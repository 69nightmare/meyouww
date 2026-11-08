-- Phone-sound pushes never arrived: FCM refuses data messages with a key called
-- "from" (reserved, like "message_type" and anything starting with "google" or
-- "gcm") and answered 400 INVALID_ARGUMENT. The sender's ID now travels as "sender".

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
    jsonb_build_object('type', 'ringer', 'mode', p_mode, 'sender', v_uid, 'v', '1'), 'friends_v1');
  return jsonb_build_object('status', 'ok');
end;
$$;

revoke all on function public.set_ringer(uuid, text) from public, anon;
grant execute on function public.set_ringer(uuid, text) to authenticated;
