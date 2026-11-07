-- Ephemeral media: finished wallpapers are handed to the sweeper once; only the
-- recipient may delete a picture that reached them; chat envelopes expire in a week.
begin;
create extension if not exists pgtap with schema extensions;

select plan(9);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test');

create function pg_temp.cmd(p_id uuid, p_status text, p_expires interval) returns void language sql as $$
  insert into public.wallpaper_commands (id, sender_id, recipient_id, target, storage_path, mime, width, height,
                                         bytes, sha256, mode, sequence, status, created_at, expires_at)
  values (p_id, 'aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002', 'lock',
          'aaaaaaaa-0000-0000-0000-000000000001/' || p_id || '.webp', 'image/webp', 1080, 2400, 1000,
          repeat('a', 64), 'ask', 1, p_status, now() - interval '2 days', now() + p_expires);
$$;
select pg_temp.cmd('11111111-0000-0000-0000-000000000001', 'declined', interval '1 day');
select pg_temp.cmd('11111111-0000-0000-0000-000000000002', 'awaiting_approval', interval '1 day');
select pg_temp.cmd('11111111-0000-0000-0000-000000000003', 'sent', interval '-1 hour');
select pg_temp.cmd('11111111-0000-0000-0000-000000000004', 'applied', interval '1 day');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

set local role authenticated;
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select throws_ok($$ select public.claim_wallpaper_media_sweep(10) $$, '42501', null, 'people cannot run the sweeper');
select ok(public.can_delete_wallpaper_object('aaaaaaaa-0000-0000-0000-000000000001/11111111-0000-0000-0000-000000000002.webp'),
          'the recipient may delete a picture sent to them');
select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select ok(not public.can_delete_wallpaper_object('aaaaaaaa-0000-0000-0000-000000000001/11111111-0000-0000-0000-000000000002.webp'),
          'nobody else may');
reset role;

set local role service_role;
select is(cardinality(public.claim_wallpaper_media_sweep(10)), 3, 'finished and expired pictures are handed out');
select is(cardinality(public.claim_wallpaper_media_sweep(10)), 0, 'each one only once');
select public.release_wallpaper_media_sweep(array['aaaaaaaa-0000-0000-0000-000000000001/11111111-0000-0000-0000-000000000001.webp']);
select is(public.claim_wallpaper_media_sweep(10), array['aaaaaaaa-0000-0000-0000-000000000001/11111111-0000-0000-0000-000000000001.webp'],
          'a picture that could not be deleted is handed out again');
reset role;
select ok((select media_swept_at is null from public.wallpaper_commands where id = '11111111-0000-0000-0000-000000000002'),
          'a picture still waiting for an answer is kept');

-- Undelivered chat envelopes: gone after 7 days.
select ok(position('7 days' in pg_get_functiondef('private.expire_messages()'::regprocedure)) > 0, 'chat envelopes expire after a week');
select ok(not has_function_privilege('authenticated', 'private.expire_messages()', 'execute'), 'expiry is not callable by people');

select * from finish();
rollback;
