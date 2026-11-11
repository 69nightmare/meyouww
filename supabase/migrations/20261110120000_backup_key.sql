-- Chat backup to the person's own Google Drive.
--
-- Chats live only on the phone, end to end encrypted in transit. A backup copy goes into a
-- hidden app folder in the person's Google Drive, encrypted on the phone before it leaves.
-- The key for that encryption is kept here, one per person, and handed only to that person
-- once they are signed in. So Google holds data it cannot read, and this server holds a key
-- but never the data: neither can read the chats alone. (No password to remember or lose,
-- which is what the owner asked for.)

create table if not exists private.backup_keys (
  user_id uuid primary key references auth.users (id) on delete cascade,
  key bytea not null check (octet_length(key) = 32),
  created_at timestamptz not null default now()
);
alter table private.backup_keys enable row level security;

-- My backup key (made the first time it is asked for), base64.
create or replace function public.backup_key()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_uid();
  v_key bytea;
begin
  if not private.consume_rate_limit('backup_key:' || v_uid::text, 60, interval '1 hour') then
    return jsonb_build_object('status', 'rate_limited');
  end if;
  -- 32 bytes from two random (version 4) UUIDs: about 244 bits of randomness.
  insert into private.backup_keys (user_id, key)
  values (v_uid, decode(replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''), 'hex'))
  on conflict (user_id) do nothing;
  select k.key into v_key from private.backup_keys k where k.user_id = v_uid;
  return jsonb_build_object('status', 'ok', 'key', encode(v_key, 'base64'));
end;
$$;

revoke all on function public.backup_key() from public, anon;
grant execute on function public.backup_key() to authenticated;
