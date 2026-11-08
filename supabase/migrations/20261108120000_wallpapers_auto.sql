-- meyouww: the sender picks the screen (home, lock or both) and the picture goes on by
-- itself on the friend's phone, for every friend level. The person receiving it is
-- always told (with Undo and Pause) and can still choose "Ask me first", "Pause" or
-- "Never" for any friend. Automatic alarms and reminders keep needing a Best Friend.

alter table public.relationship_permissions alter column wallpaper_mode set default 'auto';

-- Existing friendships move to automatic too, except where someone paused or said never.
update public.relationship_permissions
   set wallpaper_mode = 'auto', version = version + 1, updated_at = now()
 where wallpaper_mode = 'ask';

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
    return 'auto';
  end if;
  if v_perm.wallpaper_mode = 'never'
     or (v_perm.wallpaper_mode = 'paused' and (v_perm.paused_until is null or v_perm.paused_until > now()))
     or not (v_perm.wallpaper_home or v_perm.wallpaper_lock) then
    return 'unavailable';
  end if;
  if v_perm.wallpaper_mode = 'auto' then
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
  if private.level_rank(p_level) < 2 then
    update public.relationship_permissions
       set alarm_auto = false,
           reminder_auto_categories = '{}',
           version = version + 1,
           updated_at = now()
     where (owner_id = p_a and contact_id = p_b) or (owner_id = p_b and contact_id = p_a);
  end if;
end;
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

  -- Automatic alarms and reminders need a Best Friend or Partner. Wallpapers go on
  -- automatically for every friend (the owner's choice); they can still ask, pause or say never.
  if private.level_rank(v_level) < 2
     and (v.alarm_auto or cardinality(v.reminder_auto_categories) > 0) then
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
    version = version + 1,
    updated_at = now()
  where owner_id = v_uid and contact_id = p_contact
  returning * into v;
  return jsonb_build_object('status', 'ok', 'permissions', private.permissions_json(v));
end;
$$;

revoke all on all functions in schema private from public, anon, authenticated;
