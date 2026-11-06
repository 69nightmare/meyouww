-- Phase 5: wallpaper commands. Server decides ask/auto, enforces permissions and
-- limits, and only the recipient's real report marks a wallpaper applied.
begin;
create extension if not exists pgtap with schema extensions;

select plan(32);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test');
update public.profiles set is_adult = true, birth_year = 1998, username = 'riya', display_name = 'Riya' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
update public.profiles set is_adult = true, birth_year = 1997, username = 'sam', display_name = 'Sam' where id = 'bbbbbbbb-0000-0000-0000-000000000002';
update public.profiles set is_adult = true, birth_year = 1996, username = 'tara', display_name = 'Tara' where id = 'cccccccc-0000-0000-0000-000000000003';
insert into public.devices (id, user_id, installation_id, timezone) values
  ('eeeeeeee-0000-0000-0000-000000000005', 'bbbbbbbb-0000-0000-0000-000000000002', 'install-sam-0001', 'UTC');
insert into public.device_state (device_id, notifications_enabled) values ('eeeeeeee-0000-0000-0000-000000000005', true);
-- Riya and Sam are partners.
insert into public.friendships (user_a, user_b, level, partner_label)
values ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002', 'partner', 'Love');
insert into public.relationship_permissions (owner_id, contact_id) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;
create function pg_temp.path(n int) returns text language sql as $$
  select 'aaaaaaaa-0000-0000-0000-000000000001/' || lpad(n::text, 8, '0') || '-1111-2222-3333-444444444444.png';
$$;
create function pg_temp.send(n int, target text default 'lock', apply_local timestamp default null) returns jsonb language sql as $$
  select public.create_wallpaper_command('bbbbbbbb-0000-0000-0000-000000000002', target, pg_temp.path(n), 'image/png',
    1080, 2400, 120000, repeat('ab', 32), true, apply_local);
$$;

-- ---------------------------------------------------------------------------
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');

select is(public.create_wallpaper_command('bbbbbbbb-0000-0000-0000-000000000002', 'lock',
  'bbbbbbbb-0000-0000-0000-000000000002/00000000-1111-2222-3333-444444444444.png', 'image/png', 1080, 2400, 1000,
  repeat('ab', 32)) ->> 'status', 'invalid', 'you can only send from your own upload folder');
select is(public.create_wallpaper_command('bbbbbbbb-0000-0000-0000-000000000002', 'sideways', pg_temp.path(1), 'image/png',
  1080, 2400, 1000, repeat('ab', 32)) ->> 'status', 'invalid', 'unknown targets are refused');

-- Default permissions: Sam approves each wallpaper.
select is(pg_temp.send(1) ->> 'status', 'sent', 'a wallpaper is sent');
select is(pg_temp.send(2) ->> 'mode', 'ask', 'by default the recipient is asked');
select throws_ok($$ insert into public.wallpaper_commands (sender_id, recipient_id, target, storage_path, mime, width, height, bytes, sha256, mode, sequence, expires_at)
  values (auth.uid(), auth.uid(), 'lock', 'x/y.png', 'image/png', 1, 1, 1, repeat('a', 64), 'auto', 1, now() + interval '1 day') $$,
  '42501', null, 'commands cannot be written directly');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is((select count(*)::int from public.activity where kind = 'wallpaper_request'), 2, 'Sam has two wallpaper requests in his inbox');
select is((public.list_activity() -> 0 ->> 'actionable')::boolean, true, 'they can be answered from the inbox');
select is(public.get_wallpaper_command((select id from public.wallpaper_commands order by sequence limit 1)) -> 'command' ->> 'mode',
  'ask', 'Sam''s phone fetches the command');
select is((select status from public.wallpaper_commands order by sequence limit 1), 'delivered', 'fetching marks it delivered');
select is(public.report_wallpaper_result((select id from public.wallpaper_commands order by sequence limit 1), 'declined') ->> 'status',
  'ok', 'Sam says not now');
select is(public.report_wallpaper_result((select id from public.wallpaper_commands order by sequence limit 1), 'applied') ->> 'status',
  'gone', 'a finished command cannot be changed');
