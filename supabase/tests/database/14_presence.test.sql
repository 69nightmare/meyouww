-- Presence: friends see online / last seen / typing / chat open; strangers see nothing;
-- nobody can claim to be in someone else's chat.
begin;
create extension if not exists pgtap with schema extensions;

select plan(8);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test');
insert into public.friendships (user_a, user_b) values ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002');
insert into public.conversations (id, user_a, user_b) values
  ('dddddddd-0000-0000-0000-00000000000d', 'aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.set_presence('dddddddd-0000-0000-0000-00000000000d', true) ->> 'status', 'ok', 'Riya reports she is typing in her chat with Sam');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is((public.peer_presence('aaaaaaaa-0000-0000-0000-000000000001') ->> 'online')::boolean, true, 'Sam sees Riya online');
select is((public.peer_presence('aaaaaaaa-0000-0000-0000-000000000001') ->> 'in_chat')::boolean, true, 'with their chat open');
select is((public.peer_presence('aaaaaaaa-0000-0000-0000-000000000001') ->> 'typing')::boolean, true, 'and typing');
select ok(public.peer_presence('aaaaaaaa-0000-0000-0000-000000000001') ? 'last_seen', 'last seen is there too');

select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.peer_presence('aaaaaaaa-0000-0000-0000-000000000001') ->> 'status', 'not_friends', 'a stranger learns nothing');
select public.set_presence('dddddddd-0000-0000-0000-00000000000d', true);
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.peer_presence('cccccccc-0000-0000-0000-000000000003') ->> 'status', 'not_friends', 'and is not shown to Riya either');

select throws_ok($$ select * from public.presence $$, '42501', null, 'the table is not readable directly');

select * from finish();
rollback;
