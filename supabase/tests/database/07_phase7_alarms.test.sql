-- Phase 7: shared alarms follow the recipient's consent rules, land in the right
-- time zone, and only the two people involved can see them.
begin;
create extension if not exists pgtap with schema extensions;

select plan(22);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test');
update public.profiles set is_adult = true, birth_year = 1998, username = 'riya', display_name = 'Riya' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.profiles set is_adult = true, birth_year = 1997, username = 'sam', display_name = 'Sam' where id = 'bbbbbbbb-0000-0000-0000-000000000002';
update public.profiles set is_adult = true, birth_year = 1996, username = 'tara', display_name = 'Tara' where id = 'cccccccc-0000-0000-0000-000000000003';
insert into public.devices (id, user_id, installation_id, timezone) values
  ('b1b1b1b1-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-000000000002', 'install-sam-001', 'Asia/Kolkata');
insert into public.friendships (user_a, user_b) values ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002');
insert into public.relationship_permissions (owner_id, contact_id) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;
-- Tomorrow at a wall-clock time, in Sam's zone.
create function pg_temp.tomorrow(t time) returns timestamp language sql as $$
  select ((now() at time zone 'Asia/Kolkata')::date + 1) + t;
$$;

set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');

select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Wake up!', 'Exam day 🌞', pg_temp.tomorrow('07:00')) ->> 'status',
  'sent', 'a plain friend''s alarm becomes a request');
select is((public.list_sent_alarms() -> 0 ->> 'ring_at')::timestamptz, pg_temp.tomorrow('07:00') at time zone 'Asia/Kolkata',
  '7:00 means 7:00 in Sam''s time zone');
select is(public.list_sent_alarms() -> 0 ->> 'time_zone', 'Asia/Kolkata', 'the zone is recorded');
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Again', null, pg_temp.tomorrow('08:00')) ->> 'status',
  'limit', 'Sam''s daily maximum (1 by default) applies');
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Past', null, '2001-01-01 07:00') ->> 'status',
  'in_past', 'times in the past are refused');
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', '', null, pg_temp.tomorrow('07:00')) ->> 'status',
  'invalid', 'a title is required');
select throws_ok($$ select count(*) from public.alarms $$, '42501', null, 'alarms are not readable directly');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is((select a ->> 'kind' from jsonb_array_elements(public.list_activity()) a limit 1), 'alarm_request', 'Sam gets a request in his inbox');
select is((select (a ->> 'actionable')::boolean from jsonb_array_elements(public.list_activity()) a limit 1), true, 'which he can answer');
select is(public.list_my_alarms() -> 0 ->> 'status', 'pending', 'it waits for his answer');
select is(public.respond_alarm((public.list_my_alarms() -> 0 ->> 'id')::uuid, true) ->> 'status', 'ok', 'Sam accepts');
select is(public.list_my_alarms() -> 0 ->> 'status', 'accepted', 'and it is on his phone''s list');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is((select a ->> 'kind' from jsonb_array_elements(public.list_activity()) a limit 1), 'alarm_accepted', 'Riya hears that he accepted');

-- Sam lets Riya, now his best friend, add alarms automatically between 6 and 9 in the morning.
reset role;
update public.friendships set level = 'best_friend';
update public.relationship_permissions set alarm_auto = true, alarm_daily_max = 5,
       alarm_window_start = '06:00', alarm_window_end = '09:00'
 where owner_id = 'bbbbbbbb-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Gym', null, pg_temp.tomorrow('06:30'), 31) ->> 'status',
  'added', 'inside Sam''s window it is added straight away');
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Midnight', null, pg_temp.tomorrow('23:30')) ->> 'status',
  'sent', 'outside it, Sam is asked first');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is((select a ->> 'kind' from jsonb_array_elements(public.list_activity()) a where a ->> 'kind' = 'alarm_added' limit 1),
  'alarm_added', 'an automatic alarm is never silent');
select is((select count(*)::int from jsonb_array_elements(public.list_my_alarms()) a where (a ->> 'repeat_days')::int = 31), 1,
  'repeat days (Mon-Fri) are kept');

-- Riya takes the gym alarm back.
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.cancel_alarm((select (a ->> 'id')::uuid from jsonb_array_elements(public.list_sent_alarms()) a where a ->> 'title' = 'Gym')) ->> 'status',
  'ok', 'the sender can cancel');
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is((select count(*)::int from jsonb_array_elements(public.list_my_alarms()) a where a ->> 'title' = 'Gym'), 0,
  'and it leaves Sam''s phone');

-- Sam switches alarm requests off for Riya.
reset role;
update public.relationship_permissions set alarm_requests = false where owner_id = 'bbbbbbbb-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Hi', null, pg_temp.tomorrow('07:00')) ->> 'status',
  'unavailable', 'requests off means not accepting right now');
select is(public.create_alarm('aaaaaaaa-0000-0000-0000-000000000001', 'My own', null, pg_temp.tomorrow('07:00')) ->> 'status',
  'added', 'anyone can set an alarm for themselves');

select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Hey', null, pg_temp.tomorrow('07:00')) ->> 'status',
  'not_friends', 'strangers can''t set alarms');

select * from finish();
rollback;
