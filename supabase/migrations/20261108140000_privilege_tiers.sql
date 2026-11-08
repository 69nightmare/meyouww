-- meyouww: three relationship levels, each with its own privileges (owner's decision, 8 Nov).
--
--                  Friend        Best friend          Girlfriend / boyfriend
--   Wallpaper      asks first    asks first           goes on by itself
--   Alarm          asks first    set by itself        set by itself
--   Reminder       asks first    added directly       added directly
--   Phone sound    -             -                    ring / vibrate / mute
-- (Alarms set directly always come with a Remove button.)
--
-- Only one girlfriend / boyfriend at a time, and both have to accept. Everyone keeps
-- per-person switches (Ask first / Pause / Never, alarms off, sound changes off).

alter table public.relationship_permissions alter column alarm_auto set default true;
update public.relationship_permissions set alarm_auto = true, version = version + 1, updated_at = now() where not alarm_auto;
alter table public.relationship_permissions add column if not exists ringer_allowed boolean not null default true;
-- Alarms set directly need room for more than one a day.
alter table public.relationship_permissions alter column alarm_daily_max set default 3;
update public.relationship_permissions set alarm_daily_max = 3, version = version + 1, updated_at = now() where alarm_daily_max = 1;

-- The person's current girlfriend / boyfriend, if any.
create or replace function private.partner_of(p_user uuid)
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
  select case when f.user_a = p_user then f.user_b else f.user_a end
    from public.friendships f
   where (f.user_a = p_user or f.user_b = p_user) and f.level = 'partner'
   limit 1;
$$;

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
    return case when private.level_rank(v_level) >= 3 then 'auto' else 'ask' end;
  end if;
  if v_perm.wallpaper_mode = 'never'
     or (v_perm.wallpaper_mode = 'paused' and (v_perm.paused_until is null or v_perm.paused_until > now()))
     or not (v_perm.wallpaper_home or v_perm.wallpaper_lock) then
    return 'unavailable';
  end if;
  -- Only a girlfriend / boyfriend puts wallpapers on by themselves; friends and best friends ask first.
  if v_perm.wallpaper_mode = 'auto' and private.level_rank(v_level) >= 3 then
    return 'auto';
  end if;
  return 'ask';
end;
$$;

create or replace function private.revoke_above_level(p_a uuid, p_b uuid, p_level text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  -- Nothing to switch off any more: what each level may do is decided from the level
  -- itself (see capability, create_alarm, create_event and set_ringer), so stepping
  -- down takes effect at once and stepping back up restores it.
  return;
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
    'share_screen_specs', p.share_screen_specs,
    'ringer_allowed', p.ringer_allowed
  );
$$;

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
                 'alarm_window_start', 'alarm_window_end', 'alarm_daily_max', 'share_screen_specs', 'ringer_allowed') then
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
    if c ? 'ringer_allowed' then v.ringer_allowed := (c ->> 'ringer_allowed')::boolean; end if;
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

  -- Automatic wallpapers are for a girlfriend / boyfriend only.
  if c ->> 'wallpaper_mode' = 'auto' and private.level_rank(v_level) < 3 then
    return jsonb_build_object('status', 'needs_closer_level');
  end if;
  -- Every automatic change must be announced, so turning it on needs a phone that shows notifications.
  if c ->> 'wallpaper_mode' = 'auto' and not exists (
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
    ringer_allowed = v.ringer_allowed,
    version = version + 1,
    updated_at = now()
  where owner_id = v_uid and contact_id = p_contact
  returning * into v;
  return jsonb_build_object('status', 'ok', 'permissions', private.permissions_json(v));
end;
$$;

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
    -- Set straight away for a best friend or girlfriend / boyfriend (unless they switched
    -- it off or it's outside their window); friends ask first.
    v_auto := coalesce(v_perm.alarm_auto, true) and private.level_rank(v_level) >= 2 and (
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
    -- Best friends and girlfriend / boyfriend add reminders directly; friends suggest them.
    v_status := case when private.level_rank(private.friend_level(v_uid, v_invitee)) >= 2 then 'going' else 'invited' end;
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
  -- One girlfriend / boyfriend at a time, and only one open ask.
  if p_level = 'partner' then
    if private.partner_of(v_uid) is not null then
      return jsonb_build_object('status', 'already_partnered');
    end if;
    if exists (select 1 from public.relationship_requests r
                where r.from_user = v_uid and r.to_user <> p_user and r.level = 'partner'
                  and r.status = 'pending' and r.expires_at > now()) then
      return jsonb_build_object('status', 'already_asked');
    end if;
    if private.partner_of(p_user) is not null then
      return jsonb_build_object('status', 'unavailable');
    end if;
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
    -- Still only one girlfriend / boyfriend each.
    if v_req.level = 'partner'
       and (coalesce(private.partner_of(v_uid), v_req.from_user) <> v_req.from_user
            or coalesce(private.partner_of(v_req.from_user), v_uid) <> v_uid) then
      return jsonb_build_object('status', 'already_partnered');
    end if;
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

-- A girlfriend / boyfriend switches the other's phone to ring, vibrate or mute.
-- The phone does it right away and says who did it, with Undo.
create or replace function public.set_ringer(p_recipient uuid, p_mode text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_perm public.relationship_permissions;
begin
  if p_mode not in ('ring', 'vibrate', 'mute') then
    return jsonb_build_object('status', 'invalid');
  end if;
  if p_recipient = v_uid or private.friend_level(v_uid, p_recipient) is distinct from 'partner'
     or private.blocked_between(v_uid, p_recipient) then
    return jsonb_build_object('status', 'needs_closer_level');
  end if;
  if exists (select 1 from public.user_settings s where s.user_id = p_recipient and s.remote_paused) then
    return jsonb_build_object('status', 'unavailable');
  end if;
  select * into v_perm from public.relationship_permissions where owner_id = p_recipient and contact_id = v_uid;
  if found and not v_perm.ringer_allowed then
    return jsonb_build_object('status', 'unavailable');
  end if;
  if not private.consume_rate_limit('ringer:' || v_uid::text, 30, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  perform private.dispatch_push(p_recipient,
    jsonb_build_object('type', 'ringer', 'mode', p_mode, 'from', v_uid, 'v', '1'), 'friends_v1');
  return jsonb_build_object('status', 'ok');
end;
$$;

revoke all on function public.set_ringer(uuid, text) from public, anon;
grant execute on function public.set_ringer(uuid, text) to authenticated;
revoke all on all functions in schema private from public, anon, authenticated;
