-- Three levels: friends suggest; best friends set alarms and reminders directly; a
-- girlfriend / boyfriend also puts wallpapers on directly and can switch the phone's sound.
-- Only one girlfriend / boyfriend each, and both accept.
begin;
create extension if not exists pgtap with schema extensions;

select plan(14);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test');
update public.profiles set is_adult = true, birth_year = 1998, username = 'u' || left(id::text, 4), display_name = 'P' || left(id::text, 4);
insert into public.friendships (user_a, user_b, level) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002', 'friend'),
  ('aaaaaaaa-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000003', 'friend'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000003', 'friend');
insert into public.relationship_permissions (owner_id, contact_id, alarm_daily_max)
select a.id, b.id, 10 from auth.users a cross join auth.users b where a.id <> b.id;

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;
create function pg_temp.tomorrow(p text) returns timestamp language sql as $$
  select ((now() at time zone 'UTC')::date + 1 + p::time)::timestamp;
$$;

set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
-- Friend level: everything is a suggestion.
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Wake up', null, pg_temp.tomorrow('07:00')) ->> 'status',
  'sent', 'a friend''s alarm waits for a yes');
select is(public.create_event('Movie', pg_temp.tomorrow('19:00'), false, null, null, null,
  array['bbbbbbbb-0000-0000-0000-000000000002']::uuid[]) ->> 'status', 'ok', 'a friend can suggest a reminder');
select is(public.set_ringer('bbbbbbbb-0000-0000-0000-000000000002', 'vibrate') ->> 'status',
  'needs_closer_level', 'a friend cannot touch the phone''s sound');

-- Best friends: reminders and alarms go straight on; wallpapers still ask.
reset role;
update public.friendships set level = 'best_friend' where user_b = 'bbbbbbbb-0000-0000-0000-000000000002' and user_a = 'aaaaaaaa-0000-0000-0000-000000000001';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select public.create_event('Dinner', pg_temp.tomorrow('20:00'), false, null, null, null, array['bbbbbbbb-0000-0000-0000-000000000002']::uuid[]);
reset role;
select is((select p.status from public.event_participants p join public.events e on e.id = p.event_id
            where e.title = 'Dinner' and p.user_id = 'bbbbbbbb-0000-0000-0000-000000000002'), 'going', 'a best friend''s reminder is added directly');
select is((select p.status from public.event_participants p join public.events e on e.id = p.event_id
            where e.title = 'Movie' and p.user_id = 'bbbbbbbb-0000-0000-0000-000000000002'), 'invited', 'a friend''s was only a suggestion');
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Gym', null, pg_temp.tomorrow('08:00')) ->> 'status',
  'added', 'a best friend''s alarm is set straight away (with Remove)');

-- Girlfriend / boyfriend: one at a time, both accept.
select is(public.send_relationship_invite('bbbbbbbb-0000-0000-0000-000000000002', 'partner', 'Boyfriend') ->> 'status', 'pending', 'Riya asks Sam');
select is(public.send_relationship_invite('cccccccc-0000-0000-0000-000000000003', 'partner', 'Boyfriend') ->> 'status',
  'already_asked', 'she can''t ask a second person at the same time');
reset role;
create temp table partner_ask as select id from public.relationship_requests where level = 'partner';
grant select on partner_ask to authenticated;
set local role authenticated;
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.respond_relationship_invite((select id from partner_ask limit 1), true) ->> 'status',
  'accepted', 'Sam says yes');
select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.send_relationship_invite('aaaaaaaa-0000-0000-0000-000000000001', 'partner', null) ->> 'status',
  'unavailable', 'nobody else can ask Riya now');
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.send_relationship_invite('cccccccc-0000-0000-0000-000000000003', 'partner', null) ->> 'status',
  'already_partnered', 'and Riya can''t ask anyone else');
select is(public.create_alarm('bbbbbbbb-0000-0000-0000-000000000002', 'Call me', null, pg_temp.tomorrow('09:00')) ->> 'status',
  'added', 'her alarm is set on his phone straight away');
select is(public.set_ringer('bbbbbbbb-0000-0000-0000-000000000002', 'mute') ->> 'status', 'ok', 'she can switch his phone to mute');
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"ringer_allowed": false}', null);
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.set_ringer('bbbbbbbb-0000-0000-0000-000000000002', 'ring') ->> 'status', 'unavailable', 'unless he turns that off');

select * from finish();
rollback;
