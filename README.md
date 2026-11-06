# meyouww

A little private world for you and your people: chat, doodles, surprise wallpapers, reminders and alarms. Native Android (Kotlin, Jetpack Compose) with a Supabase backend and Firebase Cloud Messaging for closed-app delivery.

**Status: Phase 6 of 9.** See [docs/STATUS.md](docs/STATUS.md) for details and known limits.

## What works

- Crayon-cartoon design system with the Whity sticker pack; 18+ age gate; **Continue with Google** or email-code sign-in.
- Friends by ID, link or QR code; best friend and partner levels; per-friend permissions; blocking.
- The heart inbox: requests and updates you can accept from the notification.
- Doodle studio (pencil, crayon, marker, shapes, text, stickers, photos) with drafts that never get lost.
- Exact previews on the friend's phone shape, then **send a doodle or photo straight onto a friend's wallpaper** (automatically or with their OK, now or scheduled), with Undo and a "home base" to go back to.
- **End-to-end encrypted chat** (Signal Protocol via libsignal) with stickers, replies, reactions, edits, deletions, receipts and safety-number verification. See [docs/E2EE.md](docs/E2EE.md).
- Notifications that still arrive when the phone maker stops the app, a catch-up safety net, and a notification check that shows where a push stopped.

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
| `core/scene`, `core/render` | Doodle scene model and the single renderer used for editing, previews and export |
| `core/database` | Encrypted local database (Room + SQLCipher) for drafts and wallpaper history |
| `core/chat` | Signal Protocol keys, sessions and encrypted messages (libsignal + SQLCipher) |
| `feature/friends`, `feature/inbox`, `feature/studio`, `feature/chat` | Friends, heart inbox, studio and previews, chats |
| `supabase` | Migrations, pgTAP tests, Edge Functions |

## Building

Every push builds in GitHub Actions (`.github/workflows/ci.yml`): unit tests, lint, a debug APK (artifact `meyouww-debug-apk`), Deno tests and type checks for the Edge Functions, and pgTAP tests against a local Supabase database.

To build on your own computer, open the folder in Android Studio (JDK 21) and run the `app` configuration. Backend settings come from `config/backend.properties` (public client values only).

Backend setup (Supabase, email codes, Firebase, GitHub secrets): **[docs/SETUP.md](docs/SETUP.md)**.

## Docs

- [docs/STATUS.md](docs/STATUS.md): phase progress, verified vs unverified, known limits
- [docs/DECISIONS.md](docs/DECISIONS.md): owner decisions and engineering choices
- [docs/SETUP.md](docs/SETUP.md): backend and device setup
- [ASSETS.md](ASSETS.md): every asset and its licence
- [docs/legal/PRIVACY.md](docs/legal/PRIVACY.md), [docs/legal/TERMS.md](docs/legal/TERMS.md): drafts for review

## Licence

meyouww is free software under the **GNU Affero General Public License v3.0** (see [LICENSE](LICENSE)), because it uses Signal's libsignal (AGPL-3.0) for chat encryption. Bundled fonts and stickers keep their own licences ([ASSETS.md](ASSETS.md)).

## Secrets

No secrets live in this repository or in the APK. `config/backend.properties` holds only values that ship in every APK by design. The Supabase secret key and access token, the Firebase service-account JSON and the SMTP password live in GitHub Actions secrets and Supabase function secrets.