select is(public.report_wallpaper_result((select id from public.wallpaper_commands order by sequence desc limit 1), 'applied') ->> 'status',
  'ok', 'Sam applies the second one');
select is((select count(*)::int from public.activity where kind = 'wallpaper_received' and read_at is not null), 1,
  'the applied wallpaper is kept in Sam''s inbox without another push');
select is(jsonb_array_length(public.list_received_wallpapers()), 2, 'Sam''s "what happened on my phone" lists both');
select is(public.report_wallpaper_result((select id from public.wallpaper_commands order by sequence desc limit 1), 'hacked') ->> 'status',
  'invalid', 'unknown outcomes are refused');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is((select count(*)::int from public.activity where kind = 'wallpaper_applied'), 1, 'Riya gets the "applied" receipt');
select is((select count(*)::int from public.activity where kind = 'wallpaper_declined'), 1, 'and the "not this time" one');
select is(public.list_sent_wallpapers() -> 0 ->> 'status', 'applied', 'Riya''s sent list shows the real outcome');
select is(public.get_wallpaper_command((select id from public.wallpaper_commands limit 1)) ->> 'status', 'gone',
  'only the recipient can fetch a command');

-- Sam allows automatic wallpapers from Riya.
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.update_contact_permissions('aaaaaaaa-0000-0000-0000-000000000001', '{"wallpaper_mode":"auto","wallpaper_lock":true,"wallpaper_home":false}', null) ->> 'status',
  'ok', 'Sam turns on automatic lock-screen wallpapers for Riya');
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(pg_temp.send(3) ->> 'mode', 'auto', 'now Riya''s wallpapers apply automatically');
select is(pg_temp.send(4, 'home') ->> 'status', 'unavailable', 'home screen is not allowed, and Riya only sees "not accepting"');
select is(pg_temp.send(5, 'lock', (now() at time zone 'UTC' + interval '2 hours')::timestamp) ->> 'status', 'scheduled',
  'a wallpaper can be scheduled in Sam''s time zone');
select is(pg_temp.send(6, 'lock', (now() at time zone 'UTC' - interval '2 hours')::timestamp) ->> 'status', 'invalid',
  'schedules in the past are refused');

-- Quiet hours that cover right now hold the wallpaper.
reset role;
update public.relationship_permissions
   set quiet_start = ((now() at time zone 'UTC') - interval '1 hour')::time,
       quiet_end = ((now() at time zone 'UTC') + interval '1 hour')::time
 where owner_id = 'bbbbbbbb-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(pg_temp.send(7) ->> 'status', 'held', 'during quiet hours it waits');

-- The daily limit (default 5) is reached.
select is(pg_temp.send(8) ->> 'status', 'unavailable', 'over the daily limit Riya just sees "not accepting"');

-- Sam pauses Riya: a pending command is cancelled when his phone fetches it.
reset role;
update public.relationship_permissions set quiet_start = null, quiet_end = null, wallpaper_daily_limit = 20
 where owner_id = 'bbbbbbbb-0000-0000-0000-000000000002';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(pg_temp.send(9) ->> 'status', 'sent', 'another one goes out');
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.set_remote_pause(true) ->> 'status', 'ok', 'Sam hits Emergency Pause');
select is(public.get_wallpaper_command((select id from public.wallpaper_commands where storage_path = pg_temp.path(9))) ->> 'status',
  'cancelled', 'the paused command is cancelled, not applied');

-- Release job: scheduled items whose time has come are sent.
reset role;
update public.user_settings set remote_paused = false where user_id = 'bbbbbbbb-0000-0000-0000-000000000002';
update public.wallpaper_commands set apply_at = now() - interval '1 minute' where status in ('scheduled', 'held');
select is(private.release_due_wallpapers(), 2, 'due scheduled and held wallpapers are released');
select is((select count(*)::int from public.wallpaper_commands where status in ('scheduled', 'held')), 0, 'nothing is left waiting');

set local role authenticated;
select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select is(public.list_sent_wallpapers(), '[]'::jsonb, 'strangers see nothing');

select * from finish();
rollback;
