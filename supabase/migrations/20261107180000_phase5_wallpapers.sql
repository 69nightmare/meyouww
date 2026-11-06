-- meyouww Phase 5: remote wallpaper delivery.
--
-- The sender uploads the finished image to the private "wallpapers" bucket under
-- their own folder, then calls create_wallpaper_command. The server checks the
-- friendship, the recipient's permissions, limits, blocks and quiet hours in one
-- transaction, decides "ask" or "auto", stores the command and pushes the
-- recipient. The recipient's phone fetches the command over authenticated HTTPS,
-- checks the image's SHA-256, applies it (or asks), and reports the real outcome.
-- Never "applied" just because a push was sent.

-- ---------------------------------------------------------------------------
-- Commands
-- ---------------------------------------------------------------------------

create table public.wallpaper_commands (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references auth.users (id) on delete cascade,
  recipient_id uuid not null references auth.users (id) on delete cascade,
  target text not null check (target in ('home', 'lock', 'both')),
  storage_path text not null unique check (char_length(storage_path) between 10 and 200),
  mime text not null check (mime in ('image/png', 'image/webp')),
  width integer not null check (width between 100 and 4000),
  height integer not null check (height between 100 and 6000),
  bytes integer not null check (bytes between 1 and 5242880),
  sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  is_doodle boolean not null default true,
  mode text not null check (mode in ('ask', 'auto')),
  -- Increases per sender; the recipient refuses anything not newer than the last one seen.
  sequence bigint not null,
  status text not null default 'sent' check (status in (
    'sent', 'scheduled', 'held', 'delivered', 'awaiting_approval', 'applying',
    'applied', 'declined', 'failed', 'expired', 'cancelled', 'replaced')),
  fail_reason text check (char_length(fail_reason) <= 40),
  apply_at timestamptz,
  receipts boolean not null default true,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  delivered_at timestamptz,
  resolved_at timestamptz,
  check (sender_id <> recipient_id),
  check (expires_at > created_at)
);
create index wallpaper_commands_recipient_idx on public.wallpaper_commands (recipient_id, created_at desc);
create index wallpaper_commands_sender_idx on public.wallpaper_commands (sender_id, created_at desc);
create index wallpaper_commands_due_idx on public.wallpaper_commands (apply_at) where status in ('scheduled', 'held');

alter table public.wallpaper_commands enable row level security;
revoke all on public.wallpaper_commands from anon, authenticated;
grant select on public.wallpaper_commands to authenticated;
create policy "wallpaper_commands: sender or recipient can read" on public.wallpaper_commands
  for select to authenticated using (sender_id = (select auth.uid()) or recipient_id = (select auth.uid()));

-- Items already shown on the recipient's phone are stored read and not pushed again.
create or replace function private.on_activity_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.read_at is null then
    perform private.dispatch_push(
      new.recipient_id,
      jsonb_build_object('type', 'activity', 'activity_id', new.id, 'kind', new.kind, 'v', '1'),
      private.activity_channel(new.kind)
    );
  end if;
  return new;
end;
$$;

