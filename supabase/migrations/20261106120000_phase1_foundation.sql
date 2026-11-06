-- meyouww Phase 1: profiles, age gate, usernames, devices, push tokens, device state,
-- delivery self-tests and rate limiting.
--
-- Rules followed throughout:
--   * Row Level Security on every table in public, deny by default.
--   * Clients never write tables directly; writes go through functions that take the
--     caller's identity from auth.uid() only.
--   * Functions are SECURITY DEFINER with an empty search_path and fully qualified names.
--   * Device and token functions are callable by service_role (Edge Functions) only.

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Helpers
-- ---------------------------------------------------------------------------

create or replace function private.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- Fixed-window rate limiter. Returns true when the action is allowed.
create table private.rate_limit_counters (
  key text not null,
  window_start timestamptz not null,
  hits integer not null default 0,
  primary key (key, window_start)
);

create or replace function private.consume_rate_limit(p_key text, p_limit integer, p_window interval)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_window timestamptz := to_timestamp(floor(extract(epoch from now()) / extract(epoch from p_window)) * extract(epoch from p_window));
  v_hits integer;
begin
  insert into private.rate_limit_counters as c (key, window_start, hits)
  values (p_key, v_window, 1)
  on conflict (key, window_start) do update set hits = c.hits + 1
  returning c.hits into v_hits;
  return v_hits <= p_limit;
end;
$$;

-- Callable by Edge Functions for their own limits (for example test pushes).
create or replace function public.consume_rate_limit(p_key text, p_limit integer, p_window_seconds integer)
returns boolean
language sql
security definer
set search_path = ''
as $$
  select private.consume_rate_limit(p_key, p_limit, make_interval(secs => p_window_seconds));
$$;

-- ---------------------------------------------------------------------------
-- Profiles
-- ---------------------------------------------------------------------------

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text unique,
  display_name text check (char_length(display_name) between 1 and 40),
  nickname text check (char_length(nickname) between 1 and 24),
  bio text check (char_length(bio) <= 160),
  is_adult boolean not null default false,
  birth_year integer check (birth_year between 1900 and 2100),
  age_confirmed_at timestamptz,
  username_changed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint profiles_username_format check (
    username is null
    or (username ~ '^[a-z][a-z0-9._]{2,19}$' and username !~ '\.\.' and username !~ '\.$')
  )
);

create trigger profiles_touch_updated_at
  before update on public.profiles
  for each row execute function private.touch_updated_at();

-- IDs nobody may claim. Mirrors UserIdRules.RESERVED in the app.
create table public.reserved_usernames (word text primary key);
insert into public.reserved_usernames (word) values
  ('admin'), ('administrator'), ('support'), ('help'), ('official'), ('meyouww'), ('meyou'),
  ('whity'), ('staff'), ('team'), ('moderator'), ('mod'), ('system'), ('root'), ('security'),
  ('privacy'), ('null'), ('undefined'), ('api'), ('app'), ('settings'), ('account'), ('login'),
  ('signup'), ('everyone'), ('anonymous');

-- Substrings that make an ID unacceptable. Kept short to avoid blocking real names.
create table public.blocked_username_terms (term text primary key);
insert into public.blocked_username_terms (term) values
  ('fuck'), ('shit'), ('bitch'), ('cunt'), ('whore'), ('slut'), ('rape'), ('nazi'),
  ('chutiya'), ('madarchod'), ('bhenchod'), ('behenchod'), ('randi'), ('gandu');

-- A changed ID stays reserved for its previous owner for 30 days.
create table public.username_holds (
  username text primary key,
  previous_owner uuid not null references auth.users (id) on delete cascade,
  held_until timestamptz not null
);

-- Every new auth user gets an empty profile.
create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id) on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

-- ---------------------------------------------------------------------------
-- Devices, push tokens and delivery state
-- ---------------------------------------------------------------------------

