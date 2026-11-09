-- Phones still on versions before 0.15.0 never mark receipts, reactions or edits as silent,
-- and the server can't tell those from a real text (all are encrypted the same way). So
-- every "delivered" or "read" from such a phone left an empty "New message" on the other
-- phone. Their pushes now go data-only: the receiving app still decrypts and shows real
-- messages with the sender's name and text, and receipts no longer leave anything behind.
-- Unknown versions count as current.

create or replace function private.device_before(p_device uuid, p_version integer[])
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce((
    select string_to_array(substring(d.app_version from '^[0-9]+[.][0-9]+[.][0-9]+'), '.')::integer[] < p_version
      from public.devices d
     where d.id = p_device and d.app_version ~ '^[0-9]+[.][0-9]+[.][0-9]+'), false);
$$;

revoke all on function private.device_before(uuid, integer[]) from public, anon, authenticated;

create or replace function public.send_message(
  p_conversation uuid, p_message_id uuid, p_installation_id text, p_envelopes jsonb,
  p_silent boolean default false)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_device uuid := private.my_device(v_uid, p_installation_id);
  v_conv public.conversations;
  v_peer uuid;
  v_seq bigint;
  v_existing bigint;
  v_count integer;
  v_silent boolean;
begin
  if v_device is null then
    return jsonb_build_object('status', 'not_found');
  end if;
  select * into v_conv from public.conversations where id = p_conversation and (user_a = v_uid or user_b = v_uid);
  if not found then
    return jsonb_build_object('status', 'not_found');
  end if;
  v_peer := case when v_conv.user_a = v_uid then v_conv.user_b else v_conv.user_a end;
  if private.friend_level(v_uid, v_peer) is null or private.blocked_between(v_uid, v_peer) then
    return jsonb_build_object('status', 'not_friends');
  end if;
  select seq into v_existing from public.encrypted_messages where message_id = p_message_id limit 1;
  if v_existing is not null then
    return jsonb_build_object('status', 'ok', 'seq', v_existing);
  end if;
  if not private.consume_rate_limit('send_message:' || v_uid::text, 600, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  if jsonb_typeof(p_envelopes) <> 'array' or jsonb_array_length(p_envelopes) between 1 and 16 is not true then
    return jsonb_build_object('status', 'invalid');
  end if;
  -- Every envelope must be for an active, keyed device of the other person.
  if exists (
       select 1 from jsonb_array_elements(p_envelopes) e
        where not exists (
          select 1 from public.device_keys dk join public.devices d on d.id = dk.device_id
           where dk.device_id = (e ->> 'device_id')::uuid and dk.user_id = v_peer and d.revoked_at is null)) then
    return jsonb_build_object('status', 'stale_devices');
  end if;
  -- ...and every such device must get one (a new phone of theirs would otherwise miss it).
  if exists (
       select 1 from public.device_keys dk join public.devices d on d.id = dk.device_id
        where dk.user_id = v_peer and d.revoked_at is null
          and dk.device_id not in (select (e ->> 'device_id')::uuid from jsonb_array_elements(p_envelopes) e)) then
    return jsonb_build_object('status', 'stale_devices');
  end if;

  update public.conversations set next_seq = next_seq + 1, last_message_at = now()
   where id = p_conversation returning next_seq - 1 into v_seq;
  insert into public.encrypted_messages (
    message_id, conversation_id, sender_id, sender_device_id, recipient_id, recipient_device_id,
    seq, envelope_type, body)
  select p_message_id, p_conversation, v_uid, v_device, v_peer, (e ->> 'device_id')::uuid,
         v_seq, (e ->> 'type')::smallint, e ->> 'body'
    from jsonb_array_elements(p_envelopes) e
  on conflict (message_id, recipient_device_id) do nothing;
  get diagnostics v_count = row_count;

  v_silent := p_silent or private.device_before(v_device, array[0, 15, 0]);
  if v_count > 0 then
    perform private.dispatch_push(
      v_peer,
      jsonb_build_object('type', 'message', 'conversation_id', p_conversation, 'message_id', p_message_id, 'v', '1')
        || case when v_silent then jsonb_build_object('silent', '1') else '{}'::jsonb end,
      'messages_v1');
  end if;
  return jsonb_build_object('status', 'ok', 'seq', v_seq);
end;
$$;

revoke all on function public.send_message(uuid, uuid, text, jsonb, boolean) from public, anon, authenticated;
grant execute on function public.send_message(uuid, uuid, text, jsonb, boolean) to authenticated;
