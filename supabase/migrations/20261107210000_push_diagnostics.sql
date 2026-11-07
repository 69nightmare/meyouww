-- meyouww: push diagnostics.
--
-- push-dispatch records one row per event it sends: how many devices and push tokens
-- the person had, how many messages FCM accepted and FCM's error codes. Never tokens,
-- names or content. Rows are kept for 7 days. A person can read their own rows (and
-- the state of this phone's registration) through my_push_status(), which powers the
-- app's notification check.

create table private.push_log (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users (id) on delete cascade,
  type text not null,
  channel text not null,
  devices integer not null,
  tokens integer not null,
  sent integer not null,
  error text,
  created_at timestamptz not null default clock_timestamp()
);
create index push_log_user_idx on private.push_log (user_id, created_at desc);
create index push_log_created_idx on private.push_log (created_at);
alter table private.push_log enable row level security;

create or replace function public.record_push_result(
  p_user uuid, p_type text, p_channel text, p_devices integer, p_tokens integer, p_sent integer, p_error text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_user is null then
    return;
  end if;
  insert into private.push_log (user_id, type, channel, devices, tokens, sent, error)
  values (
    p_user,
    left(coalesce(p_type, '?'), 32),
    left(coalesce(p_channel, '?'), 40),
    greatest(coalesce(p_devices, 0), 0),
    greatest(coalesce(p_tokens, 0), 0),
    greatest(coalesce(p_sent, 0), 0),
    left(p_error, 200));
exception when foreign_key_violation then
  null; -- the account was deleted meanwhile
end;
$$;
revoke all on function public.record_push_result(uuid, text, text, integer, integer, integer, text) from public, anon, authenticated;
grant execute on function public.record_push_result(uuid, text, text, integer, integer, integer, text) to service_role;

create or replace function private.expire_push_log()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from private.push_log where created_at < now() - interval '7 days';
$$;

revoke all on all functions in schema private from public, anon, authenticated;

-- Everything the notification check needs, for the signed-in person and this phone.
create or replace function public.my_push_status(p_installation_id text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_device uuid;
  v_token_at timestamptz;
  v_state public.device_state;
  v_test public.delivery_tests;
  v_dispatcher jsonb := '[]'::jsonb;
begin
  select id into v_device from public.devices
   where user_id = v_uid and installation_id = p_installation_id and revoked_at is null;
  if v_device is not null then
    select updated_at into v_token_at from public.push_tokens where device_id = v_device;
    select * into v_state from public.device_state where device_id = v_device;
    select * into v_test from public.delivery_tests where device_id = v_device order by requested_at desc limit 1;
  end if;

  -- The last replies push-dispatch gave Postgres: only status codes and our own error words.
  begin
    execute $q$
      select coalesce(jsonb_agg(x order by x ->> 'at' desc), '[]'::jsonb) from (
        select jsonb_build_object(
                 'at', r.created,
                 'status', r.status_code,
                 'timed_out', coalesce(r.timed_out, false),
                 'error', case when r.content ~ '^\s*\{' then left((r.content::jsonb) ->> 'error', 40) end) x
          from net._http_response r
         order by r.created desc
         limit 5) s
    $q$ into v_dispatcher;
  exception when others then
    v_dispatcher := '[]'::jsonb;
  end;

  return jsonb_build_object(
    'device_registered', v_device is not null,
    'has_push_token', v_token_at is not null,
    'token_updated_at', v_token_at,
    'notifications_enabled', v_state.notifications_enabled,
    'state_reported_at', v_state.reported_at,
    'dispatcher_configured',
      exists (select 1 from private.settings where key = 'push_dispatch_url') and
      exists (select 1 from private.settings where key = 'push_dispatch_token'),
    'dispatcher', v_dispatcher,
    'last_test', case when v_test.id is null then null else jsonb_build_object(
      'requested_at', v_test.requested_at, 'sent_at', v_test.sent_at,
      'send_error', v_test.send_error, 'arrived', v_test.arrival_reported_at is not null) end,
    'recent', coalesce((
      select jsonb_agg(jsonb_build_object(
               'at', l.created_at, 'type', l.type, 'channel', l.channel, 'devices', l.devices,
               'tokens', l.tokens, 'sent', l.sent, 'error', l.error) order by l.created_at desc)
        from (select * from private.push_log where user_id = v_uid order by created_at desc limit 10) l
    ), '[]'::jsonb));
end;
$$;
revoke all on function public.my_push_status(text) from public, anon;
grant execute on function public.my_push_status(text) to authenticated;

do $$
begin
  if to_regnamespace('cron') is not null then
    perform cron.schedule('meyouww-expire-push-log', '17 3 * * *', 'select private.expire_push_log()');
  end if;
exception when others then
  raise warning 'pg_cron not scheduled: %', sqlerrm;
end;
$$;
