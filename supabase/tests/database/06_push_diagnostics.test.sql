-- Push diagnostics: each person sees only their own push results and registration.
begin;
create extension if not exists pgtap with schema extensions;

select plan(12);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test');
insert into public.devices (id, user_id, installation_id) values
  ('eeeeeeee-0000-0000-0000-000000000005', 'bbbbbbbb-0000-0000-0000-000000000002', 'install-sam-0001');
insert into public.push_tokens (device_id, token) values ('eeeeeeee-0000-0000-0000-000000000005', 'token-for-sams-phone-000000');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

select ok(has_table_privilege('service_role', 'public.push_tokens', 'select') and
          has_table_privilege('service_role', 'public.devices', 'select') and
          has_table_privilege('service_role', 'public.delivery_tests', 'insert'),
  'Edge Functions can read the device tables they need');

-- push-dispatch (service role) records two results for Sam and one for Riya.
set local role service_role;
select lives_ok($$ select public.record_push_result('bbbbbbbb-0000-0000-0000-000000000002', 'activity', 'friends_v1', 1, 1, 1, null) $$,
  'the dispatcher can record a result');
select public.record_push_result('bbbbbbbb-0000-0000-0000-000000000002', 'wallpaper', 'wallpapers_v1', 1, 1, 0, '403 SENDER_ID_MISMATCH');
select public.record_push_result('aaaaaaaa-0000-0000-0000-000000000001', 'activity', 'friends_v1', 0, 0, 0, 'no_devices');
select lives_ok($$ select public.record_push_result('dddddddd-0000-0000-0000-000000000009', 'activity', 'friends_v1', 0, 0, 0, null) $$,
  'a result for a deleted account is dropped quietly');

set local role authenticated;
select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select throws_ok($$ select public.record_push_result(auth.uid(), 'activity', 'friends_v1', 1, 1, 1, null) $$, '42501', null,
  'people cannot write push results');
select throws_ok($$ select count(*) from private.push_log $$, '42501', null, 'the log is not readable directly');
select is((public.my_push_status('install-sam-0001') ->> 'device_registered')::boolean, true, 'Sam''s phone is registered');
select is((public.my_push_status('install-sam-0001') ->> 'has_push_token')::boolean, true, 'and has a push token');
select is(jsonb_array_length(public.my_push_status('install-sam-0001') -> 'recent'), 2, 'Sam sees his two results');
select is(public.my_push_status('install-sam-0001') -> 'recent' -> 0 ->> 'error', '403 SENDER_ID_MISMATCH',
  'newest first, with the FCM error code');
select is((public.my_push_status('someone-elses-install') ->> 'device_registered')::boolean, false,
  'an unknown installation is reported as not registered');
select ok(public.my_push_status('install-sam-0001')::text !~ 'token-for-sams-phone', 'tokens never appear in the status');

select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.my_push_status('install-sam-0001') -> 'recent' -> 0 ->> 'error', 'no_devices',
  'Riya sees only her own results, and Sam''s phone is not hers');

select * from finish();
rollback;
