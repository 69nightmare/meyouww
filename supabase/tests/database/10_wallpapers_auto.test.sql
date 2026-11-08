-- The sender picks the screen. Friends ask first; a girlfriend / boyfriend's wallpaper
-- goes on by itself unless the recipient chose otherwise.
begin;
create extension if not exists pgtap with schema extensions;

select plan(8);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test');
update public.profiles set is_adult = true, birth_year = 1998, username = 'riya', display_name = 'Riya' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.profiles set is_adult = true, birth_year = 1997, username = 'sam', display_name = 'Sam' where id = 'bbbbbbbb-0000-0000-0000-000000000002';
insert into public.devices (id, user_id, installation_id, timezone) values
  ('eeeeeeee-0000-0000-0000-000000000005', 'bbbbbbbb-0000-0000-0000-000000000002', 'install-sam-0001', 'UTC');
insert into public.device_state (device_id, notifications_enabled) values ('eeeeeeee-0000-0000-0000-000000000005', true);
-- Plain friends, default permissions.
insert into public.friendships (user_a, user_b) values ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002');
insert into public.relationship_permissions (owner_id, contact_id) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

select is((select wallpaper_mode from public.relationship_permissions where owner_id = 'bbbbbbbb-0000-0000-0000-000000000002'),
  'auto', 'new friendships put wallpapers on automatically');
select is(private.capability('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002'),
  'ask', 'but plain friends ask first');

update public.friendships set level = 'partner';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.create_wallpaper_command('bbbbbbbb-0000-0000-0000-000000000002', 'both',
  'aaaaaaaa-0000-0000-0000-000000000001/00000001-1111-2222-3333-444444444444.webp', 'image/webp',
  1080, 2400, 120000, repeat('ab', 32), true, null) ->> 'mode', 'auto', 'a girlfriend''s wallpaper goes on by itself');
select is((select target from public.wallpaper_commands limit 1), 'both', 'on the screens Riya picked');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"wallpaper_mode":"ask"}', null) ->> 'status',
  'ok', 'Sam can still choose to be asked first');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"receipts":false}', null) ->> 'status',
  'ok', 'other settings save without touching the wallpaper mode');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"wallpaper_mode":"auto"}', null) ->> 'status',
  'ok', 'and switch back to automatic');
reset role;
update public.friendships set level = 'best_friend';
set local role authenticated;
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"wallpaper_mode":"auto"}', null) ->> 'status',
  'needs_closer_level', 'best friends ask first too');

select * from finish();
rollback;
