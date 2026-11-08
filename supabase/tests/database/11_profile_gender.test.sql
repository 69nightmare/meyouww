-- Gender: set by the person only, one of three values or cleared, never on the friend card.
begin;
create extension if not exists pgtap with schema extensions;

select plan(5);

insert into auth.users (id, email) values ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test');
create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select is(public.set_gender('female') ->> 'status', 'ok', 'a person sets their gender');
select is((select gender from public.profiles where id = 'aaaaaaaa-0000-0000-0000-000000000001'), 'female', 'and can read it back');
select is(public.set_gender('robot') ->> 'status', 'invalid', 'only female, male or other');
select is(public.set_gender(null) ->> 'status', 'ok', 'it can be cleared');
reset role;
select ok(private.public_card('aaaaaaaa-0000-0000-0000-000000000001') ? 'gender' is false, 'friends never see it');

select * from finish();
rollback;
