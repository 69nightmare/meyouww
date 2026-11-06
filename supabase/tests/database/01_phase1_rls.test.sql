-- Phase 1 database tests: RLS on every table, profile and username rules, age gate,
-- device registration privileges. Run with: supabase test db
begin;
create extension if not exists pgtap with schema extensions;

select plan(37);

-- ---------------------------------------------------------------------------
-- Structure
-- ---------------------------------------------------------------------------
select is(
  (select count(*)::int from pg_tables where schemaname = 'public' and not rowsecurity),
  0,
  'RLS is enabled on every table in public'
);
select is(
  (select count(*)::int from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.prosecdef and (p.proconfig is null or not ('search_path=""' = any(p.proconfig)))),
  0,
  'every SECURITY DEFINER function in public pins an empty search_path'
);

-- ---------------------------------------------------------------------------
-- Users (created the way Supabase Auth does it, so the trigger runs under its role)
-- ---------------------------------------------------------------------------
-- Use Supabase Auth's own role when the test user may assume it, to prove sign-up works.
do $$
begin
  begin
    execute 'set local role supabase_auth_admin';
  exception when others then
    null;
  end;
end;
$$;
insert into auth.users (id, email) values
  ('11111111-1111-1111-1111-111111111111', 'a@meyouww.test'),
  ('22222222-2222-2222-2222-222222222222', 'b@meyouww.test');
reset role;

select is(
  (select count(*)::int from public.profiles where id in ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222')),
  2,
  'a profile row is created for each new auth user'
);

-- ---------------------------------------------------------------------------
-- As user A
-- ---------------------------------------------------------------------------
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"11111111-1111-1111-1111-111111111111","role":"authenticated"}', true);

select is((select count(*)::int from public.profiles), 1, 'A sees exactly one profile: their own');
select is((public.claim_username('riya', 'Riya', null)) ->> 'status', 'invalid', 'an ID cannot be claimed before the age gate');
select is((public.complete_age_gate(2015, true)) ->> 'status', 'underage', 'under-18 birth year is rejected');
select is((public.complete_age_gate(1999, false)) ->> 'status', 'invalid', 'adult confirmation is required');
select is((public.complete_age_gate(3000, true)) ->> 'status', 'invalid', 'future birth year is invalid');
select is((select is_adult from public.profiles), false, 'failed attempts do not mark the profile adult');
select is((public.complete_age_gate(1999, true)) ->> 'status', 'ok', 'an adult passes the age gate');
select is((public.complete_age_gate(1980, true)) ->> 'status', 'ok', 'repeating the age gate is harmless');
select is((select birth_year from public.profiles), 1999, 'the first recorded birth year is kept');

select is((public.claim_username('Admin', 'A', null)) ->> 'status', 'reserved', 'reserved IDs are refused');
select is((public.claim_username('ab', 'A', null)) ->> 'status', 'invalid', 'too-short IDs are refused');
select is((public.claim_username('a..b', 'A', null)) ->> 'status', 'invalid', 'consecutive dots are refused');
select is((public.claim_username('abc.', 'A', null)) ->> 'status', 'invalid', 'a trailing dot is refused');
select is((public.claim_username('9lives', 'A', null)) ->> 'status', 'invalid', 'IDs must start with a letter');
select is((public.claim_username('xshitx', 'A', null)) ->> 'status', 'invalid', 'blocked terms are refused');
select is((public.claim_username('riya.cloud', '   ', null)) ->> 'status', 'invalid', 'a display name is required');
select is((public.claim_username('@Riya.Cloud', 'Riya', 'Riyu')) ->> 'status', 'ok', 'a valid ID is claimed, normalised');
select is((select username from public.profiles), 'riya.cloud', 'the stored ID is lowercase without @');
select is((public.claim_username('riya.sky', 'Riya', null)) ->> 'status', 'ok', 'the first change after the initial claim is allowed');
select is((public.claim_username('riya.moon', 'Riya', null)) ->> 'status', 'too_soon', 'a second change within 30 days is refused');

select throws_ok($$ update public.profiles set is_adult = false $$, '42501', null, 'profiles cannot be updated directly');
select throws_ok($$ insert into public.devices (user_id, installation_id) values (auth.uid(), 'install-aaaa') $$, '42501', null, 'devices cannot be inserted directly');
select throws_ok($$ select token from public.push_tokens $$, '42501', null, 'push tokens are never readable by clients');
select throws_ok(
  $$ select public.upsert_device(auth.uid(), 'install-aaaa', repeat('t', 40), 'Google', 36, '1', null) $$,
  '42501', null, 'clients cannot call the device registration function'
);

-- ---------------------------------------------------------------------------
-- As user B
-- ---------------------------------------------------------------------------
select set_config('request.jwt.claims', '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}', true);
select is((public.complete_age_gate(1995, true)) ->> 'status', 'ok', 'B passes the age gate');
select is((public.claim_username('riya.sky', 'B', null)) ->> 'status', 'taken', 'IDs are unique');
select is((public.claim_username('riya.cloud', 'B', null)) ->> 'status', 'taken', 'a released ID stays reserved for its previous owner');
select is(
  (select count(*)::int from public.profiles where id = '11111111-1111-1111-1111-111111111111'),
  0,
  'B cannot read A''s profile'
);

-- ---------------------------------------------------------------------------
-- Anonymous
-- ---------------------------------------------------------------------------
reset role;
set local role anon;
select throws_ok($$ select * from public.profiles $$, '42501', null, 'anonymous visitors cannot read profiles');
select throws_ok($$ select public.complete_age_gate(1990, true) $$, '42501', null, 'anonymous visitors cannot call the age gate');

-- ---------------------------------------------------------------------------
-- Edge Function path (service_role)
-- ---------------------------------------------------------------------------
reset role;
set local role service_role;
select isnt(
  public.upsert_device('11111111-1111-1111-1111-111111111111', 'install-aaaa', repeat('t', 40), 'Google', 36, '0.1.0',
    '{"notifications_enabled": true, "channels": {"delivery_status_v1": 2}, "data_saver": "off", "play_services": "ok"}'::jsonb),
  null,
  'service_role registers a device'
);
select public.upsert_device('22222222-2222-2222-2222-222222222222', 'install-bbbb', repeat('t', 40), 'Xiaomi', 35, '0.1.0', null);
select is((select count(*)::int from public.push_tokens where token = repeat('t', 40)), 1, 'a token moves to the newest device and is never duplicated');
select public.upsert_device('22222222-2222-2222-2222-222222222222', 'install-aaaa', null, 'Google', 36, '0.1.0', null);
select is(
  (select count(*)::int from public.devices where installation_id = 'install-aaaa' and revoked_at is null),
  1,
  'a phone that changes account keeps only the newest registration active'
);
reset role;

-- A reads only their own devices.
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"22222222-2222-2222-2222-222222222222","role":"authenticated"}', true);
select is((select count(*)::int from public.devices), 2, 'B sees only B''s two devices');

select * from finish();
rollback;
