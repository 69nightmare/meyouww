-- meyouww: keep as little as possible on the server ("like Snapchat").
--
-- * Chat: the server only ever had encrypted envelopes, deleted when the phone
--   acknowledges them. Undelivered envelopes now go after 7 days instead of 30.
-- * Wallpapers and doodles: the recipient's phone deletes the picture from storage
--   right after downloading it (applied or not); the sender can still delete their own.
-- * Anything left behind (turned down, expired, cancelled, replaced, failed) is swept
--   by push-dispatch, which deletes the files through the Storage API.

create or replace function private.expire_messages()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from public.encrypted_messages where created_at < now() - interval '7 days';
$$;

-- The recipient of a wallpaper may delete its picture once it reached them.
create or replace function public.can_delete_wallpaper_object(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.wallpaper_commands c
     where c.storage_path = p_name and c.recipient_id = auth.uid()
  );
$$;
revoke all on function public.can_delete_wallpaper_object(text) from public, anon;
grant execute on function public.can_delete_wallpaper_object(text) to authenticated;

do $$
begin
  if to_regclass('storage.objects') is null then
    raise notice 'storage schema not present; policy skipped';
    return;
  end if;
  execute $p$create policy "wallpapers: recipients delete what reached them" on storage.objects
    for delete to authenticated
    using (bucket_id = 'wallpapers' and public.can_delete_wallpaper_object(name))$p$;
end;
$$;

-- Sweep: hands out up to p_limit picture paths whose command is finished (or past its
-- expiry) and marks them swept. push-dispatch deletes the files and hands back any it
-- could not delete.
alter table public.wallpaper_commands add column if not exists media_swept_at timestamptz;
create index if not exists wallpaper_commands_unswept_idx
  on public.wallpaper_commands (created_at) where media_swept_at is null;

create or replace function public.claim_wallpaper_media_sweep(p_limit integer default 25)
returns text[]
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_paths text[];
begin
  with picked as (
    select c.id
      from public.wallpaper_commands c
     where c.media_swept_at is null
       and (c.status in ('applied', 'declined', 'failed', 'expired', 'cancelled', 'replaced') or c.expires_at < now())
     order by c.created_at
     limit least(greatest(coalesce(p_limit, 25), 1), 100)
     for update skip locked
  ), marked as (
    update public.wallpaper_commands c set media_swept_at = now()
      from picked where c.id = picked.id
    returning c.storage_path
  )
  select coalesce(array_agg(storage_path), '{}') into v_paths from marked;
  return v_paths;
end;
$$;

create or replace function public.release_wallpaper_media_sweep(p_paths text[])
returns void
language sql
security definer
set search_path = ''
as $$
  update public.wallpaper_commands set media_swept_at = null where storage_path = any(p_paths);
$$;

revoke all on function public.claim_wallpaper_media_sweep(integer) from public, anon, authenticated;
revoke all on function public.release_wallpaper_media_sweep(text[]) from public, anon, authenticated;
grant execute on function public.claim_wallpaper_media_sweep(integer) to service_role;
grant execute on function public.release_wallpaper_media_sweep(text[]) to service_role;

revoke all on all functions in schema private from public, anon, authenticated;
