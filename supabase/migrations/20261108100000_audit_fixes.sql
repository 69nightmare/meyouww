-- meyouww: fixes from the 8 Nov 2026 backend audit (Supabase security advisor + manual review).
--
-- 1. public.rls_auto_enable() is Supabase's event-trigger helper (it switches RLS on for
--    new tables). It is SECURITY DEFINER and was executable by anon. Event triggers don't
--    need EXECUTE, so nobody gets it.
-- 2. pg_net was installed in the public schema; it belongs in "extensions" (its functions
--    live in the "net" schema either way, so push dispatch is unaffected).
-- 3. Foreign keys that are used for account deletion and joins get indexes.
--
-- Accepted, by design: "Signed-in users can execute SECURITY DEFINER function" for every
-- public RPC. The whole API is RPCs that check auth.uid() themselves; tables have no
-- write grants.

do $$
begin
  if to_regprocedure('public.rls_auto_enable()') is not null then
    execute 'revoke all on function public.rls_auto_enable() from public, anon, authenticated';
  end if;
end;
$$;

do $$
begin
  if exists (select 1 from pg_extension e join pg_namespace n on n.oid = e.extnamespace
              where e.extname = 'pg_net' and n.nspname = 'public') then
    create schema if not exists extensions;
    drop extension pg_net;
    create extension pg_net with schema extensions;
  end if;
exception when others then
  raise warning 'pg_net left where it was: %', sqlerrm;
end;
$$;

create index if not exists activity_actor_idx on public.activity (actor_id) where actor_id is not null;
create index if not exists friend_requests_to_idx on public.friend_requests (to_user);
create index if not exists relationship_permissions_contact_idx on public.relationship_permissions (contact_id);
create index if not exists user_blocks_blocked_idx on public.user_blocks (blocked_id);
create index if not exists wallpaper_commands_recipient_idx on public.wallpaper_commands (recipient_id, created_at desc);
create index if not exists alarms_recipient_created_idx on public.alarms (recipient_id, created_at desc);
