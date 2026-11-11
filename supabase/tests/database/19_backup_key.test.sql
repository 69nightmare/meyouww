-- The chat backup key: one per person, the same every time, never anyone else's.
begin;
create extension if not exists pgtap with schema extensions;

select plan(6);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.backup_key() ->> 'status', 'ok', 'Riya gets a backup key');
select is(octet_length(decode(public.backup_key() ->> 'key', 'base64')), 32, 'it is 32 bytes');
select is(public.backup_key() ->> 'key', public.backup_key() ->> 'key', 'and the same one every time');
select set_config('test.riya_key', public.backup_key() ->> 'key', true);

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select isnt(public.backup_key() ->> 'key', current_setting('test.riya_key'), 'Sam gets a different key');
select throws_ok($$ select * from private.backup_keys $$, '42501', null, 'the keys cannot be read directly');

reset role;
select is((select count(*)::int from private.backup_keys), 2, 'one key is stored per person');

select * from finish();
rollback;
