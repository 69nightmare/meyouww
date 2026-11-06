-- meyouww Phase 2: friend requests, friendships and relationship levels, per-contact
-- device permissions ("Who can decorate my phone?"), blocks, the remote-actions pause,
-- and the activity inbox (the heart) with push dispatch.
--
-- Same rules as Phase 1: RLS on every table, deny by default; clients read and write
-- only through SECURITY DEFINER functions that take identity from auth.uid().

-- ---------------------------------------------------------------------------
-- Private settings (push dispatch URL and token)
-- ---------------------------------------------------------------------------

create table private.settings (
  key text primary key,
  value text
);

-- The shared secret pg_net sends to the push-dispatch Edge Function. Generated here,
-- never typed by a person, readable only by the database owner and service_role.
insert into private.settings (key, value)
values ('push_dispatch_token', replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''))
on conflict (key) do nothing;

-- Set per project after deploying the function, for example:
--   update private.settings set value = 'https://<ref>.supabase.co/functions/v1/push-dispatch'
--    where key = 'push_dispatch_url';
insert into private.settings (key, value) values ('push_dispatch_url', null)
on conflict (key) do nothing;

do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_net') then
    create extension if not exists pg_net;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.user_settings (
  user_id uuid primary key references auth.users (id) on delete cascade,
  -- Emergency Pause: no remote actions from anyone while set.
  remote_paused boolean not null default false,
  remote_paused_at timestamptz,
  updated_at timestamptz not null default now()
);

