-- meyouww: shared calendar and profile pictures.
--
-- Calendar: anyone can mark a day ("Riya's birthday 🎂", "Movie night 🍿") and invite
-- friends. Invitations follow the invitee's rules: their "reminder suggestions" switch
-- must be on for the inviter, and "events" in their auto-accept categories adds it
-- straight away (with a notification). Only the owner and invited people see an event.
-- Reminders are scheduled on each phone from remind_minutes.
--
-- Profile pictures: stored in a private "avatars" bucket. Only the person and their
-- friends (not blocked) can read them; only the person can write their own folder.

-- ---------------------------------------------------------------------------
-- Calendar
-- ---------------------------------------------------------------------------

create table public.events (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users (id) on delete cascade,
  title text not null check (char_length(title) between 1 and 60),
  note text check (char_length(note) <= 200),
  emoji text not null default '📅' check (char_length(emoji) between 1 and 8),
  starts_at timestamptz not null,
  all_day boolean not null default false,
  local_date date not null,
  local_time time,
  time_zone text not null check (char_length(time_zone) between 1 and 64),
  remind_minutes integer check (remind_minutes between 0 and 10080),
  cancelled_at timestamptz,
  created_at timestamptz not null default clock_timestamp(),
  updated_at timestamptz not null default now()
);
create index events_owner_idx on public.events (owner_id, starts_at);
create index events_starts_idx on public.events (starts_at);

