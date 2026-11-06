-- Phase 4: device profiles are private unless shared per friend.
begin;
create extension if not exists pgtap with schema extensions;

select plan(12);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test');
update public.profiles set is_adult = true, birth_year = 1998, username = 'riya', display_name = 'Riya' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.profiles set is_adult = true, birth_year = 1997, username = 'sam', display_name = 'Sam' where id = 'bbbbbbbb-0000-0000-0000-000000000002';
update public.profiles set is_adult = true, birth_year = 1996, username = 'tara', display_name = 'Tara' where id = 'cccccccc-0000-0000-0000-000000000003';
insert into public.devices (id, user_id, installation_id) values
  ('eeeeeeee-0000-0000-0000-000000000005', 'bbbbbbbb-0000-0000-0000-000000000002', 'install-sam-0001');
-- Riya and Sam are friends; Tara is not.
insert into public.friendships (user_a, user_b) values ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002');
insert into public.relationship_permissions (owner_id, contact_id) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

set local role authenticated;
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.upsert_device_profile('install-sam-0001',
  '{"width_px":1080,"height_px":2400,"density_dpi":420,"status_bar_px":96,"nav_bar_px":63,"corner_radius_px":110,
    "sdk_int":36,"timezone":"Asia/Kolkata","live_wallpaper":true,"displays":[]}') ->> 'status', 'ok', 'Sam saves his phone''s profile');
select is(public.upsert_device_profile('install-not-mine', '{"width_px":1080,"height_px":2400,"density_dpi":420}') ->> 'status',
  'not_found', 'only your own installation can be profiled');
select is(public.upsert_device_profile('install-sam-0001', '{"width_px":5,"height_px":2400,"density_dpi":420}') ->> 'status',
  'invalid', 'nonsense sizes are refused');
select is(public.upsert_device_profile('install-sam-0001', '{"width_px":1080,"height_px":2400,"density_dpi":420,"displays":[{"w":1,"h":2,"dpi":3}]}') ->> 'status',
  'invalid', 'bad foldable displays are refused');
select is((select count(*)::int from public.device_profiles), 1, 'Sam can read his own profile');
select throws_ok($$ insert into public.device_profiles (device_id, user_id, width_px, height_px, density_dpi)
  values ('eeeeeeee-0000-0000-0000-000000000005', auth.uid(), 1, 1, 1) $$, '42501', null, 'profiles are not writable directly');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is((select count(*)::int from public.device_profiles), 0, 'Riya cannot read Sam''s profile directly');
select is(public.list_contact_devices('bbbbbbbb-0000-0000-0000-000000000002'), '[]'::jsonb, 'nothing is shared by default');

reset role;
update public.relationship_permissions set share_screen_specs = true
 where owner_id = 'bbbbbbbb-0000-0000-0000-000000000002' and contact_id = 'aaaaaaaa-0000-0000-0000-000000000001';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is((public.list_contact_devices('bbbbbbbb-0000-0000-0000-000000000002') -> 0 ->> 'height_px')::int, 2400,
  'after Sam shares specs, Riya sees his screen size');
select ok(public.list_contact_devices('bbbbbbbb-0000-0000-0000-000000000002') -> 0 -> 'timezone' is null,
  'the time zone is never shared');

select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.list_contact_devices('bbbbbbbb-0000-0000-0000-000000000002'), '[]'::jsonb, 'strangers see nothing');

reset role;
select is((select timezone from public.devices where id = 'eeeeeeee-0000-0000-0000-000000000005'), 'Asia/Kolkata',
  'the server keeps the time zone for scheduling');

select * from finish();
rollback;
