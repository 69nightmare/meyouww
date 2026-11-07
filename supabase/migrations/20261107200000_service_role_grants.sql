-- meyouww: table privileges for Edge Functions.
--
-- Supabase projects created since late 2025 no longer grant table privileges to the
-- API roles automatically. Edge Functions read a few tables with the secret key
-- (service_role), and without these grants every such read failed quietly:
-- push-dispatch found "0 devices" for everyone, so no push was ever sent, and
-- update-device-state / test-push answered "device not registered".
-- service_role still bypasses RLS; these grants only let it reach the tables.

grant usage on schema public to service_role;
grant select on public.devices, public.push_tokens, public.device_state to service_role;
grant select, insert, update on public.delivery_tests to service_role;
