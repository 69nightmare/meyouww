-- meyouww Phase 6: end-to-end encrypted chat (Signal Protocol via libsignal).
--
-- The server stores public keys and opaque ciphertext only. Each device publishes
-- an identity key, a signed prekey, a Kyber "last resort" prekey and batches of
-- one-time prekeys; a sender claims one bundle per recipient device (one-time keys
-- are deleted as they are handed out). Messages are fanned out per recipient
-- device, delivered by push + fetch, and deleted once the device acknowledges them.
-- Receipts, reactions, edits and deletions travel as encrypted messages too.
--
-- What the server can still see: who talks to whom, when, ciphertext sizes and
-- device push tokens (documented in docs/E2EE.md).

create table public.device_keys (
  device_id uuid primary key references public.devices (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  -- Small per-user number used as the Signal device ID.
  signal_id integer not null check (signal_id between 1 and 127),
  registration_id integer not null check (registration_id between 1 and 16380),
  identity_key text not null check (char_length(identity_key) between 40 and 100),
  signed_prekey_id integer not null,
  signed_prekey text not null check (char_length(signed_prekey) between 40 and 100),
  signed_prekey_sig text not null check (char_length(signed_prekey_sig) between 80 and 200),
  kyber_last_resort_id integer not null,
  kyber_last_resort text not null check (char_length(kyber_last_resort) between 1000 and 3000),
  kyber_last_resort_sig text not null check (char_length(kyber_last_resort_sig) between 80 and 200),
  updated_at timestamptz not null default now(),
  unique (user_id, signal_id)
);

create table public.one_time_prekeys (
  device_id uuid not null references public.devices (id) on delete cascade,
  key_id integer not null,
  public_key text not null check (char_length(public_key) between 40 and 100),
  primary key (device_id, key_id)
);

create table public.kyber_prekeys (
  device_id uuid not null references public.devices (id) on delete cascade,
  key_id integer not null,
  public_key text not null check (char_length(public_key) between 1000 and 3000),
  signature text not null check (char_length(signature) between 80 and 200),
  primary key (device_id, key_id)
);

create table public.conversations (
  id uuid primary key default gen_random_uuid(),
  user_a uuid not null references auth.users (id) on delete cascade,
  user_b uuid not null references auth.users (id) on delete cascade,
  next_seq bigint not null default 1,
  created_at timestamptz not null default now(),
  last_message_at timestamptz,
  check (user_a < user_b),
  unique (user_a, user_b)
);

create table public.encrypted_messages (
  id bigint generated always as identity primary key,
  message_id uuid not null,
  conversation_id uuid not null references public.conversations (id) on delete cascade,
  sender_id uuid not null references auth.users (id) on delete cascade,
  sender_device_id uuid not null references public.devices (id) on delete cascade,
  recipient_id uuid not null references auth.users (id) on delete cascade,
  recipient_device_id uuid not null references public.devices (id) on delete cascade,
  seq bigint not null,
  envelope_type smallint not null check (envelope_type in (2, 3)),
  body text not null check (char_length(body) between 1 and 200000),
  created_at timestamptz not null default now(),
  unique (message_id, recipient_device_id)
);
create index encrypted_messages_recipient_idx on public.encrypted_messages (recipient_device_id, seq);

alter table public.device_keys enable row level security;
alter table public.one_time_prekeys enable row level security;
alter table public.kyber_prekeys enable row level security;
alter table public.conversations enable row level security;
alter table public.encrypted_messages enable row level security;
revoke all on public.device_keys, public.one_time_prekeys, public.kyber_prekeys, public.conversations,
  public.encrypted_messages from anon, authenticated;
-- Everything goes through the functions below; no direct access at all.

create or replace function private.my_device(p_uid uuid, p_installation_id text)
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
  select id from public.devices where user_id = p_uid and installation_id = p_installation_id and revoked_at is null;
$$;

-- ---------------------------------------------------------------------------
-- Keys
-- ---------------------------------------------------------------------------

-- Publishes (or refreshes) this device's public keys and adds one-time prekeys.
create or replace function public.publish_keys(p_installation_id text, p_keys jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_device uuid := private.my_device(v_uid, p_installation_id);
  k jsonb := coalesce(p_keys, '{}'::jsonb);
  v_signal integer;
  v_changed boolean := false;
begin
  if v_device is null then
    return jsonb_build_object('status', 'not_found');
  end if;
  if not private.consume_rate_limit('publish_keys:' || v_device::text, 30, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  if jsonb_array_length(coalesce(k -> 'prekeys', '[]')) > 200 or jsonb_array_length(coalesce(k -> 'kyber_prekeys', '[]')) > 200 then
    return jsonb_build_object('status', 'invalid');
  end if;

  begin
    if k ? 'identity_key' then
      select signal_id, identity_key <> (k ->> 'identity_key') into v_signal, v_changed
        from public.device_keys where device_id = v_device;
      if v_signal is null then
        select coalesce(min(n), 1) into v_signal from generate_series(1, 127) n
         where n not in (select signal_id from public.device_keys where user_id = v_uid);
      end if;
      -- A new identity means everything old is useless.
      if coalesce(v_changed, false) then
        delete from public.one_time_prekeys where device_id = v_device;
        delete from public.kyber_prekeys where device_id = v_device;
      end if;
      insert into public.device_keys as dk (
        device_id, user_id, signal_id, registration_id, identity_key,
        signed_prekey_id, signed_prekey, signed_prekey_sig,
        kyber_last_resort_id, kyber_last_resort, kyber_last_resort_sig, updated_at)
      values (
        v_device, v_uid, v_signal, (k ->> 'registration_id')::integer, k ->> 'identity_key',
        (k ->> 'signed_prekey_id')::integer, k ->> 'signed_prekey', k ->> 'signed_prekey_sig',
        (k ->> 'kyber_last_resort_id')::integer, k ->> 'kyber_last_resort', k ->> 'kyber_last_resort_sig', now())
      on conflict (device_id) do update set
        registration_id = excluded.registration_id, identity_key = excluded.identity_key,
        signed_prekey_id = excluded.signed_prekey_id, signed_prekey = excluded.signed_prekey,
        signed_prekey_sig = excluded.signed_prekey_sig,
        kyber_last_resort_id = excluded.kyber_last_resort_id, kyber_last_resort = excluded.kyber_last_resort,
        kyber_last_resort_sig = excluded.kyber_last_resort_sig, updated_at = now();
    elsif not exists (select 1 from public.device_keys where device_id = v_device) then
      return jsonb_build_object('status', 'invalid');
    end if;

    insert into public.one_time_prekeys (device_id, key_id, public_key)
    select v_device, (x ->> 'id')::integer, x ->> 'key' from jsonb_array_elements(coalesce(k -> 'prekeys', '[]')) x
    on conflict do nothing;
    insert into public.kyber_prekeys (device_id, key_id, public_key, signature)
    select v_device, (x ->> 'id')::integer, x ->> 'key', x ->> 'sig' from jsonb_array_elements(coalesce(k -> 'kyber_prekeys', '[]')) x
    on conflict do nothing;
  exception when invalid_text_representation or check_violation or not_null_violation or numeric_value_out_of_range then
    return jsonb_build_object('status', 'invalid');
  end;

  return jsonb_build_object(
    'status', 'ok',
    'signal_id', (select signal_id from public.device_keys where device_id = v_device),
    'prekeys', (select count(*) from public.one_time_prekeys where device_id = v_device),
    'kyber_prekeys', (select count(*) from public.kyber_prekeys where device_id = v_device));
end;
$$;

create or replace function public.prekey_counts(p_installation_id text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_device uuid := private.my_device(v_uid, p_installation_id);
begin
  if v_device is null or not exists (select 1 from public.device_keys where device_id = v_device) then
    return jsonb_build_object('status', 'not_found');
  end if;
  return jsonb_build_object(
    'status', 'ok',
    'signal_id', (select signal_id from public.device_keys where device_id = v_device),
    'identity_key', (select identity_key from public.device_keys where device_id = v_device),
    'prekeys', (select count(*) from public.one_time_prekeys where device_id = v_device),
    'kyber_prekeys', (select count(*) from public.kyber_prekeys where device_id = v_device));
end;
$$;

-- One prekey bundle per active device of a friend. One-time keys are removed as
-- they are handed out; when they run out the Kyber last-resort key is used.
create or replace function public.claim_bundles(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  r record;
  v_pre_id integer;
  v_pre_key text;
  v_k_id integer;
  v_k_key text;
  v_k_sig text;
  v_out jsonb := '[]'::jsonb;
begin
  if private.friend_level(v_uid, p_user) is null or private.blocked_between(v_uid, p_user) then
    return jsonb_build_object('status', 'not_friends', 'bundles', '[]'::jsonb);
  end if;
  if not private.consume_rate_limit('claim_bundles:' || v_uid::text, 120, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited', 'bundles', '[]'::jsonb);
  end if;
  for r in
    select dk.* from public.device_keys dk join public.devices d on d.id = dk.device_id
     where dk.user_id = p_user and d.revoked_at is null
  loop
    v_pre_id := null; v_pre_key := null; v_k_id := null; v_k_key := null; v_k_sig := null;
    delete from public.one_time_prekeys
     where ctid = (select ctid from public.one_time_prekeys where device_id = r.device_id order by key_id limit 1 for update skip locked)
     returning key_id, public_key into v_pre_id, v_pre_key;
    delete from public.kyber_prekeys
     where ctid = (select ctid from public.kyber_prekeys where device_id = r.device_id order by key_id limit 1 for update skip locked)
     returning key_id, public_key, signature into v_k_id, v_k_key, v_k_sig;
    v_out := v_out || jsonb_build_array(jsonb_build_object(
      'device_id', r.device_id,
      'signal_id', r.signal_id,
      'registration_id', r.registration_id,
      'identity_key', r.identity_key,
      'signed_prekey_id', r.signed_prekey_id,
      'signed_prekey', r.signed_prekey,
      'signed_prekey_sig', r.signed_prekey_sig,
      'prekey_id', v_pre_id,
      'prekey', v_pre_key,
      'kyber_id', coalesce(v_k_id, r.kyber_last_resort_id),
      'kyber', coalesce(v_k_key, r.kyber_last_resort),
      'kyber_sig', coalesce(v_k_sig, r.kyber_last_resort_sig)));
  end loop;
  return jsonb_build_object('status', 'ok', 'bundles', v_out);
end;
$$;

-- ---------------------------------------------------------------------------
-- Conversations and messages
-- ---------------------------------------------------------------------------

create or replace function public.open_conversation(p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_id uuid;
begin
  if p_user is null or p_user = v_uid or private.friend_level(v_uid, p_user) is null or private.blocked_between(v_uid, p_user) then
    return jsonb_build_object('status', 'not_friends');
  end if;
  insert into public.conversations (user_a, user_b) values (least(v_uid, p_user), greatest(v_uid, p_user))
  on conflict (user_a, user_b) do nothing;
  select id into v_id from public.conversations where user_a = least(v_uid, p_user) and user_b = greatest(v_uid, p_user);
  return jsonb_build_object('status', 'ok', 'conversation_id', v_id);
end;
$$;

-- Stores one encrypted copy per recipient device and pushes them. Retries with the
-- same message ID are harmless.
create or replace function public.send_message(
  p_conversation uuid, p_message_id uuid, p_installation_id text, p_envelopes jsonb)
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

  if v_count > 0 then
    perform private.dispatch_push(
      v_peer,
      jsonb_build_object('type', 'message', 'conversation_id', p_conversation, 'message_id', p_message_id, 'v', '1'),
      'messages_v1');
  end if;
  return jsonb_build_object('status', 'ok', 'seq', v_seq);
end;
$$;

-- Messages waiting for this device, oldest first.
create or replace function public.fetch_messages(p_installation_id text, p_limit integer default 200)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_device uuid := private.my_device(v_uid, p_installation_id);
begin
  if v_device is null then
    return '[]'::jsonb;
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'id', m.id, 'message_id', m.message_id, 'conversation_id', m.conversation_id,
             'sender_id', m.sender_id, 'sender_device_id', m.sender_device_id, 'sender_signal_id', coalesce(dk.signal_id, 1),
             'seq', m.seq, 'type', m.envelope_type, 'body', m.body, 'created_at', m.created_at)
           order by m.conversation_id, m.seq)
      from (select * from public.encrypted_messages
             where recipient_device_id = v_device
             order by created_at, seq
             limit least(greatest(coalesce(p_limit, 200), 1), 500)) m
      left join public.device_keys dk on dk.device_id = m.sender_device_id
  ), '[]'::jsonb);
end;
$$;

-- Deletes delivered ciphertext. The server keeps nothing once a device has it.
create or replace function public.ack_messages(p_installation_id text, p_ids bigint[])
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_device uuid := private.my_device(v_uid, p_installation_id);
  v_count integer;
begin
  if v_device is null then
    return 0;
  end if;
  delete from public.encrypted_messages where recipient_device_id = v_device and id = any(p_ids);
  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

-- Undelivered ciphertext is kept for 30 days at most.
create or replace function private.expire_messages()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.encrypted_messages where created_at < now() - interval '30 days';
$$;

do $$
begin
  if to_regnamespace('cron') is not null then
    perform cron.schedule('meyouww-expire-messages', '17 3 * * *', 'select private.expire_messages()');
  end if;
exception when others then
  raise warning 'pg_cron not scheduled: %', sqlerrm;
end;
$$;

revoke all on all functions in schema private from public, anon, authenticated;
revoke all on function public.publish_keys(text, jsonb) from public, anon, authenticated;
revoke all on function public.prekey_counts(text) from public, anon, authenticated;
revoke all on function public.claim_bundles(uuid) from public, anon, authenticated;
revoke all on function public.open_conversation(uuid) from public, anon, authenticated;
revoke all on function public.send_message(uuid, uuid, text, jsonb) from public, anon, authenticated;
revoke all on function public.fetch_messages(text, integer) from public, anon, authenticated;
revoke all on function public.ack_messages(text, bigint[]) from public, anon, authenticated;
grant execute on function public.publish_keys(text, jsonb) to authenticated;
grant execute on function public.prekey_counts(text) to authenticated;
grant execute on function public.claim_bundles(uuid) to authenticated;
grant execute on function public.open_conversation(uuid) to authenticated;
grant execute on function public.send_message(uuid, uuid, text, jsonb) to authenticated;
grant execute on function public.fetch_messages(text, integer) to authenticated;
grant execute on function public.ack_messages(text, bigint[]) to authenticated;
