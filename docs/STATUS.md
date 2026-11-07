# Status

_Updated 7 November 2026._

| Phase | Scope | State |
|---|---|---|
| 0 | Decisions, accounts, CI skeleton | **Done.** |
| 1 | Foundation: modules, design system, sign-in, age gate, push registration, channels | **Done**, deployed. |
| 2 | Friends: ID lookup, requests, QR/invite links, relationships, permissions, heart inbox | **Done**, deployed (v0.2.0). |
| 3 | Wallpaper studio and doodle canvas | **Done** (v0.4.0). |
| 4 | Recipient phone preview | **Done** (v0.4.0). |
| 5 | Remote wallpaper delivery (auto / ask, scheduled, Undo, home base) | **Done** (v0.5.0); notification reliability fixes in v0.5.1. |
| 6 | End-to-end encrypted chat (libsignal) with a chat box for doodles, photos and stickers | **Done** (v0.6.0). See `E2EE.md`. |
| 7 | Alarms (shared and personal); shared calendar and reminders | **Done.** Alarms in v0.7.0; calendar on Home, event invitations, reminders and profile pictures in v0.8.0. |
| 8 | Polish and hardening | Not started |
| 9 | Release builds and the reliability report | Not started |

## Known gaps

- Wallpapers and doodles are stored privately but not end-to-end encrypted, and wallpaper commands are not signed yet.
- Your own second phone doesn't receive copies of chat messages you send.
- Group chats.
- The closed-app device matrix (brief §24.4) has not been run on real phones; no results are claimed.

## Backend audit (8 Nov, live server)

Supabase security advisor: 0 errors. RLS on every table; the only direct table reads are each person's own rows; every SECURITY DEFINER function pins `search_path`; nothing in `private` is callable from the app; no public function is callable without signing in; storage buckets are private. Fixed: Supabase's `rls_auto_enable()` helper was executable by anon, pg_net sat in the public schema, event responses could be flipped to spam notifications, some foreign keys lacked indexes (`20261108100000_audit_fixes.sql`). Accepted by design: "signed-in users can execute SECURITY DEFINER functions" (the API is RPCs that check `auth.uid()`).

## Fixed after real-phone testing (7 Nov)

- **No notifications at all.** Supabase projects created since late 2025 don't grant table privileges to the API roles, so the push sender's device lookup failed quietly and reported "0 devices". Fixed by `20261107200000_service_role_grants.sql`; a test push then reached both phones.
- Pushes now also carry a generic, system-shown notification so phones that stop swiped-away apps (Xiaomi, Oppo, Vivo, Realme...) still show something; a catch-up worker and a Server check in Settings → Background delivery cover the rest.

## Phase 1: verified

Checked by CI on every push (`.github/workflows/ci.yml`):

- **Kotlin unit tests (47):** user-ID rules, age gate, onboarding routing, delivery-health checklist logic, palette contrast (WCAG ratios for every text/fill pair), crayon outline geometry (seeded, in-bounds), push handling order (notification before any work, no network in the handler), clock-offset latency, app phase routing.
- **Android lint** with errors failing the build.
- **Debug APK** builds (artifact `meyouww-debug-apk`).
- **Edge Functions:** Deno unit tests (priority policy, TTL, input sanitising) and full type check.
- **Database (pgTAP, 37 assertions):** RLS on every public table; SECURITY DEFINER functions pin `search_path`; a profile row is created for every new user; age gate rejects under-18, unconfirmed and impossible years; ID rules (format, reserved, blocked terms, uniqueness, 30-day change limit, 30-day hold of released IDs); no direct profile updates; push tokens never readable by clients; device registration callable only by service role; a token belongs to one device; a phone that switches account keeps only the newest registration.
- **Emulator smoke test (API 35):** installs the APK, walks welcome → age gate → sign-in, checks the underage path, dark theme and 200% font scale, and fails on any crash. Screenshots are published to the `ci-screens` branch.

## Phase 1: not verified yet

These need your backend (docs/SETUP.md) and real phones:

- Real email-code sign-in end to end (needs custom SMTP and the code in the email template).
- FCM token registration and the closed-app **delivery self-test** on real phones, including a Xiaomi/OPPO/vivo/realme phone.
- The closed-app device matrix (brief §24.4). Not run; no results are claimed.
- Edge Functions running on the hosted project (they are type-checked, not yet deployed).
- A full TalkBack walkthrough on a device.

## Added after Phase 1
- **Continue with Google** on the sign-in screen (Credential Manager + Supabase ID-token sign-in with nonce). Google's name pre-fills the display name.
- Builds now talk to the hosted backend (`config/backend.properties`), share one debug key, and a "Deploy backend" workflow applies migrations, auth settings and functions once `SUPABASE_ACCESS_TOKEN` is added.

## Known limits of Phase 1

- Home shows the greeting, your ID and background-delivery health. The Friends, Create, Chat and Moments tabs arrive with their phases, so there are no empty tabs.
- No profile picture, invite link or QR code yet (Phase 2). No app lock or "log out of other devices" yet.
- Google sign-in works with the shared debug key only until the Play app-signing SHA-1 is registered (see SETUP).
- Account deletion and data export are not in the app yet; the privacy draft says so. They must ship before any public release.
- The library list on the Licences screen is maintained by hand; an automatic generator comes in Phase 8.
- Instrumented (on-device) tests beyond the smoke test are not in CI yet.
- `targetSdk` is 36; behaviour when targeting 37 (Android 17) still has to be tested.
- End-to-end encryption does not exist yet, and nothing in the app claims it.
- The launcher icon is upscaled from a ~450 px sticker; a ≥1024 px square Whity image would make it sharp.
- The privacy policy and terms are drafts without a grievance contact.
