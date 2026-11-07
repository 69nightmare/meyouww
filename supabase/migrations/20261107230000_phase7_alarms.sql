-- meyouww Phase 7: shared alarms.
--
-- A person can set an alarm for themselves or ask a friend's phone to ring. The
-- recipient's consent rules decide: alarm requests off -> refused; auto-accept on
-- (best friend or partner, inside their allowed hours, under their daily maximum) ->
-- added straight away with a notification and Undo; otherwise -> a request in the
-- heart inbox. Accepted alarms are scheduled on the recipient's phone with
-- AlarmManager, so they ring offline and with the app closed.
--
-- Time model: the sender picks a wall-clock time in the RECIPIENT's zone. The server
-- stores the first ring as an instant (UTC) plus the local time and zone; repeating
-- alarms (repeat_days bit 0 = Monday ... bit 6 = Sunday) are expanded on the phone in
-- its own zone, so they follow daylight-saving changes and travel.

create table public.alarms (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references auth.users (id) on delete cascade,
  recipient_id uuid not null references auth.users (id) on delete cascade,
  title text not null check (char_length(title) between 1 and 60),
  message text check (char_length(message) <= 200),
  ring_at timestamptz not null,
  local_time time not null,
  time_zone text not null check (char_length(time_zone) between 1 and 64),
  repeat_days smallint not null default 0 check (repeat_days between 0 and 127),
  snooze_minutes smallint not null default 5 check (snooze_minutes between 1 and 30),
  vibrate boolean not null default true,
  sound text not null default 'default' check (sound in ('default', 'chime', 'gentle')),
  dismiss text not null default 'tap' check (dismiss in ('tap', 'hold')),
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined', 'cancelled', 'deleted', 'expired')),
  auto boolean not null default false,
  created_at timestamptz not null default clock_timestamp(),
  responded_at timestamptz,
  updated_at timestamptz not null default now()
);
create index alarms_recipient_idx on public.alarms (recipient_id, status);
create index alarms_sender_idx on public.alarms (sender_id, created_at desc);

alter table public.alarms enable row level security;
revoke all on public.alarms from anon, authenticated;
-- Everything goes through the functions below.

-- Data-only push: "your alarms changed, sync them". The inbox item carries the visible part.
create or replace function private.push_alarm_sync(p_user uuid, p_alarm uuid)
returns void
language sql
security definer
set search_path = ''
as $$
  select private.dispatch_push(p_user, jsonb_build_object('type', 'alarm', 'alarm_id', p_alarm, 'v', '1'), 'alarms_v1');
$$;

