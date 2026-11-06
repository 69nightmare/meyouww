# Status

_Updated 6 November 2026._

| Phase | Scope | State |
|---|---|---|
| 0 | Decisions, accounts, CI skeleton | **Done.** Decisions in `DECISIONS.md`; CI live; owner still needs to create the Supabase, Firebase and email accounts (`SETUP.md`). |
| 1 | Foundation: modules, design system, email sign-in, age gate, push registration, channels | **Built and passing CI.** Not yet tried against a real backend or on real phones (see below). |
| 2 | Friends: ID lookup, requests, QR/invite links, relationships, "Who can decorate my phone?" | Not started |
| 3 | Wallpaper studio and doodle canvas | Not started |
| 4 | Recipient phone preview | Not started |
| 5 | Remote wallpaper delivery, works-when-closed pipeline | Not started (the push foundation and delivery self-test are already in Phase 1) |
| 6 | End-to-end encrypted chat | Not started. Library decision pending (brief §11.6) |
| 7 | Calendar, reminders and alarms | Not started |
| 8 | Polish and hardening | Not started |
| 9 | Release builds and the reliability report | Not started |

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

## Known limits of Phase 1

- Home shows the greeting, your ID and background-delivery health. The Friends, Create, Chat and Moments tabs arrive with their phases, so there are no empty tabs.
- No profile picture, invite link or QR code yet (Phase 2). No Google sign-in, app lock or "log out of other devices" yet.
- Account deletion and data export are not in the app yet; the privacy draft says so. They must ship before any public release.
- The library list on the Licences screen is maintained by hand; an automatic generator comes in Phase 8.
- Instrumented (on-device) tests beyond the smoke test are not in CI yet.
- `targetSdk` is 36; behaviour when targeting 37 (Android 17) still has to be tested.
- End-to-end encryption does not exist yet, and nothing in the app claims it.
- The launcher icon is upscaled from a ~450 px sticker; a ≥1024 px square Whity image would make it sharp.
- The privacy policy and terms are drafts without a grievance contact.
