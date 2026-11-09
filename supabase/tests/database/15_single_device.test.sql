-- One phone at a time: claiming retires the other phones; a replaced phone stays retired
-- when it registers again, until it signs in again itself.
begin;
create extension if not exists pgtap with schema extensions;

select plan(10);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test');
insert into public.devices (id, user_id, installation_id) values
  ('a1a1a1a1-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'install-riya-old'),
  ('a2a2a2a2-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'install-riya-new');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.claim_only_device('install-riya-new') ->> 'signed_out', '1', 'signing in on the new phone signs the old one out');
select is((public.device_active('install-riya-old') ->> 'active')::boolean, false, 'the old phone is no longer active');
select is((public.device_active('install-riya-old') ->> 'replaced')::boolean, true, 'and knows it was replaced');
select is((public.device_active('install-riya-new') ->> 'active')::boolean, true, 'the new phone is active');

reset role;
select public.upsert_device('aaaaaaaa-0000-0000-0000-000000000001', 'install-riya-old', null, null, null, '0.16.0', null);
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is((public.device_active('install-riya-old') ->> 'active')::boolean, false, 'registering again on start does not bring it back');

select is(public.claim_only_device('install-riya-old') ->> 'status', 'ok', 'signing in on the old phone again claims it');
select is((public.device_active('install-riya-old') ->> 'active')::boolean, true, 'so it is active again');
select is((public.device_active('install-riya-new') ->> 'replaced')::boolean, true, 'and the other phone is the replaced one now');
select is(public.claim_only_device('install-unknown') ->> 'status', 'not_found', 'an unregistered phone cannot claim');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.device_active('install-riya-old') ->> 'status', 'not_found', 'nobody can look at someone else''s phones');

select * from finish();
rollback;