create or replace function private.alarm_json(a public.alarms, p_viewer uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select jsonb_build_object(
    'id', a.id,
    'from_me', a.sender_id = p_viewer,
    'to_me', a.recipient_id = p_viewer,
    'sender', private.public_card(a.sender_id),
    'recipient', private.public_card(a.recipient_id),
    'title', a.title,
    'message', a.message,
    'ring_at', a.ring_at,
    'local_time', to_char(a.local_time, 'HH24:MI'),
    'time_zone', a.time_zone,
    'repeat_days', a.repeat_days,
    'snooze_minutes', a.snooze_minutes,
    'vibrate', a.vibrate,
    'sound', a.sound,
    'dismiss', a.dismiss,
    'status', a.status,
    'auto', a.auto,
    'created_at', a.created_at,
    'updated_at', a.updated_at);
$$;

-- Creates an alarm. p_ring_local is a wall-clock time in the recipient's zone.
create or replace function public.create_alarm(
  p_recipient uuid,
  p_title text,
  p_message text,
  p_ring_local timestamp,
  p_repeat_days integer default 0,
  p_snooze_minutes integer default 5,
  p_vibrate boolean default true,
  p_sound text default 'default',
  p_dismiss text default 'tap'
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_self boolean := p_recipient = v_uid;
  v_level text;
  v_perm public.relationship_permissions;
  v_tz text;
  v_ring timestamptz;
  v_local time;
  v_auto boolean := false;
  v_alarm public.alarms;
  v_today integer;
begin
  if p_recipient is null or p_ring_local is null or nullif(trim(coalesce(p_title, '')), '') is null
     or char_length(trim(p_title)) > 60 or char_length(coalesce(p_message, '')) > 200
     or coalesce(p_repeat_days, 0) not between 0 and 127
     or coalesce(p_snooze_minutes, 5) not between 1 and 30
     or coalesce(p_sound, 'default') not in ('default', 'chime', 'gentle')
     or coalesce(p_dismiss, 'tap') not in ('tap', 'hold') then
    return jsonb_build_object('status', 'invalid');
  end if;
  if not private.consume_rate_limit('alarm_create:' || v_uid::text, 40, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;

  if not v_self then
    v_level := private.friend_level(v_uid, p_recipient);
    if v_level is null or private.blocked_between(v_uid, p_recipient) then
      return jsonb_build_object('status', 'not_friends');
    end if;
    -- The sender only learns "not accepting right now", never which rule said no.
    if exists (select 1 from public.user_settings s where s.user_id = p_recipient and s.remote_paused) then
      return jsonb_build_object('status', 'unavailable');
    end if;
    select * into v_perm from public.relationship_permissions where owner_id = p_recipient and contact_id = v_uid;
    if found and not v_perm.alarm_requests then
      return jsonb_build_object('status', 'unavailable');
    end if;
  end if;

  v_tz := private.user_timezone(p_recipient);
  v_ring := p_ring_local at time zone v_tz;
  v_local := p_ring_local::time;
  if v_ring < now() + interval '30 seconds' or v_ring > now() + interval '366 days' then
    return jsonb_build_object('status', 'in_past');
  end if;

  if v_self then
    v_auto := true;
  else
    -- Daily maximum: alarms from this friend created during the recipient's current day.
    select count(*) into v_today from public.alarms a
     where a.sender_id = v_uid and a.recipient_id = p_recipient and a.status <> 'cancelled'
       and (a.created_at at time zone v_tz)::date = (now() at time zone v_tz)::date;
    if v_today >= coalesce(v_perm.alarm_daily_max, 1) then
      return jsonb_build_object('status', 'limit');
    end if;
    -- Auto-accept: best friend or partner, switched on, and the ring time inside their window.
    v_auto := coalesce(v_perm.alarm_auto, false) and private.level_rank(v_level) >= 2 and (
      v_perm.alarm_window_start is null
      or (v_perm.alarm_window_start <= v_perm.alarm_window_end and v_local between v_perm.alarm_window_start and v_perm.alarm_window_end)
      or (v_perm.alarm_window_start > v_perm.alarm_window_end and (v_local >= v_perm.alarm_window_start or v_local <= v_perm.alarm_window_end)));
  end if;

  insert into public.alarms (
    sender_id, recipient_id, title, message, ring_at, local_time, time_zone, repeat_days,
    snooze_minutes, vibrate, sound, dismiss, status, auto, responded_at)
  values (
    v_uid, p_recipient, trim(p_title), nullif(trim(coalesce(p_message, '')), ''), v_ring, v_local, v_tz,
    coalesce(p_repeat_days, 0), coalesce(p_snooze_minutes, 5), coalesce(p_vibrate, true),
    coalesce(p_sound, 'default'), coalesce(p_dismiss, 'tap'),
    case when v_auto then 'accepted' else 'pending' end, v_auto,
    case when v_auto then now() end)
  returning * into v_alarm;

  if v_self then
    perform private.push_alarm_sync(v_uid, v_alarm.id);
    return jsonb_build_object('status', 'added', 'alarm_id', v_alarm.id, 'ring_at', v_ring);
  end if;
  if v_auto then
    -- Never silent: "Alarm added by Riya ⏰" with Undo.
    perform private.add_activity(p_recipient, v_uid, 'alarm_added', v_alarm.id,
      jsonb_build_object('title', v_alarm.title, 'local_time', to_char(v_local, 'HH24:MI')));
    perform private.push_alarm_sync(p_recipient, v_alarm.id);
    return jsonb_build_object('status', 'added', 'alarm_id', v_alarm.id, 'ring_at', v_ring);
  end if;
  perform private.add_activity(p_recipient, v_uid, 'alarm_request', v_alarm.id,
    jsonb_build_object('title', v_alarm.title, 'local_time', to_char(v_local, 'HH24:MI')));
  return jsonb_build_object('status', 'sent', 'alarm_id', v_alarm.id, 'ring_at', v_ring);
end;
$$;

-- The recipient accepts or declines a request. Undo of an automatic alarm is a decline.
create or replace function public.respond_alarm(p_alarm uuid, p_accept boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_alarm public.alarms;
begin
  select * into v_alarm from public.alarms where id = p_alarm and recipient_id = v_uid for update;
  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;
  if v_alarm.status not in ('pending', 'accepted') or (v_alarm.status = 'accepted' and p_accept) then
    return jsonb_build_object('status', 'already_' || v_alarm.status);
  end if;
  if p_accept and private.blocked_between(v_uid, v_alarm.sender_id) then
    return jsonb_build_object('status', 'not_friends');
  end if;
  update public.alarms
     set status = case when p_accept then 'accepted' else 'declined' end,
         responded_at = now(), updated_at = now()
   where id = p_alarm
  returning * into v_alarm;
  perform private.resolve_activity(p_alarm, case when p_accept then 'accepted' else 'declined' end);
  if v_alarm.sender_id <> v_uid then
    perform private.add_activity(v_alarm.sender_id, v_uid,
      case when p_accept then 'alarm_accepted' else 'alarm_declined' end, p_alarm,
      jsonb_build_object('title', v_alarm.title, 'local_time', to_char(v_alarm.local_time, 'HH24:MI')));
  end if;
  perform private.push_alarm_sync(v_uid, p_alarm);
  return jsonb_build_object('status', 'ok', 'alarm', private.alarm_json(v_alarm, v_uid));
end;
$$;

-- The sender takes an alarm back, or the recipient deletes it from their phone.
create or replace function public.cancel_alarm(p_alarm uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_alarm public.alarms;
begin
  select * into v_alarm from public.alarms where id = p_alarm and (sender_id = v_uid or recipient_id = v_uid) for update;
  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;
  if v_alarm.status not in ('pending', 'accepted') then
    return jsonb_build_object('status', 'ok');
  end if;
  update public.alarms
     set status = case when recipient_id = v_uid then 'deleted' else 'cancelled' end, updated_at = now()
   where id = p_alarm
  returning * into v_alarm;
  perform private.resolve_activity(p_alarm, 'cancelled');
  if v_alarm.recipient_id <> v_uid then
    perform private.push_alarm_sync(v_alarm.recipient_id, p_alarm);
  else
    perform private.push_alarm_sync(v_uid, p_alarm);
  end if;
  return jsonb_build_object('status', 'ok');
end;
$$;

-- Alarms that should be on my phone (accepted and not finished), plus requests waiting for me.
create or replace function public.list_my_alarms()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(jsonb_agg(private.alarm_json(a, auth.uid()) order by a.ring_at), '[]'::jsonb)
    from public.alarms a
   where a.recipient_id = auth.uid()
     and a.status in ('accepted', 'pending')
     and (a.repeat_days > 0 or a.ring_at > now() - interval '1 day');
$$;

-- Alarms I set for friends, newest first.
create or replace function public.list_sent_alarms()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(jsonb_agg(private.alarm_json(a, auth.uid()) order by a.created_at desc), '[]'::jsonb)
    from (select * from public.alarms
           where sender_id = auth.uid() and recipient_id <> auth.uid()
           order by created_at desc limit 50) a;
$$;

create or replace function public.get_alarm(p_alarm uuid)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select private.alarm_json(a, auth.uid())
    from public.alarms a
   where a.id = p_alarm and (a.sender_id = auth.uid() or a.recipient_id = auth.uid());
$$;

-- Pending requests nobody answered before they would have rung.
create or replace function private.expire_alarm_requests()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  r record;
begin
  for r in
    update public.alarms set status = 'expired', updated_at = now()
     where status = 'pending' and repeat_days = 0 and ring_at < now()
    returning id
  loop
    perform private.resolve_activity(r.id, 'expired');
  end loop;
end;
$$;

-- Alarm news uses the reminders channel (a normal sound). The alarms channel, with
-- the alarm sound, is only for an alarm that is actually ringing.
create or replace function private.activity_channel(p_kind text)
returns text
language sql
immutable
set search_path = ''
as $$
  select case
    when p_kind like 'wallpaper_request%' then 'wallpaper_requests_v1'
    when p_kind like 'wallpaper%' or p_kind like 'doodle%' then 'wallpapers_v1'
    when p_kind in ('alarm_request', 'alarm_added') then 'reminders_v1'
    when p_kind like 'event%' or p_kind like 'reminder%' then 'reminders_v1'
    when p_kind like 'security%' then 'security_v1'
    else 'friends_v1'
  end;
$$;

-- Alarm requests can be answered from the inbox and the notification.
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
    -- "Alarm added by Riya" offers Undo while the alarm is still on.
    when 'alarm_added' then exists (
      select 1 from public.alarms a
       where a.id = p_subject and a.recipient_id = p_user and a.status = 'accepted')
    else false end;
$$;

revoke all on all functions in schema private from public, anon, authenticated;
revoke all on function public.create_alarm(uuid, text, text, timestamp, integer, integer, boolean, text, text) from public, anon, authenticated;
revoke all on function public.respond_alarm(uuid, boolean) from public, anon, authenticated;
revoke all on function public.cancel_alarm(uuid) from public, anon, authenticated;
revoke all on function public.list_my_alarms() from public, anon, authenticated;
revoke all on function public.list_sent_alarms() from public, anon, authenticated;
revoke all on function public.get_alarm(uuid) from public, anon, authenticated;
grant execute on function public.create_alarm(uuid, text, text, timestamp, integer, integer, boolean, text, text) to authenticated;
grant execute on function public.respond_alarm(uuid, boolean) to authenticated;
grant execute on function public.cancel_alarm(uuid) to authenticated;
grant execute on function public.list_my_alarms() to authenticated;
grant execute on function public.list_sent_alarms() to authenticated;
grant execute on function public.get_alarm(uuid) to authenticated;

do $$
begin
  if to_regnamespace('cron') is not null then
    perform cron.schedule('meyouww-expire-alarm-requests', '*/5 * * * *', 'select private.expire_alarm_requests()');
  end if;
exception when others then
  raise warning 'pg_cron not scheduled: %', sqlerrm;
end;
$$;
