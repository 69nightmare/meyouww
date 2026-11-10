-- Name and ID: free the first time, then one change every 15 days, on one clock for both.
begin;
create extension if not exists pgtap with schema extensions;

select plan(9);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test');
update public.profiles set is_adult = true, birth_year = 1999;

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.claim_username('riya.cloud', 'Riya', null) ->> 'status', 'ok', 'the first name and ID are free');
select is(public.claim_username('riya.cloud', 'Riya', 'Riyu') ->> 'status', 'ok', 'the private nickname can change any time');
select is(public.claim_username('riya.cloud', 'Riya Sharma', null) ->> 'status', 'ok', 'the first change of the name is allowed');
select is(public.claim_username('riya.cloud', 'Riya S', null) ->> 'status', 'too_soon', 'a second change of the name within 15 days is refused');
select is(public.claim_username('riya.sky', 'Riya Sharma', null) ->> 'status', 'too_soon', 'and so is a change of the ID: one clock for both');

reset role;
update public.profiles set username_changed_at = now() - interval '16 days' where id = 'aaaaaaaa-0000-0000-0000-000000000001';
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.claim_username('riya.sky', 'Riya', null) ->> 'status', 'ok', 'after 15 days both can change');
select is((select username || '/' || display_name from public.profiles where id = 'aaaaaaaa-0000-0000-0000-000000000001'), 'riya.sky/Riya', 'and both are stored');

select pg_temp.as_user('bbbbbbbb-0000-0000-0000-000000000002');
select is(public.claim_username('riya.sky', 'Sam', null) ->> 'status', 'taken', 'an ID someone has is taken');
select is(public.claim_username('riya.cloud', 'Sam', null) ->> 'status', 'taken', 'and an ID just given up is held for its previous owner');

select * from finish();
rollback;
