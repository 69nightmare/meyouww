-- Shared calendar and profile pictures.
begin;
create extension if not exists pgtap with schema extensions;

select plan(20);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test');
update public.profiles set is_adult = true, birth_year = 1998, username = 'riya', display_name = 'Riya' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.profiles set is_adult = true, birth_year = 1997, username = 'sam', display_name = 'Sam' where id = 'bbbbbbbb-0000-0000-0000-000000000002';
update public.profiles set is_adult = true, birth_year = 1996, username = 'tara', display_name = 'Tara' where id = 'cccccccc-0000-0000-0000-000000000003';
insert into public.devices (id, user_id, installation_id, timezone) values
  ('a1a1a1a1-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'install-riya-01', 'Asia/Kolkata');
insert into public.friendships (user_a, user_b) values ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002');
insert into public.relationship_permissions (owner_id, contact_id) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001');

-- With Supabase storage present, the picture must really exist before it can be used.
do $$
begin
  if to_regclass('storage.objects') is not null then
    execute $q$insert into storage.objects (bucket_id, name)
               values ('avatars', 'aaaaaaaa-0000-0000-0000-000000000001/aaaaaaaa-1111-2222-3333-444444444444.jpg')$q$;
  end if;
end;
$$;

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');

select is(public.create_event('Movie night 🍿', '2026-12-05 19:30', false, 'My place', '🍿', 60,
  array['bbbbbbbb-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000003']::uuid[]) ->> 'invited',
  '1', 'only friends are invited (Tara is skipped)');
select is(jsonb_array_length(public.list_events('2026-12-01', '2026-12-31')), 1, 'Riya sees her event');
select is((public.list_events('2026-12-01', '2026-12-31') -> 0 ->> 'starts_at')::timestamptz,
  '2026-12-05 19:30'::timestamp at time zone 'Asia/Kolkata', '19:30 is in Riya''s zone');
select is(public.list_events('2026-12-01', '2026-12-31') -> 0 ->> 'my_status', 'going', 'the owner is going');
select is(public.list_events('2026-12-01', '2026-12-31') -> 0 -> 'owner' ->> 'display_name', 'Riya', 'it says who marked it');
select is(public.create_event('Birthday', '2026-12-10 00:00', true, null, '🎂', null, '{}') ->> 'status', 'ok', 'all-day events work');
select is(public.list_events('2026-12-10', '2026-12-10') -> 0 ->> 'local_time', null, 'all-day events have no time');
select is(public.create_event('', '2026-12-10 10:00') ->> 'status', 'invalid', 'a title is required');
select throws_ok($$ select count(*) from public.events $$, '42501', null, 'events are not readable directly');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is((select a ->> 'kind' from jsonb_array_elements(public.list_activity()) a limit 1), 'event_invite', 'Sam is invited');
select is((select (a ->> 'actionable')::boolean from jsonb_array_elements(public.list_activity()) a limit 1), true, 'and can answer');
select is(public.list_events('2026-12-01', '2026-12-31') -> 0 ->> 'my_status', 'invited', 'it shows on his calendar as an invitation');
select is(jsonb_array_length(public.list_events('2026-12-01', '2026-12-31')), 1, 'Riya''s private birthday isn''t on Sam''s calendar');
select is(public.respond_event((public.list_events('2026-12-01', '2026-12-31') -> 0 ->> 'id')::uuid, true) ->> 'status', 'ok', 'Sam says yes');

select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(jsonb_array_length(public.list_events('2026-12-01', '2026-12-31')), 0, 'Tara sees nothing');

-- Profile pictures
select is(public.set_avatar('bbbbbbbb-0000-0000-0000-000000000002/aaaaaaaa-1111-2222-3333-444444444444.jpg') ->> 'status',
  'invalid', 'nobody can point their profile at someone else''s folder');
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.set_avatar('aaaaaaaa-0000-0000-0000-000000000001/aaaaaaaa-1111-2222-3333-444444444444.jpg') ->> 'status',
  'ok', 'Riya sets her picture');
select is(public.can_read_avatar('aaaaaaaa-0000-0000-0000-000000000001/x.jpg'), true, 'she can read it');
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.can_read_avatar('aaaaaaaa-0000-0000-0000-000000000001/x.jpg'), true, 'her friend Sam can read it');
select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.can_read_avatar('aaaaaaaa-0000-0000-0000-000000000001/x.jpg'), false, 'Tara, not a friend, can''t');

select * from finish();
rollback;
