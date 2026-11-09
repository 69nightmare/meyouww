-- Pushes from phones on versions before 0.15.0 (which never mark receipts silent) go data-only.
begin;
create extension if not exists pgtap with schema extensions;

select plan(5);

insert into auth.users (id, email) values ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test');
insert into public.devices (id, user_id, installation_id, app_version) values
  ('a1a1a1a1-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'install-old-013', '0.13.0'),
  ('a2a2a2a2-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'install-new-017', '0.17.0'),
  ('a3a3a3a3-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000001', 'install-dev-015', '0.15.0-debug'),
  ('a4a4a4a4-0000-0000-0000-000000000004', 'aaaaaaaa-0000-0000-0000-000000000001', 'install-unknown', null);

select ok(private.device_before('a1a1a1a1-0000-0000-0000-000000000001', array[0, 15, 0]), 'a v0.13 phone is older than 0.15.0');
select ok(not private.device_before('a2a2a2a2-0000-0000-0000-000000000002', array[0, 15, 0]), 'a v0.17 phone is not');
select ok(not private.device_before('a3a3a3a3-0000-0000-0000-000000000003', array[0, 15, 0]), 'a 0.15.0 test build is not');
select ok(not private.device_before('a4a4a4a4-0000-0000-0000-000000000004', array[0, 15, 0]), 'an unknown version counts as current');
select ok(not private.device_before('a1a1a1a1-0000-0000-0000-000000000001', array[0, 9, 0]), 'versions compare as numbers, not text');

select * from finish();
rollback;
