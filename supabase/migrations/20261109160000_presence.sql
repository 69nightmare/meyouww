-- Online, last seen, typing, and "has our chat open", for friends only.
--
-- Each phone reports a heartbeat while the app is in front (about every 25 s), which chat
-- is open on screen, and a short "typing" mark while someone types. A friend's phone asks
-- for this while their chat with you is open. Nothing about message content is involved;
-- strangers and blocked people get nothing.

create table public.presence (
  user_id uuid primary key references auth.users (id) on delete cascade,
  last_active_at timestamptz not null default now(),
  -- The conversation open on screen right now (only ever one of the person's own).
  open_chat uuid references public.conversations (id) on delete set null,
  typing_in uuid references public.conversations (id) on delete set null,
  typing_at timestamptz
);

alter table public.presence enable row level security;
-- No policies: only the two functions below touch it.

create or replace function public.set_presence(p_open_chat uuid default null, p_typing boolean default false)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_chat uuid;
begin
  if not private.consume_rate_limit('presence:' || v_uid::text, 1500, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  select c.id into v_chat from public.conversations c
   where c.id = p_open_chat and v_uid in (c.user_a, c.user_b);
  insert into public.presence as p (user_id, last_active_at, open_chat, typing_in, typing_at)
  values (v_uid, now(), v_chat, case when p_typing then v_chat end, case when p_typing and v_chat is not null then now() end)
  on conflict (user_id) do update
    set last_active_at = now(),
        open_chat = excluded.open_chat,
        typing_in = excluded.typing_in,
        typing_at = excluded.typing_at;
  return jsonb_build_object('status', 'ok');
end;
$$;

create or replace function public.peer_presence(p_user uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_p public.presence;
  v_chat uuid;
  v_fresh boolean;
begin
  if p_user = v_uid or private.friend_level(v_uid, p_user) is null or private.blocked_between(v_uid, p_user) then
    return jsonb_build_object('status', 'not_friends');
  end if;
  select * into v_p from public.presence where user_id = p_user;
  if not found then
    return jsonb_build_object('status', 'ok', 'online', false);
  end if;
  select c.id into v_chat from public.conversations c
   where (c.user_a, c.user_b) = (least(v_uid, p_user), greatest(v_uid, p_user));
  v_fresh := v_p.last_active_at > now() - interval '45 seconds';
  return jsonb_build_object(
    'status', 'ok',
    'online', v_fresh,
    'last_seen', v_p.last_active_at,
    'in_chat', v_fresh and v_chat is not null and v_p.open_chat = v_chat,
    'typing', v_chat is not null and v_p.typing_in = v_chat and v_p.typing_at > now() - interval '6 seconds');
end;
$$;

revoke all on table public.presence from public, anon, authenticated;
revoke all on function public.set_presence(uuid, boolean) from public, anon;
revoke all on function public.peer_presence(uuid) from public, anon;
grant execute on function public.set_presence(uuid, boolean) to authenticated;
grant execute on function public.peer_presence(uuid) to authenticated;
