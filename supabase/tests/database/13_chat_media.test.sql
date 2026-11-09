-- Chat media: only the two people in a chat may use its folder; uploads go under the
-- sender's own ID; the bucket is private and holds ciphertext only.
begin;
create extension if not exists pgtap with schema extensions;

select plan(9);

insert into auth.users (id, email) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'riya@meyouww.test'),
  ('bbbbbbbb-0000-0000-0000-000000000002', 'sam@meyouww.test'),
  ('cccccccc-0000-0000-0000-000000000003', 'tara@meyouww.test');
insert into public.conversations (id, user_a, user_b) values
  ('dddddddd-0000-0000-0000-00000000000d', 'aaaaaaaa-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000002');

create function pg_temp.as_user(p uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p, 'role', 'authenticated')::text, true);
$$;

select is((select public from storage.buckets where id = 'chat-media'), false, 'the chat-media bucket is private');
select is((select allowed_mime_types from storage.buckets where id = 'chat-media'), array['application/octet-stream'],
  'it only takes opaque encrypted blobs');

set local role authenticated;
select pg_temp.as_user('aaaaaaaa-0000-0000-0000-000000000001');
select ok(public.can_use_chat_media('aaaaaaaa-0000-0000-0000-000000000001/dddddddd-0000-0000-0000-00000000000d/m1'),
  'Riya can use a file she sent into her chat with Sam');
select ok(public.can_use_chat_media('bbbbbbbb-0000-0000-0000-000000000002/dddddddd-0000-0000-0000-00000000000d/m2'),
  'and read one Sam sent her');
select ok(not public.can_use_chat_media('cccccccc-0000-0000-0000-000000000003/dddddddd-0000-0000-0000-00000000000d/m3'),
  'a folder for someone outside the chat is refused');
select ok(not public.can_use_chat_media('aaaaaaaa-0000-0000-0000-000000000001/eeeeeeee-0000-0000-0000-00000000000e/m4'),
  'so is a chat that does not exist');

select pg_temp.as_user('cccccccc-0000-0000-0000-000000000003');
select ok(not public.can_use_chat_media('aaaaaaaa-0000-0000-0000-000000000001/dddddddd-0000-0000-0000-00000000000d/m1'),
  'Tara cannot read files from a chat she is not in');
select throws_ok($$ select public.claim_chat_media_sweep(5) $$, '42501', null, 'only the server may sweep');

reset role;
select is(public.claim_chat_media_sweep(5), '{}'::text[], 'nothing old to sweep');

select * from finish();
rollback;