create table public.user_blocks (
  blocker_id uuid not null references auth.users (id) on delete cascade,
  blocked_id uuid not null references auth.users (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index user_blocks_blocked_idx on public.user_blocks (blocked_id);

create table public.friend_requests (
  id uuid primary key default gen_random_uuid(),
  from_user uuid not null references auth.users (id) on delete cascade,
  to_user uuid not null references auth.users (id) on delete cascade,
  -- 'declined' is never shown to the sender: they see "Pending" until expires_at.
  status text not null default 'pending'
    check (status in ('pending', 'accepted', 'declined', 'cancelled')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  expires_at timestamptz not null default now() + interval '30 days',
  check (from_user <> to_user)
);
create unique index friend_requests_one_open_idx on public.friend_requests (from_user, to_user)
  where status = 'pending';
create index friend_requests_to_idx on public.friend_requests (to_user, status);

-- One row per pair, stored with user_a < user_b.
create table public.friendships (
  user_a uuid not null references auth.users (id) on delete cascade,
  user_b uuid not null references auth.users (id) on delete cascade,
  level text not null default 'friend' check (level in ('friend', 'best_friend', 'partner')),
  -- Chosen by the couple; visible only to the two of them.
  partner_label text check (char_length(partner_label) between 1 and 24),
  since timestamptz not null default now(),
  level_since timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (user_a, user_b),
  check (user_a < user_b),
  check (level = 'partner' or partner_label is null)
);
create index friendships_user_b_idx on public.friendships (user_b);

create trigger friendships_touch_updated_at
  before update on public.friendships
  for each row execute function private.touch_updated_at();

-- Invitations to become Best Friends or Partners. Both sides must accept.
create table public.relationship_requests (
  id uuid primary key default gen_random_uuid(),
  from_user uuid not null references auth.users (id) on delete cascade,
  to_user uuid not null references auth.users (id) on delete cascade,
  level text not null check (level in ('best_friend', 'partner')),
  label text check (char_length(label) between 1 and 24),
  status text not null default 'pending'
    check (status in ('pending', 'accepted', 'declined', 'cancelled')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  expires_at timestamptz not null default now() + interval '30 days',
  check (from_user <> to_user)
);
create unique index relationship_requests_one_open_idx
  on public.relationship_requests (least(from_user, to_user), greatest(from_user, to_user))
  where status = 'pending';

-- What contact_id may do on owner_id's phone. Owned and edited only by owner_id.
create table public.relationship_permissions (
  owner_id uuid not null references auth.users (id) on delete cascade,
  contact_id uuid not null references auth.users (id) on delete cascade,
  version integer not null default 1,
  wallpaper_home boolean not null default true,
  wallpaper_lock boolean not null default true,
  doodle_wallpapers boolean not null default true,
  wallpaper_mode text not null default 'ask' check (wallpaper_mode in ('ask', 'auto', 'paused', 'never')),
  paused_until timestamptz,
  wallpaper_daily_limit integer not null default 5 check (wallpaper_daily_limit between 1 and 20),
  quiet_start time,
  quiet_end time,
  quiet_behavior text not null default 'hold' check (quiet_behavior in ('silent', 'hold')),
  receipts boolean not null default true,
  reminder_suggestions boolean not null default true,
  reminder_auto_categories text[] not null default '{}',
  alarm_requests boolean not null default true,
  alarm_auto boolean not null default false,
  alarm_window_start time,
  alarm_window_end time,
  alarm_daily_max integer not null default 1 check (alarm_daily_max between 1 and 10),
  share_screen_specs boolean not null default false,
  updated_at timestamptz not null default now(),
  primary key (owner_id, contact_id),
  check (owner_id <> contact_id),
  check (cardinality(reminder_auto_categories) <= 12),
  check ((quiet_start is null) = (quiet_end is null)),
  check ((alarm_window_start is null) = (alarm_window_end is null))
);
create index relationship_permissions_contact_idx on public.relationship_permissions (contact_id);

-- The heart inbox: everything that happened to you, kept until you dismiss it.
create table public.activity (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references auth.users (id) on delete cascade,
  actor_id uuid references auth.users (id) on delete set null,
  kind text not null check (kind ~ '^[a-z_]{3,40}$'),
  subject_id uuid,
  data jsonb not null default '{}'::jsonb check (pg_column_size(data) <= 2048),
  -- clock_timestamp keeps several items from one transaction in order.
  created_at timestamptz not null default clock_timestamp(),
  read_at timestamptz,
  resolved_at timestamptz,
  resolution text check (resolution in ('accepted', 'declined', 'cancelled', 'expired', 'applied', 'done'))
);
create index activity_recipient_idx on public.activity (recipient_id, created_at desc);
create index activity_subject_idx on public.activity (subject_id) where subject_id is not null;

alter table public.user_settings enable row level security;
alter table public.user_blocks enable row level security;
alter table public.friend_requests enable row level security;
alter table public.friendships enable row level security;
alter table public.relationship_requests enable row level security;
alter table public.relationship_permissions enable row level security;
alter table public.activity enable row level security;

revoke all on public.user_settings, public.user_blocks, public.friend_requests, public.friendships,
  public.relationship_requests, public.relationship_permissions, public.activity
  from anon, authenticated;

-- Direct reads are limited to your own rows; everything that joins other people's
-- names goes through the functions below.
grant select on public.user_settings, public.relationship_permissions, public.activity to authenticated;

create policy "user_settings: owner can read" on public.user_settings
  for select to authenticated using (user_id = (select auth.uid()));
create policy "relationship_permissions: owner can read" on public.relationship_permissions
  for select to authenticated using (owner_id = (select auth.uid()));
create policy "activity: recipient can read" on public.activity
  for select to authenticated using (recipient_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Helpers (private)
-- ---------------------------------------------------------------------------

create or replace function private.require_uid()
returns uuid
language plpgsql
stable
set search_path = ''
as $$
declare
  v uuid := auth.uid();
begin
  if v is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  return v;
end;
$$;

create or replace function private.level_rank(p_level text)
returns integer
language sql
immutable
set search_path = ''
as $$
  select case p_level when 'friend' then 1 when 'best_friend' then 2 when 'partner' then 3 else 0 end;
$$;

create or replace function private.friend_level(p_a uuid, p_b uuid)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select f.level from public.friendships f
   where f.user_a = least(p_a, p_b) and f.user_b = greatest(p_a, p_b);
$$;

create or replace function private.blocked_between(p_a uuid, p_b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.user_blocks b
     where (b.blocker_id = p_a and b.blocked_id = p_b) or (b.blocker_id = p_b and b.blocked_id = p_a)
  );
$$;

create or replace function private.profile_ready(p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
     where p.id = p_user and p.is_adult and p.username is not null and p.display_name is not null
  );
$$;

-- What other people may see of someone: ID and display name only.
create or replace function private.public_card(p_user uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object('id', p.id, 'username', p.username, 'display_name', p.display_name)
    from public.profiles p where p.id = p_user;
$$;

-- Sends a data-only push to every active device of p_user via the push-dispatch Edge
-- Function. Never fails the calling transaction.
create or replace function private.dispatch_push(p_user uuid, p_data jsonb, p_channel text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_url text;
  v_token text;
begin
  select value into v_url from private.settings where key = 'push_dispatch_url';
  select value into v_token from private.settings where key = 'push_dispatch_token';
  if v_url is null or v_token is null or to_regnamespace('net') is null then
    return;
  end if;
  execute 'select net.http_post(url := $1, body := $2, headers := $3, timeout_milliseconds := 5000)'
    using v_url,
          jsonb_build_object('user_id', p_user, 'data', p_data, 'channel', p_channel),
          jsonb_build_object('Content-Type', 'application/json', 'x-meyouww-dispatch', v_token);
exception when others then
  raise warning 'push dispatch skipped: %', sqlerrm;
end;
$$;

create or replace function private.activity_channel(p_kind text)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_kind like 'wallpaper_request%' then 'wallpaper_requests_v1'
    when p_kind like 'wallpaper%' or p_kind like 'doodle%' then 'wallpapers_v1'
    when p_kind like 'alarm%' then 'alarms_v1'
    when p_kind like 'event%' or p_kind like 'reminder%' then 'reminders_v1'
    when p_kind like 'security%' then 'security_v1'
    else 'friends_v1'
  end;
$$;

create or replace function private.on_activity_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.dispatch_push(
    new.recipient_id,
    jsonb_build_object('type', 'activity', 'activity_id', new.id, 'kind', new.kind, 'v', '1'),
    private.activity_channel(new.kind)
  );
  return new;
end;
$$;

create trigger activity_dispatch_push
  after insert on public.activity
  for each row execute function private.on_activity_insert();

create or replace function private.add_activity(
  p_recipient uuid, p_actor uuid, p_kind text, p_subject uuid, p_data jsonb default '{}'::jsonb
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_id uuid;
begin
  insert into public.activity (recipient_id, actor_id, kind, subject_id, data)
  values (p_recipient, p_actor, p_kind, p_subject, coalesce(p_data, '{}'::jsonb))
  returning id into v_id;
  return v_id;
end;
$$;

-- Marks the inbox items about a request as resolved, so their buttons disappear.
create or replace function private.resolve_activity(p_subject uuid, p_resolution text)
returns void
language sql
security definer
set search_path = ''
as $$
  update public.activity
     set resolved_at = coalesce(resolved_at, now()), resolution = coalesce(resolution, p_resolution)
   where subject_id = p_subject and resolved_at is null;
$$;

create or replace function private.ensure_permissions(p_a uuid, p_b uuid)
returns void
language sql
security definer
set search_path = ''
as $$
  insert into public.relationship_permissions (owner_id, contact_id) values (p_a, p_b), (p_b, p_a)
  on conflict (owner_id, contact_id) do nothing;
$$;

-- Turns off everything that needs a closer relationship than p_level, in both directions.
create or replace function private.revoke_above_level(p_a uuid, p_b uuid, p_level text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if private.level_rank(p_level) < 2 then
    update public.relationship_permissions
       set wallpaper_mode = case when wallpaper_mode = 'auto' then 'ask' else wallpaper_mode end,
           alarm_auto = false,
           reminder_auto_categories = '{}',
           version = version + 1,
           updated_at = now()
     where (owner_id = p_a and contact_id = p_b) or (owner_id = p_b and contact_id = p_a);
  end if;
end;
$$;

create or replace function private.make_friends(p_a uuid, p_b uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.friendships (user_a, user_b) values (least(p_a, p_b), greatest(p_a, p_b))
  on conflict do nothing;
  perform private.ensure_permissions(p_a, p_b);
end;
$$;

-- Ends every link between two people: friendship, permissions, open requests.
create or replace function private.unlink(p_a uuid, p_b uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  r record;
begin
  delete from public.friendships where user_a = least(p_a, p_b) and user_b = greatest(p_a, p_b);
  delete from public.relationship_permissions
   where (owner_id = p_a and contact_id = p_b) or (owner_id = p_b and contact_id = p_a);
  for r in
    select id from public.friend_requests
     where status = 'pending' and ((from_user = p_a and to_user = p_b) or (from_user = p_b and to_user = p_a))
  loop
    update public.friend_requests set status = 'cancelled', responded_at = now() where id = r.id;
    perform private.resolve_activity(r.id, 'cancelled');
  end loop;
  for r in
    select id from public.relationship_requests
     where status = 'pending' and ((from_user = p_a and to_user = p_b) or (from_user = p_b and to_user = p_a))
  loop
    update public.relationship_requests set status = 'cancelled', responded_at = now() where id = r.id;
    perform private.resolve_activity(r.id, 'cancelled');
  end loop;
end;
$$;

-- Neutral answer to "what happens if I send this person a wallpaper?". Never says why.
create or replace function private.capability(p_sender uuid, p_recipient uuid)
returns text
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_level text := private.friend_level(p_sender, p_recipient);
  v_perm public.relationship_permissions;
begin
  if v_level is null or private.blocked_between(p_sender, p_recipient) then
    return 'unavailable';
  end if;
  if exists (select 1 from public.user_settings s where s.user_id = p_recipient and s.remote_paused) then
    return 'unavailable';
  end if;
  select * into v_perm from public.relationship_permissions
   where owner_id = p_recipient and contact_id = p_sender;
  if not found then
    return 'ask';
  end if;
  if v_perm.wallpaper_mode = 'never'
     or (v_perm.wallpaper_mode = 'paused' and (v_perm.paused_until is null or v_perm.paused_until > now()))
     or not (v_perm.wallpaper_home or v_perm.wallpaper_lock) then
    return 'unavailable';
  end if;
  if v_perm.wallpaper_mode = 'auto' and private.level_rank(v_level) >= 2 then
    return 'auto';
  end if;
  return 'ask';
end;
$$;

create or replace function private.permissions_json(p public.relationship_permissions)
returns jsonb
language sql
immutable
set search_path = ''
as $$
  select jsonb_build_object(
    'contact_id', p.contact_id,
    'version', p.version,
    'wallpaper_home', p.wallpaper_home,
    'wallpaper_lock', p.wallpaper_lock,
    'doodle_wallpapers', p.doodle_wallpapers,
    'wallpaper_mode', p.wallpaper_mode,
    'paused_until', p.paused_until,
    'wallpaper_daily_limit', p.wallpaper_daily_limit,
    'quiet_start', to_char(p.quiet_start, 'HH24:MI'),
    'quiet_end', to_char(p.quiet_end, 'HH24:MI'),
    'quiet_behavior', p.quiet_behavior,
    'receipts', p.receipts,
    'reminder_suggestions', p.reminder_suggestions,
    'reminder_auto_categories', to_jsonb(p.reminder_auto_categories),
    'alarm_requests', p.alarm_requests,
    'alarm_auto', p.alarm_auto,
    'alarm_window_start', to_char(p.alarm_window_start, 'HH24:MI'),
    'alarm_window_end', to_char(p.alarm_window_end, 'HH24:MI'),
    'alarm_daily_max', p.alarm_daily_max,
    'share_screen_specs', p.share_screen_specs
  );
$$;

-- ---------------------------------------------------------------------------
-- Finding people and friend requests
-- ---------------------------------------------------------------------------

-- Exact-match lookup by public ID. The reply has the same shape whether or not the ID
-- exists, and blocked people simply are not found.
create or replace function public.lookup_user_id(p_username text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_name text := regexp_replace(lower(trim(coalesce(p_username, ''))), '^@', '');
  v_target uuid;
  v_relation text;
begin
  if not private.consume_rate_limit('lookup:' || v_uid::text, 30, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited', 'user', null);
  end if;
  if v_name ~ '^[a-z][a-z0-9._]{2,19}$' then
    select p.id into v_target from public.profiles p
     where p.username = v_name and p.is_adult and p.display_name is not null;
  end if;
  if v_target is null or private.blocked_between(v_uid, v_target) then
    return jsonb_build_object('status', 'ok', 'user', null);
  end if;

  v_relation := case
    when v_target = v_uid then 'self'
    when private.friend_level(v_uid, v_target) is not null then 'friend'
    when exists (select 1 from public.friend_requests r where r.from_user = v_uid and r.to_user = v_target
                   and r.status in ('pending', 'declined') and r.expires_at > now()) then 'requested'
    when exists (select 1 from public.friend_requests r where r.from_user = v_target and r.to_user = v_uid
                   and r.status = 'pending' and r.expires_at > now()) then 'incoming'
    else 'none'
  end;
  return jsonb_build_object('status', 'ok', 'user', private.public_card(v_target) || jsonb_build_object('relation', v_relation));
end;
$$;

create or replace function public.send_friend_request(p_username text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_name text := regexp_replace(lower(trim(coalesce(p_username, ''))), '^@', '');
  v_target uuid;
  v_incoming uuid;
  v_request uuid;
begin
  if not private.profile_ready(v_uid) then
    return jsonb_build_object('status', 'invalid');
  end if;
  if not private.consume_rate_limit('friend_request:' || v_uid::text, 30, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  select p.id into v_target from public.profiles p
   where p.username = v_name and p.is_adult and p.display_name is not null;
  if v_target is null or private.blocked_between(v_uid, v_target) then
    return jsonb_build_object('status', 'not_found');
  end if;
  if v_target = v_uid then
    return jsonb_build_object('status', 'invalid');
  end if;
  if private.friend_level(v_uid, v_target) is not null then
    return jsonb_build_object('status', 'already_friends');
  end if;

  -- They already asked us: asking back means yes.
  select id into v_incoming from public.friend_requests
   where from_user = v_target and to_user = v_uid and status = 'pending' and expires_at > now()
   for update;
  if v_incoming is not null then
    update public.friend_requests set status = 'accepted', responded_at = now() where id = v_incoming;
    perform private.make_friends(v_uid, v_target);
    perform private.resolve_activity(v_incoming, 'accepted');
    perform private.add_activity(v_target, v_uid, 'friend_accepted', v_incoming);
    return jsonb_build_object('status', 'accepted');
  end if;

  -- Still open (or quietly declined and not yet expired): nothing new to send.
  if exists (select 1 from public.friend_requests
              where from_user = v_uid and to_user = v_target
                and status in ('pending', 'declined') and expires_at > now()) then
    return jsonb_build_object('status', 'pending');
  end if;
  -- An expired request is closed before a fresh one is opened.
  update public.friend_requests set status = 'cancelled', responded_at = now()
   where from_user = v_uid and to_user = v_target and status = 'pending';

  if (select count(*) from public.friend_requests
       where from_user = v_uid and status = 'pending' and expires_at > now()) >= 100 then
    return jsonb_build_object('status', 'rate_limited');
  end if;

  insert into public.friend_requests (from_user, to_user) values (v_uid, v_target) returning id into v_request;
  perform private.add_activity(v_target, v_uid, 'friend_request', v_request);
  return jsonb_build_object('status', 'pending', 'request_id', v_request);
end;
$$;

create or replace function public.respond_friend_request(p_request_id uuid, p_accept boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_req public.friend_requests;
begin
  select * into v_req from public.friend_requests where id = p_request_id and to_user = v_uid for update;
  if not found or v_req.status <> 'pending' then
    return jsonb_build_object('status', 'gone');
  end if;
  if v_req.expires_at <= now() then
    perform private.resolve_activity(v_req.id, 'expired');
    return jsonb_build_object('status', 'gone');
  end if;
  if coalesce(p_accept, false) then
    if private.blocked_between(v_uid, v_req.from_user) then
      return jsonb_build_object('status', 'gone');
    end if;
    update public.friend_requests set status = 'accepted', responded_at = now() where id = v_req.id;
    perform private.make_friends(v_uid, v_req.from_user);
    perform private.resolve_activity(v_req.id, 'accepted');
    perform private.add_activity(v_req.from_user, v_uid, 'friend_accepted', v_req.id);
    return jsonb_build_object('status', 'accepted');
  end if;
  update public.friend_requests set status = 'declined', responded_at = now() where id = v_req.id;
  perform private.resolve_activity(v_req.id, 'declined');
  return jsonb_build_object('status', 'declined');
end;
$$;

create or replace function public.cancel_friend_request(p_request_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  update public.friend_requests set status = 'cancelled', responded_at = now()
   where id = p_request_id and from_user = v_uid and status in ('pending', 'declined');
  if not found then
    return jsonb_build_object('status', 'gone');
  end if;
  -- The other person never sees a request that was taken back.
  delete from public.activity where subject_id = p_request_id and kind = 'friend_request';
  return jsonb_build_object('status', 'ok');
end;
$$;

-- Incoming requests to answer, and outgoing ones (always shown as "pending").
create or replace function public.list_friend_requests()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  return jsonb_build_object(
    'incoming', coalesce((
      select jsonb_agg(jsonb_build_object('id', r.id, 'created_at', r.created_at, 'user', private.public_card(r.from_user))
                       order by r.created_at desc)
        from public.friend_requests r
       where r.to_user = v_uid and r.status = 'pending' and r.expires_at > now()
         and not private.blocked_between(v_uid, r.from_user)
    ), '[]'::jsonb),
    'outgoing', coalesce((
      select jsonb_agg(jsonb_build_object('id', r.id, 'created_at', r.created_at, 'user', private.public_card(r.to_user))
                       order by r.created_at desc)
        from public.friend_requests r
       where r.from_user = v_uid and r.status in ('pending', 'declined') and r.expires_at > now()
         and not private.blocked_between(v_uid, r.to_user)
    ), '[]'::jsonb)
  );
end;
$$;

-- ---------------------------------------------------------------------------
-- Friends and relationship levels
-- ---------------------------------------------------------------------------

create or replace function public.list_friends()
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
    select jsonb_agg(item order by item ->> 'display_name')
      from (
        select private.public_card(o.other) || jsonb_build_object(
                 'level', f.level,
                 'label', f.partner_label,
                 'since', f.since,
                 'my_mode', coalesce(mine.wallpaper_mode, 'ask'),
                 'they_allow', private.capability(v_uid, o.other),
                 'invite', (
                   select jsonb_build_object(
                            'id', rr.id,
                            'direction', case when rr.from_user = v_uid then 'out' else 'in' end,
                            'level', rr.level, 'label', rr.label)
                     from public.relationship_requests rr
                    where rr.status = 'pending' and rr.expires_at > now()
                      and least(rr.from_user, rr.to_user) = f.user_a and greatest(rr.from_user, rr.to_user) = f.user_b
                    limit 1)
               ) as item
          from public.friendships f
          cross join lateral (select case when f.user_a = v_uid then f.user_b else f.user_a end as other) o
          left join public.relationship_permissions mine on mine.owner_id = v_uid and mine.contact_id = o.other
         where f.user_a = v_uid or f.user_b = v_uid
      ) s
  ), '[]'::jsonb);
end;
$$;

create or replace function public.remove_friend(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  if p_user is null or p_user = v_uid then
    return jsonb_build_object('status', 'invalid');
  end if;
  perform private.unlink(v_uid, p_user);
  return jsonb_build_object('status', 'ok');
end;
$$;

create or replace function public.send_relationship_invite(p_user uuid, p_level text, p_label text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_current text := private.friend_level(v_uid, p_user);
  v_label text := nullif(trim(coalesce(p_label, '')), '');
  v_open public.relationship_requests;
  v_id uuid;
begin
  if v_current is null or private.blocked_between(v_uid, p_user) then
    return jsonb_build_object('status', 'not_friends');
  end if;
  if p_level not in ('best_friend', 'partner') or private.level_rank(p_level) <= private.level_rank(v_current) then
    return jsonb_build_object('status', 'invalid');
  end if;
  if p_level = 'partner' then
    v_label := coalesce(v_label, 'Partner');
    if char_length(v_label) > 24 then
      return jsonb_build_object('status', 'invalid');
    end if;
  else
    v_label := null;
  end if;
  if not private.consume_rate_limit('relationship_invite:' || v_uid::text, 10, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;

  select * into v_open from public.relationship_requests
   where status = 'pending' and expires_at > now()
     and least(from_user, to_user) = least(v_uid, p_user) and greatest(from_user, to_user) = greatest(v_uid, p_user)
   for update;
  if found then
    if v_open.to_user = v_uid and v_open.level = p_level then
      -- They asked for the same thing: that's a yes from both.
      return public.respond_relationship_invite(v_open.id, true);
    end if;
    if v_open.from_user = v_uid then
      return jsonb_build_object('status', 'pending', 'request_id', v_open.id);
    end if;
    return jsonb_build_object('status', 'answer_theirs_first', 'request_id', v_open.id);
  end if;
  -- Close anything expired so the unique index allows a new invite.
  update public.relationship_requests set status = 'cancelled', responded_at = now()
   where status = 'pending'
     and least(from_user, to_user) = least(v_uid, p_user) and greatest(from_user, to_user) = greatest(v_uid, p_user);

  insert into public.relationship_requests (from_user, to_user, level, label)
  values (v_uid, p_user, p_level, v_label) returning id into v_id;
  perform private.add_activity(p_user, v_uid, 'relationship_invite', v_id,
    jsonb_build_object('level', p_level, 'label', v_label));
  return jsonb_build_object('status', 'pending', 'request_id', v_id);
end;
$$;

-- Accepting is one transaction: the level changes for both people at once.
create or replace function public.respond_relationship_invite(p_request_id uuid, p_accept boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_req public.relationship_requests;
begin
  select * into v_req from public.relationship_requests where id = p_request_id and to_user = v_uid for update;
  if not found or v_req.status <> 'pending' then
    return jsonb_build_object('status', 'gone');
  end if;
  if v_req.expires_at <= now() or private.friend_level(v_uid, v_req.from_user) is null then
    perform private.resolve_activity(v_req.id, 'expired');
    return jsonb_build_object('status', 'gone');
  end if;
  if coalesce(p_accept, false) then
    update public.relationship_requests set status = 'accepted', responded_at = now() where id = v_req.id;
    update public.friendships
       set level = v_req.level,
           partner_label = case when v_req.level = 'partner' then v_req.label else null end,
           level_since = now()
     where user_a = least(v_uid, v_req.from_user) and user_b = greatest(v_uid, v_req.from_user);
    perform private.resolve_activity(v_req.id, 'accepted');
    perform private.add_activity(v_req.from_user, v_uid, 'relationship_accepted', v_req.id,
      jsonb_build_object('level', v_req.level, 'label', v_req.label));
    return jsonb_build_object('status', 'accepted');
  end if;
  update public.relationship_requests set status = 'declined', responded_at = now() where id = v_req.id;
  perform private.resolve_activity(v_req.id, 'declined');
  return jsonb_build_object('status', 'declined');
end;
$$;

create or replace function public.cancel_relationship_invite(p_request_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  update public.relationship_requests set status = 'cancelled', responded_at = now()
   where id = p_request_id and from_user = v_uid and status = 'pending';
  if not found then
    return jsonb_build_object('status', 'gone');
  end if;
  delete from public.activity where subject_id = p_request_id and kind = 'relationship_invite';
  return jsonb_build_object('status', 'ok');
end;
$$;

-- Either person can step a relationship down at any time, with no approval. Anything
-- that needed the closer level is switched off for both of them.
create or replace function public.set_relationship_level(p_user uuid, p_level text, p_label text default null)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_current text := private.friend_level(v_uid, p_user);
  v_label text := nullif(trim(coalesce(p_label, '')), '');
begin
  if v_current is null then
    return jsonb_build_object('status', 'not_friends');
  end if;
  if p_level = 'partner' and v_current = 'partner' then
    -- Renaming the label is the only same-level change.
    if v_label is null or char_length(v_label) > 24 then
      return jsonb_build_object('status', 'invalid');
    end if;
    update public.friendships set partner_label = v_label
     where user_a = least(v_uid, p_user) and user_b = greatest(v_uid, p_user);
    return jsonb_build_object('status', 'ok');
  end if;
  if p_level not in ('friend', 'best_friend') or private.level_rank(p_level) >= private.level_rank(v_current) then
    return jsonb_build_object('status', 'invalid');
  end if;
  update public.friendships set level = p_level, partner_label = null, level_since = now()
   where user_a = least(v_uid, p_user) and user_b = greatest(v_uid, p_user);
  perform private.revoke_above_level(v_uid, p_user, p_level);
  update public.relationship_requests set status = 'cancelled', responded_at = now()
   where status = 'pending'
     and least(from_user, to_user) = least(v_uid, p_user) and greatest(from_user, to_user) = greatest(v_uid, p_user);
  return jsonb_build_object('status', 'ok');
end;
$$;

-- ---------------------------------------------------------------------------
-- Blocking
-- ---------------------------------------------------------------------------

create or replace function public.block_user(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  if p_user is null or p_user = v_uid or not exists (select 1 from auth.users u where u.id = p_user) then
    return jsonb_build_object('status', 'invalid');
  end if;
  insert into public.user_blocks (blocker_id, blocked_id) values (v_uid, p_user) on conflict do nothing;
  perform private.unlink(v_uid, p_user);
  delete from public.activity where recipient_id = v_uid and actor_id = p_user;
  return jsonb_build_object('status', 'ok');
end;
$$;

create or replace function public.unblock_user(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  delete from public.user_blocks where blocker_id = v_uid and blocked_id = p_user;
  return jsonb_build_object('status', 'ok');
end;
$$;

create or replace function public.list_blocked()
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
    select jsonb_agg(private.public_card(b.blocked_id) || jsonb_build_object('blocked_at', b.created_at)
                     order by b.created_at desc)
      from public.user_blocks b where b.blocker_id = v_uid
  ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------------
-- "Who can decorate my phone?"
-- ---------------------------------------------------------------------------

create or replace function public.get_contact_permissions(p_contact uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_perm public.relationship_permissions;
  v_level text := private.friend_level(v_uid, p_contact);
begin
  if v_level is null then
    return jsonb_build_object('status', 'not_friends');
  end if;
  perform private.ensure_permissions(v_uid, p_contact);
  select * into v_perm from public.relationship_permissions where owner_id = v_uid and contact_id = p_contact;
  return jsonb_build_object('status', 'ok', 'level', v_level, 'permissions', private.permissions_json(v_perm));
end;
$$;

-- Applies the keys present in p_changes. Fails as a whole on any invalid value.
create or replace function public.update_contact_permissions(p_contact uuid, p_changes jsonb, p_expected_version integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_level text := private.friend_level(v_uid, p_contact);
  v public.relationship_permissions;
  c jsonb := coalesce(p_changes, '{}'::jsonb);
  k text;
  v_categories text[];
begin
  if v_level is null then
    return jsonb_build_object('status', 'not_friends');
  end if;
  if not private.consume_rate_limit('permissions:' || v_uid::text, 120, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  for k in select jsonb_object_keys(c) loop
    if k not in ('wallpaper_home', 'wallpaper_lock', 'doodle_wallpapers', 'wallpaper_mode', 'paused_until',
                 'wallpaper_daily_limit', 'quiet_start', 'quiet_end', 'quiet_behavior', 'receipts',
                 'reminder_suggestions', 'reminder_auto_categories', 'alarm_requests', 'alarm_auto',
                 'alarm_window_start', 'alarm_window_end', 'alarm_daily_max', 'share_screen_specs') then
      return jsonb_build_object('status', 'invalid', 'field', k);
    end if;
  end loop;

  perform private.ensure_permissions(v_uid, p_contact);
  select * into v from public.relationship_permissions
   where owner_id = v_uid and contact_id = p_contact for update;
  if p_expected_version is not null and p_expected_version <> v.version then
    return jsonb_build_object('status', 'conflict', 'permissions', private.permissions_json(v));
  end if;

  begin
    if c ? 'wallpaper_home' then v.wallpaper_home := (c ->> 'wallpaper_home')::boolean; end if;
    if c ? 'wallpaper_lock' then v.wallpaper_lock := (c ->> 'wallpaper_lock')::boolean; end if;
    if c ? 'doodle_wallpapers' then v.doodle_wallpapers := (c ->> 'doodle_wallpapers')::boolean; end if;
    if c ? 'wallpaper_mode' then v.wallpaper_mode := c ->> 'wallpaper_mode'; end if;
    if c ? 'paused_until' then v.paused_until := (c ->> 'paused_until')::timestamptz; end if;
    if c ? 'wallpaper_daily_limit' then v.wallpaper_daily_limit := (c ->> 'wallpaper_daily_limit')::integer; end if;
    if c ? 'quiet_start' then v.quiet_start := (c ->> 'quiet_start')::time; end if;
    if c ? 'quiet_end' then v.quiet_end := (c ->> 'quiet_end')::time; end if;
    if c ? 'quiet_behavior' then v.quiet_behavior := c ->> 'quiet_behavior'; end if;
    if c ? 'receipts' then v.receipts := (c ->> 'receipts')::boolean; end if;
    if c ? 'reminder_suggestions' then v.reminder_suggestions := (c ->> 'reminder_suggestions')::boolean; end if;
    if c ? 'reminder_auto_categories' then
      if jsonb_typeof(c -> 'reminder_auto_categories') <> 'array' then
        return jsonb_build_object('status', 'invalid', 'field', 'reminder_auto_categories');
      end if;
      select coalesce(array_agg(distinct x), '{}') into v_categories
        from jsonb_array_elements_text(c -> 'reminder_auto_categories') x;
      if exists (select 1 from unnest(v_categories) x where x !~ '^[a-z_]{2,24}$') then
        return jsonb_build_object('status', 'invalid', 'field', 'reminder_auto_categories');
      end if;
      v.reminder_auto_categories := v_categories;
    end if;
    if c ? 'alarm_requests' then v.alarm_requests := (c ->> 'alarm_requests')::boolean; end if;
    if c ? 'alarm_auto' then v.alarm_auto := (c ->> 'alarm_auto')::boolean; end if;
    if c ? 'alarm_window_start' then v.alarm_window_start := (c ->> 'alarm_window_start')::time; end if;
    if c ? 'alarm_window_end' then v.alarm_window_end := (c ->> 'alarm_window_end')::time; end if;
    if c ? 'alarm_daily_max' then v.alarm_daily_max := (c ->> 'alarm_daily_max')::integer; end if;
    if c ? 'share_screen_specs' then v.share_screen_specs := (c ->> 'share_screen_specs')::boolean; end if;
  exception when invalid_text_representation or invalid_datetime_format or datetime_field_overflow
            or numeric_value_out_of_range then
    return jsonb_build_object('status', 'invalid');
  end;

  if v.wallpaper_mode not in ('ask', 'auto', 'paused', 'never')
     or v.quiet_behavior not in ('silent', 'hold')
     or v.wallpaper_daily_limit not between 1 and 20
     or v.alarm_daily_max not between 1 and 10
     or (v.quiet_start is null) <> (v.quiet_end is null)
     or (v.alarm_window_start is null) <> (v.alarm_window_end is null)
     or cardinality(v.reminder_auto_categories) > 12 then
    return jsonb_build_object('status', 'invalid');
  end if;

  -- Automatic actions need a Best Friend or Partner.
  if private.level_rank(v_level) < 2
     and (v.wallpaper_mode = 'auto' or v.alarm_auto or cardinality(v.reminder_auto_categories) > 0) then
    return jsonb_build_object('status', 'needs_closer_level');
  end if;
  -- Every automatic change must be announced, so this phone has to show notifications.
  if v.wallpaper_mode = 'auto' and not exists (
       select 1 from public.devices d join public.device_state s on s.device_id = d.id
        where d.user_id = v_uid and d.revoked_at is null and s.notifications_enabled) then
    return jsonb_build_object('status', 'needs_notifications');
  end if;
  if v.wallpaper_mode <> 'paused' then
    v.paused_until := null;
  end if;

  update public.relationship_permissions set
    wallpaper_home = v.wallpaper_home,
    wallpaper_lock = v.wallpaper_lock,
    doodle_wallpapers = v.doodle_wallpapers,
    wallpaper_mode = v.wallpaper_mode,
    paused_until = v.paused_until,
    wallpaper_daily_limit = v.wallpaper_daily_limit,
    quiet_start = v.quiet_start,
    quiet_end = v.quiet_end,
    quiet_behavior = v.quiet_behavior,
    receipts = v.receipts,
    reminder_suggestions = v.reminder_suggestions,
    reminder_auto_categories = v.reminder_auto_categories,
    alarm_requests = v.alarm_requests,
    alarm_auto = v.alarm_auto,
    alarm_window_start = v.alarm_window_start,
    alarm_window_end = v.alarm_window_end,
    alarm_daily_max = v.alarm_daily_max,
    share_screen_specs = v.share_screen_specs,
    version = version + 1,
    updated_at = now()
  where owner_id = v_uid and contact_id = p_contact
  returning * into v;
  return jsonb_build_object('status', 'ok', 'permissions', private.permissions_json(v));
end;
$$;

-- Turns off every automatic and remote permission for every contact at once.
create or replace function public.revoke_all_permissions()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  update public.relationship_permissions
     set wallpaper_mode = 'never', paused_until = null, alarm_auto = false, alarm_requests = false,
         reminder_auto_categories = '{}', share_screen_specs = false,
         version = version + 1, updated_at = now()
   where owner_id = v_uid;
  return jsonb_build_object('status', 'ok');
end;
$$;

-- Emergency Pause: stops remote actions from everyone until turned off.
create or replace function public.set_remote_pause(p_paused boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  insert into public.user_settings (user_id, remote_paused, remote_paused_at, updated_at)
  values (v_uid, coalesce(p_paused, false), case when p_paused then now() end, now())
  on conflict (user_id) do update set
    remote_paused = excluded.remote_paused,
    remote_paused_at = excluded.remote_paused_at,
    updated_at = now();
  return jsonb_build_object('status', 'ok', 'remote_paused', coalesce(p_paused, false));
end;
$$;

-- ---------------------------------------------------------------------------
-- The heart inbox
-- ---------------------------------------------------------------------------

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
                 'actionable', a.resolved_at is null and case a.kind
                    when 'friend_request' then exists (
                      select 1 from public.friend_requests r
                       where r.id = a.subject_id and r.to_user = v_uid and r.status = 'pending' and r.expires_at > now())
                    when 'relationship_invite' then exists (
                      select 1 from public.relationship_requests r
                       where r.id = a.subject_id and r.to_user = v_uid and r.status = 'pending' and r.expires_at > now())
                    else false end
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
    'actionable', v.resolved_at is null and v.kind in ('friend_request', 'relationship_invite')
  );
end;
$$;

create or replace function public.unread_activity_count()
returns integer
language sql
stable
security definer
set search_path = ''
as $$
  select count(*)::integer from public.activity a
   where a.recipient_id = auth.uid() and a.read_at is null
     and (a.actor_id is null or not exists (
       select 1 from public.user_blocks b where b.blocker_id = auth.uid() and b.blocked_id = a.actor_id));
$$;

-- Marks the given items read, or everything when p_ids is null.
create or replace function public.mark_activity_read(p_ids uuid[] default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  update public.activity set read_at = now()
   where recipient_id = v_uid and read_at is null and (p_ids is null or id = any(p_ids));
end;
$$;

create or replace function public.dismiss_activity(p_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  delete from public.activity where id = p_id and recipient_id = v_uid;
end;
$$;

-- ---------------------------------------------------------------------------
-- For the push-dispatch Edge Function (service_role only)
-- ---------------------------------------------------------------------------

create or replace function public.check_dispatch_token(p_token text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(p_token is not null and p_token = (select value from private.settings where key = 'push_dispatch_token'), false);
$$;

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------

revoke all on all functions in schema private from public, anon, authenticated;

revoke all on function public.lookup_user_id(text) from public, anon, authenticated;
revoke all on function public.send_friend_request(text) from public, anon, authenticated;
revoke all on function public.respond_friend_request(uuid, boolean) from public, anon, authenticated;
revoke all on function public.cancel_friend_request(uuid) from public, anon, authenticated;
revoke all on function public.list_friend_requests() from public, anon, authenticated;
revoke all on function public.list_friends() from public, anon, authenticated;
revoke all on function public.remove_friend(uuid) from public, anon, authenticated;
revoke all on function public.send_relationship_invite(uuid, text, text) from public, anon, authenticated;
revoke all on function public.respond_relationship_invite(uuid, boolean) from public, anon, authenticated;
revoke all on function public.cancel_relationship_invite(uuid) from public, anon, authenticated;
revoke all on function public.set_relationship_level(uuid, text, text) from public, anon, authenticated;
revoke all on function public.block_user(uuid) from public, anon, authenticated;
revoke all on function public.unblock_user(uuid) from public, anon, authenticated;
revoke all on function public.list_blocked() from public, anon, authenticated;
revoke all on function public.get_contact_permissions(uuid) from public, anon, authenticated;
revoke all on function public.update_contact_permissions(uuid, jsonb, integer) from public, anon, authenticated;
revoke all on function public.revoke_all_permissions() from public, anon, authenticated;
revoke all on function public.set_remote_pause(boolean) from public, anon, authenticated;
revoke all on function public.list_activity(integer, timestamptz) from public, anon, authenticated;
revoke all on function public.get_activity(uuid) from public, anon, authenticated;
revoke all on function public.unread_activity_count() from public, anon, authenticated;
revoke all on function public.mark_activity_read(uuid[]) from public, anon, authenticated;
revoke all on function public.dismiss_activity(uuid) from public, anon, authenticated;
revoke all on function public.check_dispatch_token(text) from public, anon, authenticated;

grant execute on function public.lookup_user_id(text) to authenticated;
grant execute on function public.send_friend_request(text) to authenticated;
grant execute on function public.respond_friend_request(uuid, boolean) to authenticated;
grant execute on function public.cancel_friend_request(uuid) to authenticated;
grant execute on function public.list_friend_requests() to authenticated;
grant execute on function public.list_friends() to authenticated;
grant execute on function public.remove_friend(uuid) to authenticated;
grant execute on function public.send_relationship_invite(uuid, text, text) to authenticated;
grant execute on function public.respond_relationship_invite(uuid, boolean) to authenticated;
grant execute on function public.cancel_relationship_invite(uuid) to authenticated;
grant execute on function public.set_relationship_level(uuid, text, text) to authenticated;
grant execute on function public.block_user(uuid) to authenticated;
grant execute on function public.unblock_user(uuid) to authenticated;
grant execute on function public.list_blocked() to authenticated;
grant execute on function public.get_contact_permissions(uuid) to authenticated;
grant execute on function public.update_contact_permissions(uuid, jsonb, integer) to authenticated;
grant execute on function public.revoke_all_permissions() to authenticated;
grant execute on function public.set_remote_pause(boolean) to authenticated;
grant execute on function public.list_activity(integer, timestamptz) to authenticated;
grant execute on function public.get_activity(uuid) to authenticated;
grant execute on function public.unread_activity_count() to authenticated;
grant execute on function public.mark_activity_read(uuid[]) to authenticated;
grant execute on function public.dismiss_activity(uuid) to authenticated;
grant execute on function public.check_dispatch_token(text) to service_role;
