-- Phase 2 database tests: friend requests, relationship levels, per-contact permissions,
-- blocks, the remote-actions pause and the activity inbox. Run with: supabase test db
begin;
create extension if not exists pgtap with schema extensions;

select plan(62);

-- People: A (riya), B (sam), C (tara) have finished onboarding; D has not.
insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test'),
  ('dddddddd-0000-0000-0000-000000000004', 'dee@meyouww.test');
update public.profiles set is_adult = true, birth_year = 1998, username = 'riya', display_name = 'Riya'
 where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.profiles set is_adult = true, birth_year = 1997, username = 'sam', display_name = 'Sam'
 where id = 'bbbbbbbb-0000-0000-0000-000000000002';
update public.profiles set is_adult = true, birth_year = 1996, username = 'tara', display_name = 'Tara'
 where id = 'cccccccc-0000-0000-0000-000000000003';
update public.profiles set is_adult = true, birth_year = 1995, username = 'dee'
 where id = 'dddddddd-0000-0000-0000-000000000004';

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

-- ---------------------------------------------------------------------------
-- A finds B and sends a request
-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');

select is(public.lookup_user_id('@Sam') -> 'user' ->> 'username', 'sam', 'lookup is exact-match and normalised');
select is(public.lookup_user_id('sam') -> 'user' ->> 'relation', 'none', 'a stranger shows as not connected');
select ok(public.lookup_user_id('sam') -> 'user' -> 'email' is null, 'lookup never returns an email');
select is(public.lookup_user_id('nobody.here') -> 'user', 'null'::jsonb, 'an unknown ID returns an empty result');
select is(public.lookup_user_id('nobody.here') ->> 'status', 'ok', 'an unknown ID has the same reply shape');
select is(public.lookup_user_id('dee') -> 'user', 'null'::jsonb, 'unfinished profiles cannot be found');
select is(public.lookup_user_id('sa') -> 'user', 'null'::jsonb, 'no prefix search');
select is(public.lookup_user_id('riya') -> 'user' ->> 'relation', 'self', 'looking yourself up says so');

select is(public.send_friend_request('riya') ->> 'status', 'invalid', 'you cannot friend yourself');
select is(public.send_friend_request('sam') ->> 'status', 'pending', 'a request is sent');
select is(public.send_friend_request('sam') ->> 'status', 'pending', 'sending again is harmless');
select is(jsonb_array_length(public.list_friend_requests() -> 'outgoing'), 1, 'one outgoing request is listed');
select throws_ok($$ select * from public.friend_requests $$, '42501', null, 'requests are not readable directly');
select throws_ok($$ insert into public.friendships (user_a, user_b) values (auth.uid(), auth.uid()) $$,
  '42501', null, 'friendships cannot be written directly');
select throws_ok($$ select public.check_dispatch_token('x') $$, '42501', null, 'the dispatch check is server-only');

-- ---------------------------------------------------------------------------
-- B sees it in the heart inbox and declines; A keeps seeing "Pending"
-- ---------------------------------------------------------------------------
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.unread_activity_count(), 1, 'B has one unread inbox item');
select is(public.list_activity() -> 0 ->> 'kind', 'friend_request', 'the inbox shows the friend request');
select is((public.list_activity() -> 0 ->> 'actionable')::boolean, true, 'the request can be answered from the inbox');
select is(public.list_activity() -> 0 -> 'actor' ->> 'username', 'riya', 'the inbox names who asked');
select is(public.lookup_user_id('riya') -> 'user' ->> 'relation', 'incoming', 'B sees an incoming request');
select is(public.respond_friend_request((public.list_activity() -> 0 ->> 'subject_id')::uuid, false) ->> 'status',
  'declined', 'B declines');
select is((public.list_activity() -> 0 ->> 'actionable')::boolean, false, 'a declined request has no buttons left');
select is(public.list_activity() -> 0 ->> 'resolution', 'declined', 'the inbox remembers the answer');
select is(jsonb_array_length(public.list_friend_requests() -> 'incoming'), 0, 'nothing left to answer');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(jsonb_array_length(public.list_friend_requests() -> 'outgoing'), 1, 'A still sees the request as pending');
select is(public.lookup_user_id('sam') -> 'user' ->> 'relation', 'requested', 'A is never told it was declined');
select is(public.send_friend_request('sam') ->> 'status', 'pending', 'asking again does not re-notify');
select is(public.unread_activity_count(), 0, 'A has no notifications from the decline');

-- ---------------------------------------------------------------------------
-- B changes their mind and asks A; A accepts from the inbox
-- ---------------------------------------------------------------------------
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.send_friend_request('riya') ->> 'status', 'pending', 'B sends A a request');
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.respond_friend_request((public.list_activity() -> 0 ->> 'subject_id')::uuid, true) ->> 'status',
  'accepted', 'A accepts');
