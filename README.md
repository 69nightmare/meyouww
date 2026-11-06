# meyouww

A little private world for you and your people: chat, doodles, surprise wallpapers, reminders and alarms. Native Android (Kotlin, Jetpack Compose) with a Supabase backend and Firebase Cloud Messaging for closed-app delivery.

**Status: Phase 1 of 9 (foundation).** See [docs/STATUS.md](docs/STATUS.md) for what works today and what doesn't yet.

## What's in Phase 1

- Crayon-cartoon design system: bold crayon palette with contrast verified by unit tests, hand-drawn wobbly outlines (seeded, so they never shimmer), waxy grain, bundled OFL fonts and the Whity sticker pack.
- Welcome pages, an 18+ age gate with terms consent, passwordless email sign-in with a 6-digit code, and ID + profile setup.
- Encrypted session storage (DataStore + Tink, Keystore-backed) that background work can use while the phone is locked.
- Firebase Cloud Messaging registration, all notification channels, and data-only push handling that posts the notification first and does the work second.
- "Make meyouww work like WhatsApp" setup: live checks for notifications, battery restriction, Data Saver, phone-maker background permission, Google Play services and exact alarms, plus a **closed-app delivery self-test** that measures arrival time.
- Supabase schema with Row Level Security on every table, pgTAP tests, and Edge Functions `register-device`, `update-device-state` and `test-push`.

## Project layout

| Module | Contents |
|---|---|
| `app` | Application, activity, splash, navigation |
| `core/model` | Pure Kotlin rules: user IDs, age gate, delivery health (JVM unit tests) |
| `core/designsystem` | Theme, crayon outline, components, fonts, stickers |
| `core/data` | Supabase client, encrypted session, repositories, device sync worker |
| `core/notifications` | Channels, FCM service, push handling, self-test worker |
| `feature/onboarding` | Welcome, age gate, sign-in, profile setup |
| `feature/home` | Home |
| `feature/settings` | Settings, background-delivery setup, licences |
| `supabase` | Migrations, pgTAP tests, Edge Functions |

## Building

Every push builds in GitHub Actions (`.github/workflows/ci.yml`): unit tests, lint, a debug APK (artifact `meyouww-debug-apk`), Deno tests and type checks for the Edge Functions, and pgTAP tests against a local Supabase database.

To build on your own computer, open the folder in Android Studio (JDK 21), copy `local.properties.example` to `local.properties`, fill in the values from your backend, and run the `app` configuration. Without backend values the app still builds and runs, and says plainly that it isn't connected to a server.

Backend setup (Supabase, email codes, Firebase, GitHub secrets): **[docs/SETUP.md](docs/SETUP.md)**.

## Docs

- [docs/STATUS.md](docs/STATUS.md): phase progress, verified vs unverified, known limits
- [docs/DECISIONS.md](docs/DECISIONS.md): owner decisions and engineering choices
- [docs/SETUP.md](docs/SETUP.md): backend and device setup
- [ASSETS.md](ASSETS.md): every asset and its licence
- [docs/legal/PRIVACY.md](docs/legal/PRIVACY.md), [docs/legal/TERMS.md](docs/legal/TERMS.md): drafts for review

## Secrets

No secrets live in this repository or in the APK. The Supabase secret key, the Firebase service-account JSON and the SMTP password stay on the server; CI reads the app's (non-secret) backend values from GitHub secrets.
