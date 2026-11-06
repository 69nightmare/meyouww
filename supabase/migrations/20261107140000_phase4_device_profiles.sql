-- meyouww Phase 4: device profiles for accurate previews.
--
-- A profile holds only screen facts (size, density, insets, corner radius) and
-- wallpaper capabilities, plus the owner's own "live wallpaper" / "lock screen
-- rotates" answers. Model and maker are stored only if the owner chose to share
-- them. Friends can read a profile only when the owner switched "Share my screen
-- specs" on for that friend. The device's time zone is stored separately and is
-- never shown to anyone: the server uses it to schedule "7 AM their time".

alter table public.devices add column timezone text check (char_length(timezone) between 1 and 64);

create table public.device_profiles (
  device_id uuid primary key references public.devices (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  width_px integer not null check (width_px between 200 and 10000),
  height_px integer not null check (height_px between 200 and 10000),
  density_dpi integer not null check (density_dpi between 72 and 1200),
  status_bar_px integer not null default 0 check (status_bar_px between 0 and 2000),
  nav_bar_px integer not null default 0 check (nav_bar_px between 0 and 2000),
  cutout_top_px integer not null default 0 check (cutout_top_px between 0 and 2000),
  cutout_bottom_px integer not null default 0 check (cutout_bottom_px between 0 and 2000),
  corner_radius_px integer not null default 0 check (corner_radius_px between 0 and 2000),
  sdk_int integer check (sdk_int between 1 and 1000),
  maker text check (char_length(maker) <= 40),
  model text check (char_length(model) <= 60),
  wallpaper_supported boolean not null default true,
  set_wallpaper_allowed boolean not null default true,
  separate_lock boolean not null default true,
  live_wallpaper boolean not null default false,
  lock_rotates boolean not null default false,
  -- Other displays (foldables): [{"w":2208,"h":1840,"dpi":420}]
  displays jsonb not null default '[]'::jsonb check (jsonb_typeof(displays) = 'array' and jsonb_array_length(displays) <= 3),
  updated_at timestamptz not null default now()
);
create index device_profiles_user_idx on public.device_profiles (user_id);

alter table public.device_profiles enable row level security;
revoke all on public.device_profiles from anon, authenticated;
grant select on public.device_profiles to authenticated;
create policy "device_profiles: owner can read" on public.device_profiles
  for select to authenticated using (user_id = (select auth.uid()));

-- Called by the app whenever the screen or the owner's answers change.
create or replace function public.upsert_device_profile(p_installation_id text, p_profile jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_device uuid;
  p jsonb := coalesce(p_profile, '{}'::jsonb);
  v_displays jsonb := coalesce(p -> 'displays', '[]'::jsonb);
  v_tz text := nullif(trim(coalesce(p ->> 'timezone', '')), '');
begin
  select id into v_device from public.devices
   where user_id = v_uid and installation_id = p_installation_id and revoked_at is null;
  if v_device is null then
    return jsonb_build_object('status', 'not_found');
  end if;
  if not private.consume_rate_limit('device_profile:' || v_device::text, 60, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  if jsonb_typeof(v_displays) <> 'array' or exists (
       select 1 from jsonb_array_elements(v_displays) d
        where jsonb_typeof(d) <> 'object'
           or coalesce((d ->> 'w')::integer, 0) not between 200 and 10000
           or coalesce((d ->> 'h')::integer, 0) not between 200 and 10000
           or coalesce((d ->> 'dpi')::integer, 0) not between 72 and 1200) then
    return jsonb_build_object('status', 'invalid');
  end if;
  -- Only real IANA zone names are kept.
  if v_tz is not null and not exists (select 1 from pg_catalog.pg_timezone_names where name = v_tz) then
    v_tz := null;
  end if;

  begin
    insert into public.device_profiles as dp (
      device_id, user_id, width_px, height_px, density_dpi, status_bar_px, nav_bar_px,
      cutout_top_px, cutout_bottom_px, corner_radius_px, sdk_int, maker, model,
      wallpaper_supported, set_wallpaper_allowed, separate_lock, live_wallpaper, lock_rotates, displays, updated_at)
    values (
      v_device, v_uid,
      (p ->> 'width_px')::integer, (p ->> 'height_px')::integer, (p ->> 'density_dpi')::integer,
      coalesce((p ->> 'status_bar_px')::integer, 0), coalesce((p ->> 'nav_bar_px')::integer, 0),
      coalesce((p ->> 'cutout_top_px')::integer, 0), coalesce((p ->> 'cutout_bottom_px')::integer, 0),
      coalesce((p ->> 'corner_radius_px')::integer, 0), (p ->> 'sdk_int')::integer,
      nullif(left(p ->> 'maker', 40), ''), nullif(left(p ->> 'model', 60), ''),
      coalesce((p ->> 'wallpaper_supported')::boolean, true), coalesce((p ->> 'set_wallpaper_allowed')::boolean, true),
      coalesce((p ->> 'separate_lock')::boolean, true), coalesce((p ->> 'live_wallpaper')::boolean, false),
      coalesce((p ->> 'lock_rotates')::boolean, false), v_displays, now())
    on conflict (device_id) do update set
      width_px = excluded.width_px, height_px = excluded.height_px, density_dpi = excluded.density_dpi,
      status_bar_px = excluded.status_bar_px, nav_bar_px = excluded.nav_bar_px,
      cutout_top_px = excluded.cutout_top_px, cutout_bottom_px = excluded.cutout_bottom_px,
      corner_radius_px = excluded.corner_radius_px, sdk_int = excluded.sdk_int,
      maker = excluded.maker, model = excluded.model,
      wallpaper_supported = excluded.wallpaper_supported, set_wallpaper_allowed = excluded.set_wallpaper_allowed,
      separate_lock = excluded.separate_lock, live_wallpaper = excluded.live_wallpaper,
      lock_rotates = excluded.lock_rotates, displays = excluded.displays, updated_at = now();
  exception when invalid_text_representation or check_violation or not_null_violation or numeric_value_out_of_range then
    return jsonb_build_object('status', 'invalid');
  end;
  update public.devices set timezone = coalesce(v_tz, timezone), last_seen_at = now() where id = v_device;
  return jsonb_build_object('status', 'ok', 'device_id', v_device);
end;
$$;

-- A friend's phones, for previews. Empty unless they share screen specs with you.
create or replace function public.list_contact_devices(p_contact uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  if private.friend_level(v_uid, p_contact) is null or private.blocked_between(v_uid, p_contact)
     or not exists (select 1 from public.relationship_permissions r
                     where r.owner_id = p_contact and r.contact_id = v_uid and r.share_screen_specs) then
    return '[]'::jsonb;
  end if;
  return coalesce((
    select jsonb_agg(jsonb_build_object(
             'device_id', dp.device_id,
             'width_px', dp.width_px, 'height_px', dp.height_px, 'density_dpi', dp.density_dpi,
             'status_bar_px', dp.status_bar_px, 'nav_bar_px', dp.nav_bar_px,
             'cutout_top_px', dp.cutout_top_px, 'cutout_bottom_px', dp.cutout_bottom_px,
             'corner_radius_px', dp.corner_radius_px,
             'maker', dp.maker, 'model', dp.model,
             'separate_lock', dp.separate_lock, 'live_wallpaper', dp.live_wallpaper,
             'lock_rotates', dp.lock_rotates, 'displays', dp.displays,
             'can_set', dp.wallpaper_supported and dp.set_wallpaper_allowed,
             'updated_at', dp.updated_at)
           order by d.last_seen_at desc)
      from public.device_profiles dp
      join public.devices d on d.id = dp.device_id
     where dp.user_id = p_contact and d.revoked_at is null
  ), '[]'::jsonb);
end;
$$;

revoke all on function public.upsert_device_profile(text, jsonb) from public, anon, authenticated;
revoke all on function public.list_contact_devices(uuid) from public, anon, authenticated;
grant execute on function public.upsert_device_profile(text, jsonb) to authenticated;
grant execute on function public.list_contact_devices(uuid) to authenticated;