create table public.event_participants (
  event_id uuid not null references public.events (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  status text not null default 'invited' check (status in ('invited', 'going', 'declined')),
  responded_at timestamptz,
  primary key (event_id, user_id)
);
create index event_participants_user_idx on public.event_participants (user_id, status);

alter table public.events enable row level security;
alter table public.event_participants enable row level security;
revoke all on public.events, public.event_participants from anon, authenticated;
-- No policies: everything goes through the functions below.

create or replace function private.event_json(e public.events, p_viewer uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', e.id,
    'mine', e.owner_id = p_viewer,
    'owner', private.public_card(e.owner_id),
    'title', e.title,
    'note', e.note,
    'emoji', e.emoji,
    'starts_at', e.starts_at,
    'all_day', e.all_day,
    'local_date', e.local_date,
    'local_time', to_char(e.local_time, 'HH24:MI'),
    'time_zone', e.time_zone,
    'remind_minutes', e.remind_minutes,
    'cancelled', e.cancelled_at is not null,
    'my_status', case when e.owner_id = p_viewer then 'going'
                      else (select p.status from public.event_participants p where p.event_id = e.id and p.user_id = p_viewer) end,
    'participants', coalesce((
      select jsonb_agg(jsonb_build_object('person', private.public_card(p.user_id), 'status', p.status) order by p.user_id)
        from public.event_participants p
       where p.event_id = e.id and p.status <> 'declined'
         and not private.blocked_between(p_viewer, p.user_id)), '[]'::jsonb));
$$;

-- Marks a day. p_start_local is a wall-clock time in the owner's zone; for all-day
-- events only its date is used.
create or replace function public.create_event(
  p_title text,
  p_start_local timestamp,
  p_all_day boolean default false,
  p_note text default null,
  p_emoji text default '📅',
  p_remind_minutes integer default null,
  p_invitees uuid[] default '{}'
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_tz text := private.user_timezone(v_uid);
  v_event public.events;
  v_invitee uuid;
  v_perm public.relationship_permissions;
  v_status text;
  v_skipped integer := 0;
  v_invited integer := 0;
begin
  if nullif(trim(coalesce(p_title, '')), '') is null or char_length(trim(p_title)) > 60
     or char_length(coalesce(p_note, '')) > 200 or p_start_local is null
     or char_length(coalesce(nullif(p_emoji, ''), '📅')) > 8
     or (p_remind_minutes is not null and p_remind_minutes not between 0 and 10080)
     or cardinality(coalesce(p_invitees, '{}')) > 20 then
    return jsonb_build_object('status', 'invalid');
  end if;
  if (p_start_local at time zone v_tz) < now() - interval '366 days'
     or (p_start_local at time zone v_tz) > now() + interval '5 years' then
    return jsonb_build_object('status', 'invalid');
  end if;
  if not private.consume_rate_limit('event_create:' || v_uid::text, 60, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;

  insert into public.events (owner_id, title, note, emoji, starts_at, all_day, local_date, local_time, time_zone, remind_minutes)
  values (
    v_uid, trim(p_title), nullif(trim(coalesce(p_note, '')), ''), coalesce(nullif(p_emoji, ''), '📅'),
    case when coalesce(p_all_day, false) then p_start_local::date::timestamp at time zone v_tz
         else p_start_local at time zone v_tz end,
    coalesce(p_all_day, false), p_start_local::date,
    case when coalesce(p_all_day, false) then null else p_start_local::time end,
    v_tz, p_remind_minutes)
  returning * into v_event;

  foreach v_invitee in array (select coalesce(array_agg(distinct x), '{}') from unnest(coalesce(p_invitees, '{}')) x where x <> v_uid)
  loop
    select * into v_perm from public.relationship_permissions where owner_id = v_invitee and contact_id = v_uid;
    if private.friend_level(v_uid, v_invitee) is null or private.blocked_between(v_uid, v_invitee)
       or (found and not v_perm.reminder_suggestions) then
      v_skipped := v_skipped + 1;
      continue;
    end if;
    v_status := case when found and 'events' = any (v_perm.reminder_auto_categories) then 'going' else 'invited' end;
    insert into public.event_participants (event_id, user_id, status, responded_at)
    values (v_event.id, v_invitee, v_status, case when v_status = 'going' then now() end);
    perform private.add_activity(v_invitee, v_uid,
      case when v_status = 'going' then 'event_added' else 'event_invite' end, v_event.id,
      jsonb_build_object('title', v_event.title, 'emoji', v_event.emoji, 'date', v_event.local_date,
                         'time', to_char(v_event.local_time, 'HH24:MI')));
    v_invited := v_invited + 1;
  end loop;

  return jsonb_build_object('status', 'ok', 'event_id', v_event.id, 'invited', v_invited, 'skipped', v_skipped);
end;
$$;

-- An invitee says yes or no (no also means "leave" later on).
create or replace function public.respond_event(p_event uuid, p_going boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_event public.events;
  v_old text;
  v_new text := case when p_going then 'going' else 'declined' end;
begin
  select e.* into v_event from public.events e where e.id = p_event and e.cancelled_at is null;
  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;
  select status into v_old from public.event_participants where event_id = p_event and user_id = v_uid for update;
  if v_old is null then
    return jsonb_build_object('status', 'not_found');
  end if;
  -- Each change notifies the owner, so flipping back and forth is limited.
  if not private.consume_rate_limit('event_respond:' || v_uid::text, 60, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  update public.event_participants
     set status = v_new, responded_at = now()
   where event_id = p_event and user_id = v_uid;
  perform private.resolve_activity(p_event, case when p_going then 'accepted' else 'declined' end);
  if v_old <> v_new then
    perform private.add_activity(v_event.owner_id, v_uid,
      case when p_going then 'event_accepted' else 'event_declined' end, p_event,
      jsonb_build_object('title', v_event.title, 'emoji', v_event.emoji, 'date', v_event.local_date));
  end if;
  return jsonb_build_object('status', 'ok');
end;
$$;

-- The owner cancels; everyone who was going or invited hears about it.
create or replace function public.cancel_event(p_event uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_event public.events;
  r record;
begin
  update public.events set cancelled_at = now(), updated_at = now()
   where id = p_event and owner_id = v_uid and cancelled_at is null
  returning * into v_event;
  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;
  perform private.resolve_activity(p_event, 'cancelled');
  for r in select user_id from public.event_participants where event_id = p_event and status <> 'declined' loop
    perform private.add_activity(r.user_id, v_uid, 'event_cancelled', p_event,
      jsonb_build_object('title', v_event.title, 'emoji', v_event.emoji, 'date', v_event.local_date));
  end loop;
  return jsonb_build_object('status', 'ok');
end;
$$;

-- My calendar between two dates: my events and those I'm invited to or going to.
create or replace function public.list_events(p_from date, p_to date)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  if p_from is null or p_to is null or p_to < p_from or p_to - p_from > 400 then
    return '[]'::jsonb;
  end if;
  return coalesce((
    select jsonb_agg(private.event_json(e, v_uid) order by e.starts_at)
      from public.events e
     where e.cancelled_at is null
       -- a day of slack either side: events are stored in their owner's zone
       and e.starts_at >= (p_from - 1)::timestamp at time zone 'UTC'
       and e.starts_at < (p_to + 2)::timestamp at time zone 'UTC'
       and (e.owner_id = v_uid or exists (
             select 1 from public.event_participants p
              where p.event_id = e.id and p.user_id = v_uid and p.status in ('invited', 'going')))
       and not private.blocked_between(v_uid, e.owner_id)
  ), '[]'::jsonb);
end;
$$;

-- Invitations and "added to your calendar" items are answerable from the inbox.
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
    when 'alarm_request' then exists (
      select 1 from public.alarms a
       where a.id = p_subject and a.recipient_id = p_user and a.status = 'pending')
    when 'alarm_added' then exists (
      select 1 from public.alarms a
       where a.id = p_subject and a.recipient_id = p_user and a.status = 'accepted')
    when 'event_invite' then exists (
      select 1 from public.event_participants p join public.events e on e.id = p.event_id
       where p.event_id = p_subject and p.user_id = p_user and p.status = 'invited' and e.cancelled_at is null)
    when 'event_added' then exists (
      select 1 from public.event_participants p join public.events e on e.id = p.event_id
       where p.event_id = p_subject and p.user_id = p_user and p.status = 'going' and e.cancelled_at is null)
    else false end;
$$;

-- ---------------------------------------------------------------------------
-- Profile pictures
-- ---------------------------------------------------------------------------

alter table public.profiles add column if not exists avatar_path text
  check (avatar_path is null or avatar_path ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.(jpg|webp)$');
alter table public.profiles add column if not exists avatar_updated_at timestamptz;

-- Cards now carry the picture's storage path. Reading the picture itself is still
-- limited to the person and their friends by the storage policy below.
create or replace function private.public_card(p_user uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object('id', p.id, 'username', p.username, 'display_name', p.display_name, 'avatar', p.avatar_path)
    from public.profiles p where p.id = p_user;
$$;

create or replace function public.can_read_avatar(p_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_owner uuid;
begin
  if v_uid is null or p_name !~ '^[0-9a-f-]{36}/' then
    return false;
  end if;
  v_owner := split_part(p_name, '/', 1)::uuid;
  return v_owner = v_uid
      or (private.friend_level(v_uid, v_owner) is not null and not private.blocked_between(v_uid, v_owner));
exception when invalid_text_representation then
  return false;
end;
$$;

-- After uploading to avatars/<my id>/<random>.jpg, point my profile at it (null removes it).
create or replace function public.set_avatar(p_path text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_exists boolean;
begin
  if p_path is not null and (p_path !~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.(jpg|webp)$' or split_part(p_path, '/', 1) <> v_uid::text) then
    return jsonb_build_object('status', 'invalid');
  end if;
  -- The picture must really be uploaded (dynamic: the storage schema only exists on Supabase).
  if p_path is not null and to_regclass('storage.objects') is not null then
    execute 'select exists (select 1 from storage.objects o where o.bucket_id = $1 and o.name = $2)'
      into v_exists using 'avatars', p_path;
    if not v_exists then
      return jsonb_build_object('status', 'not_found');
    end if;
  end if;
  if not private.consume_rate_limit('avatar:' || v_uid::text, 20, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  update public.profiles set avatar_path = p_path, avatar_updated_at = now(), updated_at = now() where id = v_uid;
  return jsonb_build_object('status', 'ok', 'avatar', p_path);
end;
$$;

do $$
begin
  if to_regclass('storage.buckets') is null or to_regclass('storage.objects') is null then
    raise notice 'storage schema not present; avatars bucket and policies skipped';
    return;
  end if;
  insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
  values ('avatars', 'avatars', false, 524288, array['image/jpeg', 'image/webp'])
  on conflict (id) do update set public = false, file_size_limit = 524288, allowed_mime_types = array['image/jpeg', 'image/webp'];

  execute $p$create policy "avatars: people upload to their own folder" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text)$p$;
  execute $p$create policy "avatars: the person and their friends can read" on storage.objects
    for select to authenticated
    using (bucket_id = 'avatars' and public.can_read_avatar(name))$p$;
  execute $p$create policy "avatars: people delete their own" on storage.objects
    for delete to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text)$p$;
end;
$$;

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------

revoke all on all functions in schema private from public, anon, authenticated;

revoke all on function public.create_event(text, timestamp, boolean, text, text, integer, uuid[]) from public, anon, authenticated;
revoke all on function public.respond_event(uuid, boolean) from public, anon, authenticated;
revoke all on function public.cancel_event(uuid) from public, anon, authenticated;
revoke all on function public.list_events(date, date) from public, anon, authenticated;
revoke all on function public.set_avatar(text) from public, anon, authenticated;
revoke all on function public.can_read_avatar(text) from public, anon;
grant execute on function public.create_event(text, timestamp, boolean, text, text, integer, uuid[]) to authenticated;
grant execute on function public.respond_event(uuid, boolean) to authenticated;
grant execute on function public.cancel_event(uuid) to authenticated;
grant execute on function public.list_events(date, date) to authenticated;
grant execute on function public.set_avatar(text) to authenticated;
grant execute on function public.can_read_avatar(text) to authenticated;