create table public.devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  installation_id text not null check (char_length(installation_id) between 8 and 64),
  platform text not null default 'android' check (platform in ('android')),
  manufacturer text check (char_length(manufacturer) <= 40),
  os_sdk integer check (os_sdk between 1 and 1000),
  app_version text check (char_length(app_version) <= 40),
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  revoked_at timestamptz,
  unique (user_id, installation_id)
);
create index devices_user_id_idx on public.devices (user_id);
create index devices_installation_id_idx on public.devices (installation_id);

-- Never readable by clients.
create table public.push_tokens (
  device_id uuid primary key references public.devices (id) on delete cascade,
  token text not null unique check (char_length(token) between 20 and 4096),
  updated_at timestamptz not null default now()
);

create table public.device_state (
  device_id uuid primary key references public.devices (id) on delete cascade,
  notifications_enabled boolean not null default false,
  channels jsonb not null default '{}'::jsonb,
  exact_alarms_allowed boolean,
  full_screen_allowed boolean,
  background_restricted boolean,
  data_saver text check (data_saver in ('off', 'allowed', 'on')),
  play_services text check (play_services in ('ok', 'update_required', 'missing', 'error')),
  standby_bucket integer,
  reported_at timestamptz not null default now()
);

create table public.delivery_tests (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  device_id uuid not null references public.devices (id) on delete cascade,
  requested_at timestamptz not null default now(),
  send_at timestamptz not null,
  priority text check (priority in ('high', 'normal')),
  sent_at timestamptz,
  send_error text check (char_length(send_error) <= 200),
  arrival_reported_at timestamptz,
  latency_ms integer check (latency_ms between 0 and 86400000)
);
create index delivery_tests_user_id_idx on public.delivery_tests (user_id, requested_at desc);

-- ---------------------------------------------------------------------------
-- Row Level Security and grants
-- ---------------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.reserved_usernames enable row level security;
alter table public.blocked_username_terms enable row level security;
alter table public.username_holds enable row level security;
alter table public.devices enable row level security;
alter table public.push_tokens enable row level security;
alter table public.device_state enable row level security;
alter table public.delivery_tests enable row level security;

revoke all on public.profiles, public.reserved_usernames, public.blocked_username_terms,
  public.username_holds, public.devices, public.push_tokens, public.device_state,
  public.delivery_tests from anon, authenticated;

grant select on public.profiles, public.devices, public.device_state, public.delivery_tests to authenticated;

create policy "profiles: owner can read" on public.profiles
  for select to authenticated using (id = (select auth.uid()));

create policy "devices: owner can read" on public.devices
  for select to authenticated using (user_id = (select auth.uid()));

create policy "device_state: owner can read" on public.device_state
  for select to authenticated using (
    exists (select 1 from public.devices d where d.id = device_id and d.user_id = (select auth.uid()))
  );

create policy "delivery_tests: owner can read" on public.delivery_tests
  for select to authenticated using (user_id = (select auth.uid()));

-- push_tokens, reserved_usernames, blocked_username_terms and username_holds have
-- no policies at all: only SECURITY DEFINER functions and service_role touch them.

-- ---------------------------------------------------------------------------
-- Functions for signed-in people
-- ---------------------------------------------------------------------------

