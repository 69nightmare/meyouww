-- Group chats (up to 32 people), end-to-end encrypted like 1:1 chats.
--
-- The server knows who is in which group (it has to route messages) but nothing that is
-- said: each message is encrypted separately for every member's phone with Signal
-- (client-side fan-out), and the group's name, polls and reminders travel inside those
-- encrypted messages. Members who aren't friends can fetch each other's public keys
-- because they share a group. Only the group's admins add or remove people; anyone can
-- leave. A group needs at least one admin: if the last one leaves, the longest-standing
-- member becomes admin.

create table public.chat_groups (
  id uuid primary key default gen_random_uuid(),
  created_by uuid not null references auth.users (id) on delete cascade,
  next_seq bigint not null default 1,
  created_at timestamptz not null default now()
);

create table public.group_members (
  group_id uuid not null references public.chat_groups (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null default 'member' check (role in ('admin', 'member')),
  added_by uuid references auth.users (id) on delete set null,
  joined_at timestamptz not null default now(),
  left_at timestamptz,
  primary key (group_id, user_id)
);
create index group_members_user_idx on public.group_members (user_id) where left_at is null;

alter table public.chat_groups enable row level security;
alter table public.group_members enable row level security;
revoke all on public.chat_groups, public.group_members from anon, authenticated;

-- Group messages use the same ciphertext store as 1:1 chats: exactly one target.
alter table public.encrypted_messages alter column conversation_id drop not null;
alter table public.encrypted_messages add column if not exists group_id uuid references public.chat_groups (id) on delete cascade;
alter table public.encrypted_messages drop constraint if exists encrypted_messages_one_target;
alter table public.encrypted_messages add constraint encrypted_messages_one_target
  check ((conversation_id is null) <> (group_id is null));

create or replace function private.is_group_member(p_group uuid, p_user uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.group_members
                  where group_id = p_group and user_id = p_user and left_at is null);
$$;

create or replace function private.share_group(p_a uuid, p_b uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (select 1 from public.group_members a join public.group_members b on b.group_id = a.group_id
                  where a.user_id = p_a and b.user_id = p_b and a.left_at is null and b.left_at is null);
$$;

-- Tells every member (and anyone just removed) to refresh the group.
create or replace function private.notify_group(p_group uuid, p_also uuid default null)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid;
begin
  for v_user in
    select user_id from public.group_members where group_id = p_group and left_at is null
    union select p_also where p_also is not null
  loop
    perform private.dispatch_push(v_user, jsonb_build_object('type', 'group', 'group_id', p_group, 'v', '1'), 'messages_v1');
  end loop;
end;
$$;

-- Friends whose phone runs a version without groups (before 0.18.0): they would miss every
-- group message, so they are asked to update first. No active phone counts as fine.
create or replace function private.needs_group_update(p_users uuid[])
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(jsonb_agg(u), '[]'::jsonb)
    from unnest(p_users) u
   where exists (select 1 from public.devices d
                  where d.user_id = u and d.revoked_at is null and private.device_before(d.id, array[0, 18, 0]));
$$;

-- A new group with the caller as admin and some of their friends.
create or replace function public.create_group(p_members uuid[])
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_group uuid;
  v_member uuid;
  v_old jsonb;
  v_members uuid[] := (select coalesce(array_agg(distinct m), '{}') from unnest(coalesce(p_members, '{}')) m where m <> v_uid);
begin
  if cardinality(v_members) not between 1 and 31 then
    return jsonb_build_object('status', 'invalid');
  end if;
  if exists (select 1 from unnest(v_members) m
              where private.friend_level(v_uid, m) is null or private.blocked_between(v_uid, m)) then
    return jsonb_build_object('status', 'not_friends');
  end if;
  v_old := private.needs_group_update(v_members);
  if jsonb_array_length(v_old) > 0 then
    return jsonb_build_object('status', 'needs_update', 'users', v_old);
  end if;
  if not private.consume_rate_limit('create_group:' || v_uid::text, 20, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  insert into public.chat_groups (created_by) values (v_uid) returning id into v_group;
  insert into public.group_members (group_id, user_id, role, added_by) values (v_group, v_uid, 'admin', v_uid);
  foreach v_member in array v_members loop
    insert into public.group_members (group_id, user_id, role, added_by) values (v_group, v_member, 'member', v_uid);
  end loop;
  perform private.notify_group(v_group);
  return jsonb_build_object('status', 'ok', 'group_id', v_group);
end;
$$;

-- Admins add their own friends (up to 32 people in a group).
create or replace function public.add_group_members(p_group uuid, p_members uuid[])
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_member uuid;
  v_old jsonb;
  v_members uuid[] := (select coalesce(array_agg(distinct m), '{}') from unnest(coalesce(p_members, '{}')) m where m <> v_uid);
begin
  if not exists (select 1 from public.group_members where group_id = p_group and user_id = v_uid and left_at is null and role = 'admin') then
    return jsonb_build_object('status', 'not_admin');
  end if;
  if cardinality(v_members) = 0 then
    return jsonb_build_object('status', 'invalid');
  end if;
  if exists (select 1 from unnest(v_members) m
              where private.friend_level(v_uid, m) is null or private.blocked_between(v_uid, m)) then
    return jsonb_build_object('status', 'not_friends');
  end if;
  if (select count(*) from public.group_members where group_id = p_group and left_at is null)
     + (select count(*) from unnest(v_members) m where not private.is_group_member(p_group, m)) > 32 then
    return jsonb_build_object('status', 'full');
  end if;
  v_old := private.needs_group_update(v_members);
  if jsonb_array_length(v_old) > 0 then
    return jsonb_build_object('status', 'needs_update', 'users', v_old);
  end if;
  foreach v_member in array v_members loop
    insert into public.group_members as g (group_id, user_id, role, added_by) values (p_group, v_member, 'member', v_uid)
    on conflict (group_id, user_id) do update set left_at = null, joined_at = now(), role = 'member', added_by = v_uid
      where g.left_at is not null;
  end loop;
  perform private.notify_group(p_group);
  return jsonb_build_object('status', 'ok');
end;
$$;

-- An admin removes someone, or anyone leaves (p_user = themselves).
create or replace function public.remove_group_member(p_group uuid, p_user uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  if not private.is_group_member(p_group, v_uid) then
    return jsonb_build_object('status', 'not_member');
  end if;
  if p_user <> v_uid and not exists (select 1 from public.group_members
                                      where group_id = p_group and user_id = v_uid and left_at is null and role = 'admin') then
    return jsonb_build_object('status', 'not_admin');
  end if;
  update public.group_members set left_at = now(), role = 'member'
   where group_id = p_group and user_id = p_user and left_at is null;
  if not found then
    return jsonb_build_object('status', 'not_member');
  end if;
  -- Keep an admin: the longest-standing member takes over.
  if not exists (select 1 from public.group_members where group_id = p_group and left_at is null and role = 'admin') then
    update public.group_members set role = 'admin'
     where group_id = p_group and left_at is null
       and user_id = (select user_id from public.group_members where group_id = p_group and left_at is null order by joined_at limit 1);
  end if;
  perform private.notify_group(p_group, p_user);
  return jsonb_build_object('status', 'ok');
end;
$$;

-- My groups and their members (names and photos as friends see them).
create or replace function public.list_my_groups()
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(jsonb_agg(jsonb_build_object(
           'group_id', g.id,
           'created_by', g.created_by,
           'created_at', g.created_at,
           'members', (select coalesce(jsonb_agg(jsonb_build_object(
                         'user_id', m.user_id, 'role', m.role,
                         'name', coalesce(p.display_name, p.username, ''), 'username', p.username,
                         'avatar', p.avatar_path) order by m.joined_at), '[]'::jsonb)
                         from public.group_members m left join public.profiles p on p.id = m.user_id
                        where m.group_id = g.id and m.left_at is null))
         order by g.created_at), '[]'::jsonb)
    from public.chat_groups g
   where private.is_group_member(g.id, (select auth.uid()));
$$;

-- Stores one encrypted copy for every phone of every other member, and pushes them.
create or replace function public.send_group_message(
  p_group uuid, p_message_id uuid, p_installation_id text, p_envelopes jsonb, p_silent boolean default false)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_device uuid := private.my_device(v_uid, p_installation_id);
  v_seq bigint;
  v_existing bigint;
  v_user uuid;
begin
  if v_device is null then
    return jsonb_build_object('status', 'not_found');
  end if;
  if not private.is_group_member(p_group, v_uid) then
    return jsonb_build_object('status', 'not_member');
  end if;
  select seq into v_existing from public.encrypted_messages where message_id = p_message_id limit 1;
  if v_existing is not null then
    return jsonb_build_object('status', 'ok', 'seq', v_existing);
  end if;
  if not private.consume_rate_limit('send_message:' || v_uid::text, 600, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  if jsonb_typeof(p_envelopes) <> 'array' or jsonb_array_length(p_envelopes) between 1 and 128 is not true then
    return jsonb_build_object('status', 'invalid');
  end if;
  -- Every envelope must be for an active, keyed phone of another member...
  if exists (
       select 1 from jsonb_array_elements(p_envelopes) e
        where not exists (
          select 1 from public.device_keys dk join public.devices d on d.id = dk.device_id
            join public.group_members m on m.user_id = dk.user_id
           where dk.device_id = (e ->> 'device_id')::uuid and d.revoked_at is null
             and m.group_id = p_group and m.left_at is null and m.user_id <> v_uid)) then
    return jsonb_build_object('status', 'stale_devices');
  end if;
  -- ...and every such phone must get one (except people blocked either way: claim_bundles
  -- gives the sender no keys for them, so they can't be encrypted to).
  if exists (
       select 1 from public.device_keys dk join public.devices d on d.id = dk.device_id
         join public.group_members m on m.user_id = dk.user_id
        where m.group_id = p_group and m.left_at is null and m.user_id <> v_uid and d.revoked_at is null
          and not private.blocked_between(v_uid, m.user_id)
          and dk.device_id not in (select (e ->> 'device_id')::uuid from jsonb_array_elements(p_envelopes) e)) then
    return jsonb_build_object('status', 'stale_devices');
  end if;

  update public.chat_groups set next_seq = next_seq + 1 where id = p_group returning next_seq - 1 into v_seq;
  insert into public.encrypted_messages (
    message_id, conversation_id, group_id, sender_id, sender_device_id, recipient_id, recipient_device_id,
    seq, envelope_type, body)
  select p_message_id, null, p_group, v_uid, v_device, dk.user_id, dk.device_id,
         v_seq, (e ->> 'type')::smallint, e ->> 'body'
    from jsonb_array_elements(p_envelopes) e
    join public.device_keys dk on dk.device_id = (e ->> 'device_id')::uuid
  on conflict (message_id, recipient_device_id) do nothing;

  for v_user in select distinct dk.user_id from jsonb_array_elements(p_envelopes) e
                  join public.device_keys dk on dk.device_id = (e ->> 'device_id')::uuid
  loop
    perform private.dispatch_push(
      v_user,
      jsonb_build_object('type', 'message', 'group_id', p_group, 'message_id', p_message_id, 'v', '1')
        || case when p_silent then jsonb_build_object('silent', '1') else '{}'::jsonb end,
      'messages_v1');
  end loop;
  return jsonb_build_object('status', 'ok', 'seq', v_seq);
end;
$$;

-- Group members can encrypt to each other: copied from phase 6 with that one change.
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
  -- Friends, or people in a group together (their messages are encrypted to each other).
  if (private.friend_level(v_uid, p_user) is null and not private.share_group(v_uid, p_user))
     or private.blocked_between(v_uid, p_user) then
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

-- Envelopes now say which group they belong to (copied from phase 6, plus group_id).
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
             'id', m.id, 'message_id', m.message_id, 'conversation_id', m.conversation_id, 'group_id', m.group_id,
             'sender_id', m.sender_id, 'sender_device_id', m.sender_device_id, 'sender_signal_id', coalesce(dk.signal_id, 1),
             'seq', m.seq, 'type', m.envelope_type, 'body', m.body, 'created_at', m.created_at)
           order by coalesce(m.conversation_id, m.group_id), m.seq)
      from (select * from public.encrypted_messages
             where recipient_device_id = v_device
             order by created_at, seq
             limit least(greatest(coalesce(p_limit, 200), 1), 500)) m
      left join public.device_keys dk on dk.device_id = m.sender_device_id
  ), '[]'::jsonb);
end;
$$;

-- Encrypted files in a group: the folder is <sender>/<group>/<message>.
create or replace function public.can_use_chat_media(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.conversations c
     where c.id::text = (storage.foldername(p_name))[2]
       and (select auth.uid()) in (c.user_a, c.user_b)
       and (storage.foldername(p_name))[1] in (c.user_a::text, c.user_b::text)
  ) or exists (
    select 1 from public.group_members me join public.group_members sender on sender.group_id = me.group_id
     where me.group_id::text = (storage.foldername(p_name))[2]
       and me.user_id = (select auth.uid()) and me.left_at is null
       and sender.user_id::text = (storage.foldername(p_name))[1]
  );
$$;

-- Deleting: in a 1:1 chat either person (the receiving phone removes the file once it has it);
-- in a group only the sender, because the other members still need it. Unfetched files are
-- swept after 7 days either way.
create or replace function public.can_delete_chat_media(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.conversations c
     where c.id::text = (storage.foldername(p_name))[2]
       and (select auth.uid()) in (c.user_a, c.user_b)
       and (storage.foldername(p_name))[1] in (c.user_a::text, c.user_b::text)
  ) or (
    (storage.foldername(p_name))[1] = (select auth.uid())::text and public.can_use_chat_media(p_name)
  );
$$;

revoke all on function public.can_delete_chat_media(text) from public, anon;
grant execute on function public.can_delete_chat_media(text) to authenticated;

do $$
begin
  if to_regclass('storage.objects') is null then
    raise notice 'storage schema not present; policy skipped';
    return;
  end if;
  execute $p$drop policy if exists "chat-media: both people can delete" on storage.objects$p$;
  execute $p$create policy "chat-media: both people can delete" on storage.objects
    for delete to authenticated
    using (bucket_id = 'chat-media' and public.can_delete_chat_media(name))$p$;
end;
$$;

revoke all on function private.is_group_member(uuid, uuid) from public, anon, authenticated;
revoke all on function private.share_group(uuid, uuid) from public, anon, authenticated;
revoke all on function private.notify_group(uuid, uuid) from public, anon, authenticated;
revoke all on function private.needs_group_update(uuid[]) from public, anon, authenticated;
revoke all on function public.create_group(uuid[]) from public, anon;
revoke all on function public.add_group_members(uuid, uuid[]) from public, anon;
revoke all on function public.remove_group_member(uuid, uuid) from public, anon;
revoke all on function public.list_my_groups() from public, anon;
revoke all on function public.send_group_message(uuid, uuid, text, jsonb, boolean) from public, anon;
grant execute on function public.create_group(uuid[]) to authenticated;
grant execute on function public.add_group_members(uuid, uuid[]) to authenticated;
grant execute on function public.remove_group_member(uuid, uuid) to authenticated;
grant execute on function public.list_my_groups() to authenticated;
grant execute on function public.send_group_message(uuid, uuid, text, jsonb, boolean) to authenticated;
