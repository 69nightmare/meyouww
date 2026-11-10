-- Group chats: created with friends; members can encrypt to each other; a message must go
-- to every member's phone; admins manage members; anyone can leave; files stay in the group.
begin;
create extension if not exists pgtap with schema extensions;

select plan(20);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test'),
  ('dddddddd-0000-0000-0000-000000000004', 'uma@meyouww.test');
insert into public.devices (id, user_id, installation_id) values
  ('a1a1a1a1-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'install-riya-01'),
  ('b1b1b1b1-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000002', 'install-sam-001'),
  ('c1c1c1c1-0000-0000-0000-000000000003', 'cccccccc-0000-0000-0000-000000000003', 'install-tara-01'),
  ('d1d1d1d1-0000-0000-0000-000000000004', 'dddddddd-0000-0000-0000-000000000004', 'install-uma-001');
-- Riya is friends with Sam and Tara; Sam and Tara don't know each other; Uma knows nobody.
insert into public.friendships (user_a, user_b) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002'),
  ('aaaaaaaa-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000003');

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
create function pg_temp.g() returns uuid language sql as $$ select current_setting('test.group')::uuid $$;

set local role authenticated;
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select public.publish_keys('install-sam-001', pg_temp.keys(2));
select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select public.publish_keys('install-tara-01', pg_temp.keys(2));
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select public.publish_keys('install-riya-01', pg_temp.keys(2));

select is(public.create_group(array['dddddddd-0000-0000-0000-000000000004']::uuid[]) ->> 'status', 'not_friends',
  'a group can only start with friends');
reset role;
update public.devices set app_version = '0.17.0' where id = 'b1b1b1b1-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.create_group(array['bbbbbbbb-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000003']::uuid[]) ->> 'status',
  'needs_update', 'a friend on a version without groups is asked to update first');
reset role;
update public.devices set app_version = '0.18.0' where id = 'b1b1b1b1-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select set_config('test.group',
  public.create_group(array['bbbbbbbb-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000003']::uuid[]) ->> 'group_id', true);
select isnt(current_setting('test.group', true), null, 'Riya makes a group with Sam and Tara');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(jsonb_array_length(public.list_my_groups() -> 0 -> 'members'), 3, 'Sam sees the group with three people');
select is(public.claim_bundles('cccccccc-0000-0000-0000-000000000003') ->> 'status', 'ok',
  'Sam can encrypt to Tara because they share a group');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.send_group_message(pg_temp.g(), 'eeeeeeee-0000-0000-0000-000000000001', 'install-riya-01',
  '[{"device_id":"b1b1b1b1-0000-0000-0000-000000000002","type":3,"body":"eA=="}]') ->> 'status', 'stale_devices',
  'a group message must reach every member''s phone');
select is(public.send_group_message(pg_temp.g(), 'eeeeeeee-0000-0000-0000-000000000001', 'install-riya-01',
  '[{"device_id":"b1b1b1b1-0000-0000-0000-000000000002","type":3,"body":"eA=="},
    {"device_id":"c1c1c1c1-0000-0000-0000-000000000003","type":3,"body":"eQ=="}]') ->> 'seq', '1',
  'with a copy for Sam and Tara it goes through');

select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.fetch_messages('install-tara-01') -> 0 ->> 'group_id', current_setting('test.group'),
  'Tara''s phone gets it, marked with the group');
select ok(public.can_use_chat_media('aaaaaaaa-0000-0000-0000-000000000001/' || current_setting('test.group') || '/m1'),
  'Tara can open files Riya sent to the group');
select ok(not public.can_delete_chat_media('aaaaaaaa-0000-0000-0000-000000000001/' || current_setting('test.group') || '/m1'),
  'but not delete them: the others still need the file');
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select ok(public.can_delete_chat_media('aaaaaaaa-0000-0000-0000-000000000001/' || current_setting('test.group') || '/m1'),
  'Riya can delete her own');

select pg_temp.as_user('dddddddd-0000-0000-0000-000000000004');
select ok(not public.can_use_chat_media('aaaaaaaa-0000-0000-0000-000000000001/' || current_setting('test.group') || '/m1'),
  'Uma, outside the group, cannot');
select is(jsonb_array_length(public.list_my_groups()), 0, 'and does not see the group');

-- Sam blocks Tara: he can't encrypt to her any more, so his messages go to the rest only.
reset role;
insert into public.user_blocks (blocker_id, blocked_id)
  values ('bbbbbbbb-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000003');
set local role authenticated;
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.send_group_message(pg_temp.g(), 'eeeeeeee-0000-0000-0000-000000000003', 'install-sam-001',
  '[{"device_id":"a1a1a1a1-0000-0000-0000-000000000001","type":3,"body":"eA=="}]') ->> 'status', 'ok',
  'a member who blocked someone still writes to the rest of the group');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.remove_group_member(pg_temp.g(), 'cccccccc-0000-0000-0000-000000000003') ->> 'status', 'not_admin',
  'only an admin removes people');
select is(public.remove_group_member(pg_temp.g(), 'bbbbbbbb-0000-0000-0000-000000000002') ->> 'status', 'ok', 'anyone can leave');
select is(public.send_group_message(pg_temp.g(), 'eeeeeeee-0000-0000-0000-000000000002', 'install-sam-001',
  '[{"device_id":"c1c1c1c1-0000-0000-0000-000000000003","type":3,"body":"eA=="}]') ->> 'status', 'not_member',
  'after leaving, Sam cannot write to it');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.add_group_members(pg_temp.g(), array['dddddddd-0000-0000-0000-000000000004']::uuid[]) ->> 'status', 'not_friends',
  'admins add only their own friends');
select is(public.remove_group_member(pg_temp.g(), 'aaaaaaaa-0000-0000-0000-000000000001') ->> 'status', 'ok', 'the admin leaves too');

select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.list_my_groups() -> 0 -> 'members' -> 0 ->> 'role', 'admin', 'so the longest-standing member becomes admin');

select * from finish();
rollback;
