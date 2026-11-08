-- meyouww: gender, filled from Google sign-in when the person allows it (or set by hand).
-- Only the person can see their own; it is not part of the card friends see.

alter table public.profiles add column if not exists gender text
  check (gender in ('female', 'male', 'other'));

create or replace function public.set_gender(p_gender text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
begin
  if p_gender is not null and p_gender not in ('female', 'male', 'other') then
    return jsonb_build_object('status', 'invalid');
  end if;
  update public.profiles set gender = p_gender where id = v_uid;
  return jsonb_build_object('status', 'ok');
end;
$$;

revoke all on function public.set_gender(text) from public, anon, authenticated;
grant execute on function public.set_gender(text) to authenticated;
