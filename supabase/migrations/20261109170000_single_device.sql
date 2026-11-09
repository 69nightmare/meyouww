-- One phone at a time.
--
-- Signing in on a phone makes it the account's only active phone: the app calls
-- claim_only_device right after sign-in, which retires every other phone of the account
-- (no messages are encrypted to them, no pushes reach them) and marks them replaced.
-- A replaced phone stays retired even when its app registers again on start; it learns
-- it was replaced from device_active and signs out. Signing in on it again makes it the
-- active phone and retires the others.

alter table public.devices add column if not exists replaced_at timestamptz;

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
    -- A phone replaced by a sign-in elsewhere stays retired until it signs in again.
    revoked_at = case when d.replaced_at is not null then d.revoked_at else null end
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

-- Right after a sign-in: this phone becomes the only one.
create or replace function public.claim_only_device(p_installation_id text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_me uuid;
  v_count integer;
begin
  update public.devices
     set revoked_at = null, replaced_at = null, last_seen_at = now()
   where user_id = v_uid and installation_id = p_installation_id
  returning id into v_me;
  if v_me is null then
    return jsonb_build_object('status', 'not_found');
  end if;
  delete from public.push_tokens t
   using public.devices d
   where t.device_id = d.id and d.user_id = v_uid and d.id <> v_me;
  update public.devices
     set revoked_at = now(), replaced_at = now()
   where user_id = v_uid and id <> v_me and revoked_at is null;
  get diagnostics v_count = row_count;
  return jsonb_build_object('status', 'ok', 'signed_out', v_count);
end;
$$;

-- Asked when the app starts or comes back: is this still the account's phone?
create or replace function public.device_active(p_installation_id text)
returns jsonb
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(
    (select jsonb_build_object('status', 'ok', 'active', d.revoked_at is null, 'replaced', d.replaced_at is not null)
       from public.devices d
      where d.user_id = (select auth.uid()) and d.installation_id = p_installation_id),
    jsonb_build_object('status', 'not_found'));
$$;

revoke all on function public.claim_only_device(text) from public, anon;
revoke all on function public.device_active(text) from public, anon;
grant execute on function public.claim_only_device(text) to authenticated;
grant execute on function public.device_active(text) to authenticated;