create or replace function public.complete_age_gate(p_birth_year integer, p_confirmed_adult boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_year integer := extract(year from now())::integer;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if p_birth_year is null or p_birth_year > v_year or p_birth_year < v_year - 120 then
    return jsonb_build_object('status', 'invalid');
  end if;
  if v_year - p_birth_year < 18 then
    return jsonb_build_object('status', 'underage');
  end if;
  if not coalesce(p_confirmed_adult, false) then
    return jsonb_build_object('status', 'invalid');
  end if;
  -- The first recorded birth year is kept; it cannot be changed later from the app.
  update public.profiles
     set is_adult = true,
         birth_year = coalesce(birth_year, p_birth_year),
         age_confirmed_at = coalesce(age_confirmed_at, now())
   where id = v_uid;
  return jsonb_build_object('status', 'ok');
end;
$$;

create or replace function public.claim_username(p_username text, p_display_name text, p_nickname text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_name text := regexp_replace(lower(trim(coalesce(p_username, ''))), '^@', '');
  v_display text := trim(coalesce(p_display_name, ''));
  v_nick text := nullif(trim(coalesce(p_nickname, '')), '');
  v_current text;
  v_changed timestamptz;
  v_adult boolean;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not private.consume_rate_limit('claim_username:' || v_uid::text, 20, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;

  select username, username_changed_at, is_adult
    into v_current, v_changed, v_adult
    from public.profiles where id = v_uid
    for update;

  if not coalesce(v_adult, false) then
    return jsonb_build_object('status', 'invalid', 'reason', 'age_gate_required');
  end if;
  if v_name !~ '^[a-z][a-z0-9._]{2,19}$' or v_name ~ '\.\.' or v_name ~ '\.$' then
    return jsonb_build_object('status', 'invalid');
  end if;
  if char_length(v_display) not between 1 and 40 or (v_nick is not null and char_length(v_nick) > 24) then
    return jsonb_build_object('status', 'invalid');
  end if;
  if exists (select 1 from public.reserved_usernames r where r.word = v_name) then
    return jsonb_build_object('status', 'reserved');
  end if;
  if exists (select 1 from public.blocked_username_terms b where position(b.term in v_name) > 0) then
    return jsonb_build_object('status', 'invalid');
  end if;

  if v_current is not distinct from v_name then
    update public.profiles set display_name = v_display, nickname = v_nick where id = v_uid;
    return jsonb_build_object('status', 'ok');
  end if;

  -- The first ID is free; after that, one change every 30 days.
  if v_current is not null and v_changed is not null and v_changed > now() - interval '30 days' then
    return jsonb_build_object('status', 'too_soon');
  end if;

  if exists (
    select 1 from public.username_holds h
     where h.username = v_name and h.held_until > now() and h.previous_owner <> v_uid
  ) then
    return jsonb_build_object('status', 'taken');
  end if;

  begin
    update public.profiles
       set username = v_name,
           display_name = v_display,
           nickname = v_nick,
           username_changed_at = case when v_current is null then null else now() end
     where id = v_uid;
  exception when unique_violation then
    return jsonb_build_object('status', 'taken');
  end;

  if v_current is not null then
    insert into public.username_holds (username, previous_owner, held_until)
    values (v_current, v_uid, now() + interval '30 days')
    on conflict (username) do update
      set previous_owner = excluded.previous_owner, held_until = excluded.held_until;
  end if;
  delete from public.username_holds where username = v_name;

  return jsonb_build_object('status', 'ok');
end;
$$;

create or replace function public.report_delivery_test(p_test_id uuid, p_latency_ms integer)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  update public.delivery_tests
     set arrival_reported_at = now(),
         latency_ms = greatest(0, least(coalesce(p_latency_ms, 0), 86400000))
   where id = p_test_id
     and user_id = auth.uid()
     and arrival_reported_at is null;
end;
$$;

-- ---------------------------------------------------------------------------
-- Functions for Edge Functions only (service_role)
-- ---------------------------------------------------------------------------

create or replace function public.set_device_state(p_device_id uuid, p_state jsonb)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.device_state as s (
    device_id, notifications_enabled, channels, exact_alarms_allowed, full_screen_allowed,
    background_restricted, data_saver, play_services, standby_bucket, reported_at
  ) values (
    p_device_id,
    coalesce((p_state ->> 'notifications_enabled')::boolean, false),
    coalesce(p_state -> 'channels', '{}'::jsonb),
    (p_state ->> 'exact_alarms_allowed')::boolean,
    (p_state ->> 'full_screen_allowed')::boolean,
    (p_state ->> 'background_restricted')::boolean,
    p_state ->> 'data_saver',
    p_state ->> 'play_services',
    (p_state ->> 'standby_bucket')::integer,
    now()
  )
  on conflict (device_id) do update set
    notifications_enabled = excluded.notifications_enabled,
    channels = excluded.channels,
    exact_alarms_allowed = excluded.exact_alarms_allowed,
    full_screen_allowed = excluded.full_screen_allowed,
    background_restricted = excluded.background_restricted,
    data_saver = excluded.data_saver,
    play_services = excluded.play_services,
    standby_bucket = excluded.standby_bucket,
    reported_at = now();
end;
$$;

-- Registers (or refreshes) a device for a user. p_user_id comes from the verified JWT
-- in the Edge Function, never from the request body.
create or replace function public.upsert_device(
  p_user_id uuid,
  p_installation_id text,
  p_push_token text,
  p_manufacturer text,
  p_os_sdk integer,
  p_app_version text,
  p_state jsonb
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_device uuid;
begin
  insert into public.devices as d (user_id, installation_id, manufacturer, os_sdk, app_version, last_seen_at, revoked_at)
  values (p_user_id, p_installation_id, left(p_manufacturer, 40), p_os_sdk, left(p_app_version, 40), now(), null)
  on conflict (user_id, installation_id) do update set
    manufacturer = coalesce(excluded.manufacturer, d.manufacturer),
    os_sdk = coalesce(excluded.os_sdk, d.os_sdk),
    app_version = coalesce(excluded.app_version, d.app_version),
    last_seen_at = now(),
    revoked_at = null
  returning d.id into v_device;

  -- Someone else signed in on this phone earlier: that registration stops getting pushes.
  delete from public.push_tokens t
   using public.devices d
   where t.device_id = d.id and d.installation_id = p_installation_id and d.user_id <> p_user_id;
  update public.devices
     set revoked_at = now()
   where installation_id = p_installation_id and user_id <> p_user_id and revoked_at is null;

  if p_push_token is not null then
    -- A token belongs to exactly one device.
    delete from public.push_tokens where token = p_push_token and device_id <> v_device;
    insert into public.push_tokens (device_id, token, updated_at)
    values (v_device, p_push_token, now())
    on conflict (device_id) do update set token = excluded.token, updated_at = now();
  end if;

  if p_state is not null then
    perform public.set_device_state(v_device, p_state);
  end if;

  return v_device;
end;
$$;

create or replace function public.unregister_device(p_user_id uuid, p_installation_id text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  delete from public.push_tokens t
   using public.devices d
   where t.device_id = d.id and d.user_id = p_user_id and d.installation_id = p_installation_id;
  update public.devices
     set revoked_at = now()
   where user_id = p_user_id and installation_id = p_installation_id;
end;
$$;

-- Removes a token FCM reported as unregistered or invalid.
create or replace function public.forget_push_token(p_token text)
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.push_tokens where token = p_token;
$$;

-- ---------------------------------------------------------------------------
-- Function privileges. Supabase grants EXECUTE to anon and authenticated by
-- default, so every function is locked down explicitly.
-- ---------------------------------------------------------------------------

revoke all on function public.complete_age_gate(integer, boolean) from public, anon, authenticated;
revoke all on function public.claim_username(text, text, text) from public, anon, authenticated;
revoke all on function public.report_delivery_test(uuid, integer) from public, anon, authenticated;
grant execute on function public.complete_age_gate(integer, boolean) to authenticated;
grant execute on function public.claim_username(text, text, text) to authenticated;
grant execute on function public.report_delivery_test(uuid, integer) to authenticated;

revoke all on function public.set_device_state(uuid, jsonb) from public, anon, authenticated;
revoke all on function public.upsert_device(uuid, text, text, text, integer, text, jsonb) from public, anon, authenticated;
revoke all on function public.unregister_device(uuid, text) from public, anon, authenticated;
revoke all on function public.forget_push_token(text) from public, anon, authenticated;
revoke all on function public.consume_rate_limit(text, integer, integer) from public, anon, authenticated;
grant execute on function public.set_device_state(uuid, jsonb) to service_role;
grant execute on function public.upsert_device(uuid, text, text, text, integer, text, jsonb) to service_role;
grant execute on function public.unregister_device(uuid, text) to service_role;
grant execute on function public.forget_push_token(text) to service_role;
grant execute on function public.consume_rate_limit(text, integer, integer) to service_role;

revoke all on all functions in schema private from public, anon, authenticated;
grant usage on schema private to service_role;