-- The recipient's local time zone (most recently seen phone), never exposed.
create or replace function private.user_timezone(p_user uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((
    select d.timezone from public.devices d
     where d.user_id = p_user and d.revoked_at is null and d.timezone is not null
     order by d.last_seen_at desc limit 1), 'UTC');
$$;

-- When quiet hours (local wall-clock times) are on, the moment they end; otherwise null.
create or replace function private.quiet_until(p_start time, p_end time, p_tz text, p_now timestamptz)
returns timestamptz
language plpgsql
stable
set search_path = ''
as $$
declare
  v_local timestamp := p_now at time zone p_tz;
  v_t time := v_local::time;
  v_end timestamp;
begin
  if p_start is null or p_end is null or p_start = p_end then
    return null;
  end if;
  if p_start < p_end then
    if v_t >= p_start and v_t < p_end then
      v_end := v_local::date + p_end;
    end if;
  elsif v_t >= p_start then
    v_end := (v_local::date + 1) + p_end; -- e.g. 22:00 → 07:00 tomorrow
  elsif v_t < p_end then
    v_end := v_local::date + p_end;
  end if;
  return case when v_end is null then null else v_end at time zone p_tz end;
end;
$$;

create or replace function private.push_wallpaper(p_command public.wallpaper_commands)
returns void
language sql
security definer
set search_path = ''
as $$
  select private.dispatch_push(
    p_command.recipient_id,
    jsonb_build_object('type', 'wallpaper', 'command_id', p_command.id, 'mode', p_command.mode, 'v', '1'),
    'wallpapers_v1'
  );
$$;

-- ---------------------------------------------------------------------------
-- Sending
-- ---------------------------------------------------------------------------

create or replace function public.create_wallpaper_command(
  p_recipient uuid,
  p_target text,
  p_path text,
  p_mime text,
  p_width integer,
  p_height integer,
  p_bytes integer,
  p_sha256 text,
  p_is_doodle boolean default true,
  p_apply_local timestamp default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_capability text;
  v_perm public.relationship_permissions;
  v_tz text;
  v_apply timestamptz;
  v_quiet timestamptz;
  v_status text := 'sent';
  v_cmd public.wallpaper_commands;
begin
  if p_target not in ('home', 'lock', 'both') or p_path is null or split_part(p_path, '/', 1) <> v_uid::text
     or p_path !~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.(png|webp)$' then
    return jsonb_build_object('status', 'invalid');
  end if;
  if not private.consume_rate_limit('wallpaper_send:' || v_uid::text, 30, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;

  -- The sender only ever learns "auto", "ask" or "not accepting right now".
  v_capability := private.capability(v_uid, p_recipient);
  if v_capability = 'unavailable' then
    return jsonb_build_object('status', 'unavailable');
  end if;
  select * into v_perm from public.relationship_permissions where owner_id = p_recipient and contact_id = v_uid;
  if found then
    if (p_target in ('home', 'both') and not v_perm.wallpaper_home)
       or (p_target in ('lock', 'both') and not v_perm.wallpaper_lock)
       or (coalesce(p_is_doodle, true) and not v_perm.doodle_wallpapers) then
      return jsonb_build_object('status', 'unavailable');
    end if;
    if (select count(*) from public.wallpaper_commands c
         where c.sender_id = v_uid and c.recipient_id = p_recipient
           and c.created_at > now() - interval '24 hours' and c.status <> 'cancelled') >= v_perm.wallpaper_daily_limit then
      return jsonb_build_object('status', 'unavailable');
    end if;
  end if;

  v_tz := private.user_timezone(p_recipient);
  if p_apply_local is not null then
    v_apply := p_apply_local at time zone v_tz;
    if v_apply < now() + interval '1 minute' or v_apply > now() + interval '7 days' then
      return jsonb_build_object('status', 'invalid');
    end if;
    v_status := 'scheduled';
  end if;
  -- Quiet hours: held until they end ("hold"), or applied quietly ("silent").
  if v_perm.quiet_behavior = 'hold' then
    v_quiet := private.quiet_until(v_perm.quiet_start, v_perm.quiet_end, v_tz, coalesce(v_apply, now()));
    if v_quiet is not null then
      v_apply := v_quiet;
      v_status := 'held';
    end if;
  end if;

  insert into public.wallpaper_commands (
    sender_id, recipient_id, target, storage_path, mime, width, height, bytes, sha256, is_doodle,
    mode, sequence, status, apply_at, receipts, expires_at)
  values (
    v_uid, p_recipient, p_target, p_path, p_mime, p_width, p_height, p_bytes, lower(p_sha256), coalesce(p_is_doodle, true),
    v_capability, (extract(epoch from clock_timestamp()) * 1000000)::bigint, v_status, v_apply,
    coalesce(v_perm.receipts, true),
    case when v_apply is null then now() + interval '24 hours' else v_apply + interval '12 hours' end)
  returning * into v_cmd;

  if v_status = 'sent' then
    if v_cmd.mode = 'ask' then
      perform private.add_activity(p_recipient, v_uid, 'wallpaper_request', v_cmd.id, jsonb_build_object('target', p_target));
    else
      perform private.push_wallpaper(v_cmd);
    end if;
  end if;
  return jsonb_build_object('status', v_status, 'command_id', v_cmd.id, 'mode', v_cmd.mode, 'apply_at', v_apply);
end;
$$;

-- Sends scheduled and held commands when their time comes (run every minute by pg_cron).
create or replace function private.release_due_wallpapers()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  r public.wallpaper_commands;
  n integer := 0;
begin
  update public.wallpaper_commands set status = 'expired', resolved_at = now()
   where status in ('sent', 'scheduled', 'held', 'delivered', 'awaiting_approval') and expires_at <= now();
  for r in
    select * from public.wallpaper_commands
     where status in ('scheduled', 'held') and apply_at <= now()
     order by apply_at
     for update skip locked
  loop
    -- Permissions may have changed since it was scheduled.
    if private.capability(r.sender_id, r.recipient_id) = 'unavailable' then
      update public.wallpaper_commands set status = 'cancelled', resolved_at = now() where id = r.id;
      continue;
    end if;
    update public.wallpaper_commands set status = 'sent' where id = r.id returning * into r;
    if r.mode = 'ask' then
      perform private.add_activity(r.recipient_id, r.sender_id, 'wallpaper_request', r.id, jsonb_build_object('target', r.target));
    else
      perform private.push_wallpaper(r);
    end if;
    n := n + 1;
  end loop;
  return n;
end;
$$;

do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
    create extension if not exists pg_cron;
    perform cron.schedule('meyouww-release-wallpapers', '* * * * *', 'select private.release_due_wallpapers()');
  end if;
exception when others then
  raise warning 'pg_cron not scheduled: %', sqlerrm;
end;
$$;

create or replace function public.cancel_wallpaper_command(p_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  update public.wallpaper_commands set status = 'cancelled', resolved_at = now()
   where id = p_id and sender_id = v_uid and status in ('sent', 'scheduled', 'held', 'delivered', 'awaiting_approval');
  if not found then
    return jsonb_build_object('status', 'gone');
  end if;
  delete from public.activity where subject_id = p_id and kind = 'wallpaper_request';
  return jsonb_build_object('status', 'ok');
end;
$$;

-- What you sent, with neutral statuses.
create or replace function public.list_sent_wallpapers(p_limit integer default 30)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'id', c.id, 'recipient', private.public_card(c.recipient_id), 'target', c.target,
             'status', case when c.status in ('applied', 'declined', 'expired', 'cancelled', 'failed') then c.status
                            when c.status in ('scheduled', 'held') then 'scheduled' else 'sent' end,
             'created_at', c.created_at, 'apply_at', c.apply_at, 'resolved_at', c.resolved_at)
           order by c.sequence desc)
      from (select * from public.wallpaper_commands where sender_id = v_uid order by sequence desc
             limit least(greatest(coalesce(p_limit, 30), 1), 100)) c
  ), '[]'::jsonb);
