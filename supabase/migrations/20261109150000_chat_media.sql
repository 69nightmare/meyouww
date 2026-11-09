-- Photos, videos and PDFs in chats, end-to-end encrypted.
--
-- The sending phone encrypts each file with a fresh random AES-256-GCM key and uploads
-- only the ciphertext here, at <sender>/<conversation>/<message id>. The key, the file's
-- SHA-256 and its name travel inside the Signal-encrypted chat message, so the server
-- never sees them: it sees an opaque blob, its size and when it was sent.
-- The receiving phone downloads, checks and decrypts the file, then deletes it here.
-- Anything never fetched is swept after 7 days by push-dispatch (Storage API).

-- Only the two people in a chat may touch its files.
create or replace function public.can_use_chat_media(p_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.conversations c
     where c.id::text = (storage.foldername(p_name))[2]
       and (select auth.uid()) in (c.user_a, c.user_b)
       and (storage.foldername(p_name))[1] in (c.user_a::text, c.user_b::text)
  );
$$;

revoke all on function public.can_use_chat_media(text) from public, anon;
grant execute on function public.can_use_chat_media(text) to authenticated;

-- Oldest leftovers first; deleting them through the Storage API happens in push-dispatch.
create or replace function public.claim_chat_media_sweep(p_limit integer default 25)
returns text[]
language sql
stable
security definer
set search_path = ''
as $$
  select coalesce(array_agg(o.name), '{}')
    from (select name from storage.objects
           where bucket_id = 'chat-media' and created_at < now() - interval '7 days'
           order by created_at
           limit least(greatest(coalesce(p_limit, 25), 1), 100)) o;
$$;

revoke all on function public.claim_chat_media_sweep(integer) from public, anon, authenticated;
grant execute on function public.claim_chat_media_sweep(integer) to service_role;

do $$
begin
  if to_regclass('storage.buckets') is null or to_regclass('storage.objects') is null then
    raise notice 'storage schema not present; bucket and policies skipped';
    return;
  end if;
  -- 25 MB of file plus the encryption overhead. Only ciphertext is ever stored.
  insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
  values ('chat-media', 'chat-media', false, 27262976, array['application/octet-stream'])
  on conflict (id) do update set public = false, file_size_limit = 27262976, allowed_mime_types = array['application/octet-stream'];

  execute $p$drop policy if exists "chat-media: senders upload into their chats" on storage.objects$p$;
  execute $p$drop policy if exists "chat-media: both people can read" on storage.objects$p$;
  execute $p$drop policy if exists "chat-media: both people can delete" on storage.objects$p$;
  execute $p$create policy "chat-media: senders upload into their chats" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'chat-media' and (storage.foldername(name))[1] = (select auth.uid())::text
                and public.can_use_chat_media(name))$p$;
  execute $p$create policy "chat-media: both people can read" on storage.objects
    for select to authenticated
    using (bucket_id = 'chat-media' and public.can_use_chat_media(name))$p$;
  execute $p$create policy "chat-media: both people can delete" on storage.objects
    for delete to authenticated
    using (bucket_id = 'chat-media' and public.can_use_chat_media(name))$p$;
end;
$$;
