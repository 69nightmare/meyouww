-- Phase 6: keys and ciphertext only; one-time prekeys are used once; only friends
-- can fetch bundles or message; delivered ciphertext is deleted.
begin;
create extension if not exists pgtap with schema extensions;

select plan(26);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test');
update public.profiles set is_adult = true, birth_year = 1998, username = 'riya', display_name = 'Riya' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.profiles set is_adult = true, birth_year = 1997, username = 'sam', display_name = 'Sam' where id = 'bbbbbbbb-0000-0000-0000-000000000002';
update public.profiles set is_adult = true, birth_year = 1996, username = 'tara', display_name = 'Tara' where id = 'cccccccc-0000-0000-0000-000000000003';
insert into public.devices (id, user_id, installation_id) values
  ('a1a1a1a1-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'install-riya-01'),
  ('b1b1b1b1-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000002', 'install-sam-001'),
  ('c1c1c1c1-0000-0000-0000-000000000003', 'cccccccc-0000-0000-0000-000000000003', 'install-tara-01'),
  ('b2b2b2b2-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000002', 'install-sam-002');
insert into public.friendships (user_a, user_b) values ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;
create function pg_temp.keys(n int) returns jsonb language sql as $$
  select jsonb_build_object(
    'registration_id', 1234, 'identity_key', repeat('I', 44),
    'signed_prekey_id', 7, 'signed_prekey', repeat('S', 44), 'signed_prekey_sig', repeat('s', 88),
    'kyber_last_resort_id', 99, 'kyber_last_resort', repeat('K', 2092), 'kyber_last_resort_sig', repeat('k', 88),
    'prekeys', (select jsonb_agg(jsonb_build_object('id', i, 'key', repeat('P', 44))) from generate_series(1, n) i),
    'kyber_prekeys', (select jsonb_agg(jsonb_build_object('id', 100 + i, 'key', repeat('Q', 2092), 'sig', repeat('q', 88))) from generate_series(1, 1) i));
$$;

set local role authenticated;
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.publish_keys('install-sam-001', pg_temp.keys(2)) ->> 'status', 'ok', 'Sam publishes his keys');
select is((public.prekey_counts('install-sam-001') ->> 'prekeys')::int, 2, 'two one-time prekeys are waiting');
select is(public.publish_keys('install-riya-01', pg_temp.keys(1)) ->> 'status', 'not_found', 'nobody publishes for someone else''s phone');
select is(public.publish_keys('install-sam-001', '{"identity_key":"tiny"}') ->> 'status', 'invalid', 'malformed keys are refused');
select throws_ok($$ select * from public.one_time_prekeys $$, '42501', null, 'prekeys are not readable directly');
select throws_ok($$ select * from public.encrypted_messages $$, '42501', null, 'messages are not readable directly');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.publish_keys('install-riya-01', pg_temp.keys(1)) ->> 'status', 'ok', 'Riya publishes too');
select is(jsonb_array_length(public.claim_bundles('bbbbbbbb-0000-0000-0000-000000000002') -> 'bundles'), 1, 'Riya gets a bundle for Sam''s phone');
select is((public.claim_bundles('bbbbbbbb-0000-0000-0000-000000000002') -> 'bundles' -> 0 ->> 'prekey_id')::int, 2, 'each claim takes a fresh one-time prekey');
select ok(public.claim_bundles('bbbbbbbb-0000-0000-0000-000000000002') -> 'bundles' -> 0 -> 'prekey' = 'null'::jsonb, 'when they run out there is no one-time prekey');
select is((public.claim_bundles('bbbbbbbb-0000-0000-0000-000000000002') -> 'bundles' -> 0 ->> 'kyber_id')::int, 99, 'and the Kyber last-resort key is used');

select is(public.open_conversation('bbbbbbbb-0000-0000-0000-000000000002') ->> 'status', 'ok', 'Riya opens a chat with Sam');
select is(public.send_message(
  (public.open_conversation('bbbbbbbb-0000-0000-0000-000000000002') ->> 'conversation_id')::uuid,
  'dddddddd-0000-0000-0000-000000000001', 'install-riya-01',
  '[{"device_id":"b1b1b1b1-0000-0000-0000-000000000002","type":3,"body":"Y2lwaGVydGV4dA=="}]') ->> 'seq', '1', 'the first message gets sequence 1');
select is(public.send_message(
  (public.open_conversation('bbbbbbbb-0000-0000-0000-000000000002') ->> 'conversation_id')::uuid,
  'dddddddd-0000-0000-0000-000000000001', 'install-riya-01',
  '[{"device_id":"b1b1b1b1-0000-0000-0000-000000000002","type":3,"body":"Y2lwaGVydGV4dA=="}]') ->> 'seq', '1', 'sending the same message again is harmless');
select is(public.send_message(
  (public.open_conversation('bbbbbbbb-0000-0000-0000-000000000002') ->> 'conversation_id')::uuid,
  'dddddddd-0000-0000-0000-000000000002', 'install-riya-01',
  '[{"device_id":"c1c1c1c1-0000-0000-0000-000000000003","type":2,"body":"eA=="}]') ->> 'status', 'stale_devices', 'envelopes can only go to the other person''s phones');

-- Sam sets up a second phone: a message for only his first phone is now refused.
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select public.publish_keys('install-sam-002', pg_temp.keys(1));
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.send_message(
  (public.open_conversation('bbbbbbbb-0000-0000-0000-000000000002') ->> 'conversation_id')::uuid,
  'dddddddd-0000-0000-0000-000000000003', 'install-riya-01',
  '[{"device_id":"b1b1b1b1-0000-0000-0000-000000000002","type":2,"body":"eA=="}]') ->> 'status', 'stale_devices',
  'every one of Sam''s phones must get a copy');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(jsonb_array_length(public.fetch_messages('install-sam-001')), 1, 'Sam''s phone has one envelope');
select is(public.fetch_messages('install-sam-001') -> 0 ->> 'sender_device_id', 'a1a1a1a1-0000-0000-0000-000000000001',
  'it says which of Riya''s phones sent it');
select is(public.fetch_messages('install-sam-001') -> 0 ->> 'body', 'Y2lwaGVydGV4dA==', 'only ciphertext is stored');
select is(public.ack_messages('install-sam-001', array[(public.fetch_messages('install-sam-001') -> 0 ->> 'id')::bigint]), 1, 'Sam acknowledges it');
select is(public.fetch_messages('install-sam-001'), '[]'::jsonb, 'and the server forgets it');

select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.claim_bundles('bbbbbbbb-0000-0000-0000-000000000002') ->> 'status', 'not_friends', 'strangers cannot fetch Sam''s keys');
select is(public.open_conversation('bbbbbbbb-0000-0000-0000-000000000002') ->> 'status', 'not_friends', 'or start a chat');
select is(public.fetch_messages('install-sam-001'), '[]'::jsonb, 'or read his phone''s messages');

-- Receipts and reactions ask for a push without a visible notification; old apps omit it.
select has_function('public', 'send_message', array['uuid', 'uuid', 'text', 'jsonb', 'boolean'], 'send_message takes p_silent');
select hasnt_function('public', 'send_message', array['uuid', 'uuid', 'text', 'jsonb'], 'with no ambiguous four-argument overload left');

select * from finish();
rollback;