end;
$$;

-- "What happened on my phone": every remote wallpaper, who, when, and the outcome.
create or replace function public.list_received_wallpapers(p_limit integer default 50)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'id', c.id, 'sender', private.public_card(c.sender_id), 'target', c.target, 'mode', c.mode,
             'status', c.status, 'fail_reason', c.fail_reason, 'created_at', c.created_at, 'resolved_at', c.resolved_at)
           order by c.sequence desc)
      from (select * from public.wallpaper_commands where recipient_id = v_uid order by sequence desc
             limit least(greatest(coalesce(p_limit, 50), 1), 200)) c
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- Receiving
-- ---------------------------------------------------------------------------

-- The recipient's phone fetches the command it was pushed about. Permissions are
-- checked again here: a pause or "never" since sending cancels it.
create or replace function public.get_wallpaper_command(p_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v public.wallpaper_commands;
begin
  select * into v from public.wallpaper_commands where id = p_id and recipient_id = v_uid for update;
  if not found then
    return jsonb_build_object('status', 'gone');
  end if;
  if v.status in ('applied', 'declined', 'failed', 'expired', 'cancelled', 'replaced') then
    return jsonb_build_object('status', v.status);
  end if;
  if v.expires_at <= now() then
    update public.wallpaper_commands set status = 'expired', resolved_at = now() where id = v.id;
    return jsonb_build_object('status', 'expired');
  end if;
  if private.capability(v.sender_id, v_uid) = 'unavailable' then
    update public.wallpaper_commands set status = 'cancelled', resolved_at = now() where id = v.id;
    perform private.resolve_activity(v.id, 'cancelled');
    return jsonb_build_object('status', 'cancelled');
  end if;
  if v.status = 'sent' then
    update public.wallpaper_commands set status = 'delivered', delivered_at = now() where id = v.id;
  end if;
  return jsonb_build_object(
    'status', 'ok',
    'command', jsonb_build_object(
      'id', v.id, 'sender', private.public_card(v.sender_id), 'recipient_id', v.recipient_id,
      'target', v.target, 'storage_path', v.storage_path, 'mime', v.mime, 'width', v.width, 'height', v.height,
      'bytes', v.bytes, 'sha256', v.sha256, 'mode', v.mode, 'sequence', v.sequence,
      'created_at', v.created_at, 'expires_at', v.expires_at,
      -- The recipient's own choice for this sender, so the phone can re-check locally.
      'still_auto', private.capability(v.sender_id, v_uid) = 'auto'));
end;
$$;

-- The real outcome, reported by the recipient's phone.
create or replace function public.report_wallpaper_result(p_id uuid, p_status text, p_reason text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v public.wallpaper_commands;
begin
  if p_status not in ('awaiting_approval', 'applying', 'applied', 'declined', 'failed') then
    return jsonb_build_object('status', 'invalid');
  end if;
  select * into v from public.wallpaper_commands where id = p_id and recipient_id = v_uid for update;
  if not found or v.status in ('applied', 'declined', 'failed', 'expired', 'cancelled', 'replaced') then
    return jsonb_build_object('status', 'gone');
  end if;
  update public.wallpaper_commands
     set status = p_status,
         fail_reason = case when p_status = 'failed' then left(coalesce(p_reason, 'unknown'), 40) else null end,
         delivered_at = coalesce(delivered_at, now()),
         resolved_at = case when p_status in ('applied', 'declined', 'failed') then now() else null end
   where id = v.id;

  if p_status = 'applied' then
    perform private.resolve_activity(v.id, 'applied');
    -- Kept in the recipient's inbox (already announced on the phone, so not pushed again).
    insert into public.activity (recipient_id, actor_id, kind, subject_id, data, read_at)
    values (v_uid, v.sender_id, 'wallpaper_received', v.id, jsonb_build_object('target', v.target), now());
    if v.receipts then
      perform private.add_activity(v.sender_id, v_uid, 'wallpaper_applied', v.id, jsonb_build_object('target', v.target));
    end if;
  elsif p_status = 'declined' then
    perform private.resolve_activity(v.id, 'declined');
    if v.receipts then
      perform private.add_activity(v.sender_id, v_uid, 'wallpaper_declined', v.id, jsonb_build_object('target', v.target));
    end if;
  elsif p_status = 'failed' then
    perform private.resolve_activity(v.id, 'done');
  end if;
  return jsonb_build_object('status', 'ok');
end;
$$;

-- ---------------------------------------------------------------------------
-- Storage: private bucket, sender folders, recipient read access
-- ---------------------------------------------------------------------------

create or replace function public.can_read_wallpaper_object(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.wallpaper_commands c
     where c.storage_path = p_name
       and (c.sender_id = auth.uid()
            or (c.recipient_id = auth.uid() and c.status not in ('expired', 'cancelled', 'replaced')))
  );
$$;

do $$
begin
  if to_regclass('storage.buckets') is null or to_regclass('storage.objects') is null then
    raise notice 'storage schema not present; bucket and policies skipped';
    return;
  end if;
  insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
  values ('wallpapers', 'wallpapers', false, 5242880, array['image/png', 'image/webp'])
  on conflict (id) do update set public = false, file_size_limit = 5242880, allowed_mime_types = array['image/png', 'image/webp'];

  execute $p$create policy "wallpapers: senders upload to their own folder" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'wallpapers' and (storage.foldername(name))[1] = (select auth.uid())::text)$p$;
  execute $p$create policy "wallpapers: sender and recipient can read" on storage.objects
    for select to authenticated
    using (bucket_id = 'wallpapers' and public.can_read_wallpaper_object(name))$p$;
  execute $p$create policy "wallpapers: senders can delete their uploads" on storage.objects
    for delete to authenticated
    using (bucket_id = 'wallpapers' and (storage.foldername(name))[1] = (select auth.uid())::text)$p$;
end;
$$;


-- ---------------------------------------------------------------------------
-- Inbox: wallpaper requests can be answered right there
-- ---------------------------------------------------------------------------

create or replace function private.activity_actionable(p_kind text, p_subject uuid, p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select case p_kind
    when 'friend_request' then exists (
      select 1 from public.friend_requests r
       where r.id = p_subject and r.to_user = p_user and r.status = 'pending' and r.expires_at > now())
    when 'relationship_invite' then exists (
      select 1 from public.relationship_requests r
       where r.id = p_subject and r.to_user = p_user and r.status = 'pending' and r.expires_at > now())
    when 'wallpaper_request' then exists (
      select 1 from public.wallpaper_commands c
       where c.id = p_subject and c.recipient_id = p_user
         and c.status in ('sent', 'delivered', 'awaiting_approval') and c.expires_at > now())
    else false end;
$$;

create or replace function public.list_activity(p_limit integer default 50, p_before timestamptz default null)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  return coalesce((
    select jsonb_agg(item order by (item ->> 'created_at')::timestamptz desc)
      from (
        select jsonb_build_object(
                 'id', a.id,
                 'kind', a.kind,
                 'created_at', a.created_at,
                 'read', a.read_at is not null,
                 'resolution', a.resolution,
                 'subject_id', a.subject_id,
                 'data', a.data,
                 'actor', case when a.actor_id is null then null else private.public_card(a.actor_id) end,
                 'actionable', a.resolved_at is null and private.activity_actionable(a.kind, a.subject_id, v_uid)
               ) as item
          from public.activity a
         where a.recipient_id = v_uid
           and (p_before is null or a.created_at < p_before)
           and (a.actor_id is null or not exists (
                 select 1 from public.user_blocks b where b.blocker_id = v_uid and b.blocked_id = a.actor_id))
         order by a.created_at desc
         limit least(greatest(coalesce(p_limit, 50), 1), 100)
      ) s
  ), '[]'::jsonb);
end;
$$;

create or replace function public.get_activity(p_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v public.activity;
begin
  select * into v from public.activity where id = p_id and recipient_id = v_uid;
  if not found then
    return null;
  end if;
  return jsonb_build_object(
    'id', v.id, 'kind', v.kind, 'created_at', v.created_at, 'read', v.read_at is not null,
    'resolution', v.resolution, 'subject_id', v.subject_id, 'data', v.data,
    'actor', case when v.actor_id is null then null else private.public_card(v.actor_id) end,
    'actionable', v.resolved_at is null and private.activity_actionable(v.kind, v.subject_id, v_uid)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------

revoke all on all functions in schema private from public, anon, authenticated;
revoke all on function public.create_wallpaper_command(uuid, text, text, text, integer, integer, integer, text, boolean, timestamp) from public, anon, authenticated;
revoke all on function public.cancel_wallpaper_command(uuid) from public, anon, authenticated;
revoke all on function public.list_sent_wallpapers(integer) from public, anon, authenticated;
revoke all on function public.list_received_wallpapers(integer) from public, anon, authenticated;
revoke all on function public.get_wallpaper_command(uuid) from public, anon, authenticated;
revoke all on function public.report_wallpaper_result(uuid, text, text) from public, anon, authenticated;
revoke all on function public.can_read_wallpaper_object(text) from public, anon;

grant execute on function public.create_wallpaper_command(uuid, text, text, text, integer, integer, integer, text, boolean, timestamp) to authenticated;
grant execute on function public.cancel_wallpaper_command(uuid) to authenticated;
grant execute on function public.list_sent_wallpapers(integer) to authenticated;
grant execute on function public.list_received_wallpapers(integer) to authenticated;
grant execute on function public.get_wallpaper_command(uuid) to authenticated;
grant execute on function public.report_wallpaper_result(uuid, text, text) to authenticated;
grant execute on function public.can_read_wallpaper_object(text) to authenticated;