select is(jsonb_array_length(public.list_friends()), 1, 'A has one friend');
select is(public.list_friends() -> 0 ->> 'level', 'friend', 'new friends start as Friend');
select is(public.list_friends() -> 0 ->> 'they_allow', 'auto', 'by default wallpapers go on automatically');
select is(public.send_friend_request('sam') ->> 'status', 'already_friends', 'no request between friends');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.list_activity() -> 0 ->> 'kind', 'friend_accepted', 'B is told A said yes');

-- ---------------------------------------------------------------------------
-- Relationship upgrade: both must accept, in one step
-- ---------------------------------------------------------------------------
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.send_relationship_invite('bbbbbbbb-0000-0000-0000-000000000002', 'partner', 'Love') ->> 'status',
  'pending', 'A invites B to be partners');
select is(public.list_friends() -> 0 ->> 'level', 'friend', 'nothing changes until B accepts');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.list_activity() -> 0 ->> 'kind', 'relationship_invite', 'B gets the invitation in the inbox');
select is(public.respond_relationship_invite((public.list_activity() -> 0 ->> 'subject_id')::uuid, true) ->> 'status',
  'accepted', 'B accepts');
select is(public.list_friends() -> 0 ->> 'level', 'partner', 'they are partners for B');
select is(public.list_friends() -> 0 ->> 'label', 'Love', 'with the label they chose');

-- ---------------------------------------------------------------------------
-- B's permissions for A
-- ---------------------------------------------------------------------------
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"wallpaper_mode":"auto"}', null) ->> 'status',
  'needs_notifications', 'auto-apply needs a phone that shows notifications');
reset role;
insert into public.devices (id, user_id, installation_id) values
  ('eeeeeeee-0000-0000-0000-000000000005', 'bbbbbbbb-0000-0000-0000-000000000002', 'install-bbbbbbbb');
insert into public.device_state (device_id, notifications_enabled) values ('eeeeeeee-0000-0000-0000-000000000005', true);
set local role authenticated;
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"wallpaper_mode":"auto","quiet_start":"22:00","quiet_end":"07:00"}', 1) ->> 'status',
  'ok', 'B lets A decorate automatically');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"receipts":false}', 1) ->> 'status',
  'conflict', 'a stale version is refused');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"is_admin":true}', null) ->> 'status',
  'invalid', 'unknown settings are refused');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"quiet_start":"25:99"}', null) ->> 'status',
  'invalid', 'bad times are refused');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"wallpaper_daily_limit":50}', null) ->> 'status',
  'invalid', 'the daily limit stays between 1 and 20');
select is((select count(*)::int from public.relationship_permissions where owner_id = 'aaaaaaaa-0000-0000-0000-000000000001'),
  0, 'B cannot read A''s permission settings');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.list_friends() -> 0 ->> 'they_allow', 'auto', 'A sees only the neutral "can decorate automatically"');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.set_remote_pause(true) ->> 'remote_paused', 'true', 'B hits Emergency Pause');
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.list_friends() -> 0 ->> 'they_allow', 'unavailable', 'A just sees "not accepting right now"');
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.set_remote_pause(false) ->> 'remote_paused', 'false', 'B lifts the pause');

-- A steps the relationship down: automatic wallpapers stay, automatic alarms need a closer level.
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.set_relationship_level('bbbbbbbb-0000-0000-0000-000000000002', 'friend', null) ->> 'status',
  'ok', 'either person can downgrade without approval');
select is(public.list_friends() -> 0 ->> 'they_allow', 'auto', 'downgrading keeps automatic wallpapers');
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"alarm_auto":true}', null) ->> 'status',
  'needs_closer_level', 'plain friends cannot get automatic alarms');

-- ---------------------------------------------------------------------------
-- Blocking and removing
-- ---------------------------------------------------------------------------
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.block_user('cccccccc-0000-0000-0000-000000000003') ->> 'status', 'ok', 'A blocks C');
select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.lookup_user_id('riya') -> 'user', 'null'::jsonb, 'a blocked person cannot find A');
select is(public.send_friend_request('riya') ->> 'status', 'not_found', 'a blocked person cannot send A a request');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.remove_friend('bbbbbbbb-0000-0000-0000-000000000002') ->> 'status', 'ok', 'A removes B');
select is(jsonb_array_length(public.list_friends()), 0, 'they are no longer friends');
reset role;
select is((select count(*)::int from public.relationship_permissions
            where owner_id in ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002')),
  0, 'removing a friend deletes the permissions both ways');

set local role anon;
select throws_ok($$ select public.list_friends() $$, '42501', null, 'signed-out callers cannot list friends');

select * from finish();
rollback;
