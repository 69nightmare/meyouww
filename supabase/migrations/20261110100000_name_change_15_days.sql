-- The owner's rule: a person can change their name and their ID (username) once every 15
-- days (it was 30 days for the ID, any time for the name). IDs stay unique for everyone, and
-- a given-up ID is still held for 30 days so nobody can pose as its previous owner.
-- Same function, same arguments; only the waiting rule changes (copied from phase 1).

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
  v_current_display text;
  v_changed timestamptz;
  v_adult boolean;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not private.consume_rate_limit('claim_username:' || v_uid::text, 20, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;

  select username, display_name, username_changed_at, is_adult
    into v_current, v_current_display, v_changed, v_adult
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

  -- Nothing about the name changes (only the private nickname, or nothing at all).
  if v_current is not distinct from v_name and v_current_display is not distinct from v_display then
    update public.profiles set nickname = v_nick where id = v_uid;
    return jsonb_build_object('status', 'ok');
  end if;

  -- Setting them the first time is free; after that the name and the ID can change once
  -- every 15 days (one clock for both, so friends aren't confused by constant renames).
  if v_current is not null and v_changed is not null and v_changed > now() - interval '15 days' then
    return jsonb_build_object('status', 'too_soon', 'next_at', v_changed + interval '15 days');
  end if;

  -- Only the name changes: the ID stays, so nothing to hold or check for uniqueness.
  if v_current is not distinct from v_name then
    update public.profiles
       set display_name = v_display, nickname = v_nick, username_changed_at = now()
     where id = v_uid;
    return jsonb_build_object('status', 'ok');
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

revoke all on function public.claim_username(text, text, text) from public, anon;
grant execute on function public.claim_username(text, text, text) to authenticated;
