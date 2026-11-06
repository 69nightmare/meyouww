# Decisions

Owner decisions from Phase 0 (6 November 2026) and engineering choices made since. Newest at the bottom.

## Owner decisions

| Decision | Choice | Notes |
|---|---|---|
| App name | **meyouww** | Replaces the working name "DoodleBoo" from the brief. |
| Application ID | `com.meyouww.app` | Can still change until the first Google Play upload; permanent after that. |
| Distribution | Google Play internal testing first, plus a debug APK sideloaded on the two test phones | Default. |
| Builds | GitHub Actions on `69nightmare/meyouww` | The cloud workspace cannot download the Android SDK, so all compiling happens in CI. |
| Repository visibility | **Public** | As created by the owner. Keeps CI free and would satisfy AGPL if libsignal is chosen. Never commit secrets (see SETUP). |
| Supabase | Free plan, Mumbai (ap-south-1), project ref `zjjhihhttsqpmucgnneh` | Owner decides on Pro before any public launch. Free projects pause after 7 days of inactivity. |
| Firebase / Google Cloud | Project `meyouww-a4bb1` under `whity1212020@gmail.com`; Analytics and Gemini off | Push only. |
| Sign-in methods | Email 6-digit code **and Continue with Google** (added 6 Nov 2026 at the owner's request) | Google sign-in published to production; only basic profile scopes. |
| Login email | Gmail SMTP from `whity1212020@gmail.com` with an app password | Free, ~500 emails/day, no domain needed. Codes arrive from that Gmail address. |
| End-to-end encryption library | **libsignal** (owner, 7 Nov 2026: "whichever is easy to implement and free") | `org.signal:libsignal-android` from Maven Central; no Rust toolchain needed. The app's code becomes **AGPL-3.0** when chat lands (repo is already public). Crypto sits behind `:core:crypto`. |
| Minimum age | 18+ | Birth year and an explicit "I am 18 or older" are both required. |
| Languages | English, all strings externalised | Hindi suggested next. |
| Look and feel | Bold crayon-cartoon palette in the spirit of classic Japanese family-comedy cartoons (crayon red, sunny yellow, sky blue, grass green on cream paper) | Colours and crayon style only. No characters, names, logos, music or catchphrases from any show. |
| Mascot and art | **No Boo or Pip.** The owner's **Whity** dog sticker pack is the mascot, sticker set and app-icon art | 19 stickers from the owner. A square, high-resolution Whity image (≥1024 px) is wanted for a sharper app icon. |

## Engineering choices

| Area | Choice | Why |
|---|---|---|
| Toolchain | AGP 9.5.1, Gradle 9.8.2, Kotlin 2.4.21 (AGP built-in Kotlin), KSP 2.3.13, Hilt 2.60.1 | Latest stable on 2026-11-06, read from Maven metadata by `scripts/probe_versions.py` in CI. |
| SDK levels | `minSdk` 26, `compileSdk` 37, `targetSdk` 36 | Play requires 36+. Target 37 will be tested before release (brief §22.1). |
| Navigation | Navigation Compose 2.10 with type-safe routes | Mature and stable. Navigation 3 can be re-evaluated later. |
| Modules (Phase 1) | `:app`, `:core:model`, `:core:designsystem`, `:core:data`, `:core:notifications`, `:feature:onboarding`, `:feature:home`, `:feature:settings` | Others from the brief (`:core:database`, `:core:crypto`, `:renderer`, …) are added in the phase that needs them. No convention plugins yet, to avoid relying on AGP internals. |
| Firebase setup | Initialised from build settings, no `google-services.json` and no Google Services plugin | Nothing Firebase-related is committed; CI injects the values from secrets. Only Cloud Messaging is used. |
| Debug package | Debug and release share `com.meyouww.app` | One Firebase Android app covers both. |
| Session storage | supabase-kt `SessionManager` backed by DataStore + Tink AEAD (Android Keystore master key) | Replaces deprecated EncryptedSharedPreferences; usable by background workers while the phone is locked. |
| Edge Function auth | `verify_jwt = false`; each function verifies the token with `auth.getUser` | Works with Supabase's new asymmetric JWT signing keys. |
| Device registration | Logic in SQL (`upsert_device`, service_role only), Edge Function is a thin wrapper | Transactional and covered by pgTAP. |
| Backups | `allowBackup=false` and data-extraction rules exclude everything | Sessions are tied to this phone's Keystore; E2EE keys must never be copied out. |
| Channel IDs | Versioned (`messages_v1`, …) | Android freezes a channel's sound and importance once created. |
| Public client config | `config/backend.properties` is committed | The Supabase publishable key, Firebase client values and Google client ID ship in every APK and are public by design. Replaces the earlier plan of GitHub secrets for them (the Actions-variables API isn't reachable from Claude's workspace, and Android Studio builds now work with no setup). Server secrets stay in GitHub secrets. |
| Debug signing | A shared debug keystore is committed (`app/debug.keystore`, password `android`) | Every CI build installs over the previous one, and its SHA-1 is registered for Google sign-in. Debug-only; release signing comes from environment variables. |
| Backend deploys | "Deploy backend" workflow using the Supabase Management API + CLI | Needs only `SUPABASE_ACCESS_TOKEN` (plus optional SMTP and FCM secrets). Migrations are tracked in `supabase_migrations.schema_migrations`, compatible with the Supabase CLI. |
| Google sign-in flow | Credential Manager `GetSignInWithGoogleOption` → ID token → `auth.signInWith(IDToken)` with a SHA-256 nonce | Google's own account sheet; meyouww never sees the Google password. Button is text-only (no Google logo drawn by us). |
| Friends and permissions logic | All in Postgres `SECURITY DEFINER` functions (Phase 2), not Edge Functions | Transactional (an upgrade changes both people at once), covered by pgTAP, and deployable from the SQL editor. Exact-ID lookups are rate-limited per user (30/hour); per-IP limiting would need an Edge Function and can be added later. |
| Push for inbox items | A trigger on `activity` calls `push-dispatch` through `pg_net`, authenticated with a token the database generates for itself | No person ever handles that secret. The push carries IDs only; the app shows a generic notification first, then fetches names. |
| Drawing engine | Own scene model (`:core:scene`, pure Kotlin) and one Android renderer (`:core:render`) used by the studio, previews and exports | Brief §8: one scene, one renderer, aspect-safe units. Jetpack Ink 1.0 is stable but renders strokes with its own engine, so the editor and the exported wallpaper could differ; it can still be added later as the live-ink layer. |
| Low-latency ink | Compose canvas + `MotionEventPredictor` (predicted points) + a bitmap cache of finished work; stylus pressure for pencil, crayon and marker; fingers ignored after a pen is used | Front-buffered rendering (`androidx.graphics`) is a later optimisation if testers notice lag. |
| Crayon texture | A fixed-seed grain mask generated in code, applied with a colour filter on every Android version | Identical in editor, preview and export. An AGSL shader (API 33+) can come later; consistency mattered more. |
| Local database | Room 2.8 + SQLCipher 4.20 (`:core:database`); passphrase is 32 random bytes encrypted by a Tink key in Android Keystore | Brief §21 stack. If the key is lost the database is recreated empty (nothing is backed up anyway). Schema export is off until the first migration. |
| Drafts | Room row per draft + `filesDir/drafts/<id>/scene.json`, photos and a thumbnail; autosave every 3 s and on leaving the screen; atomic writes | Never lose work (brief §8). |
| Photos | Android Photo Picker only; re-encoded as WebP, upright, ≤2048 px, so EXIF and location are dropped | No media permissions. |
| Saving to the gallery | MediaStore on Android 10+; `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion 28` only for Android 8–9 | Brief §8. |
| Device profiles (Phase 4) | Screen size, density, bar/cut-out heights, corner radius and wallpaper capabilities, uploaded when they change; maker/model only if the owner opts in; friends read them only with "Share my screen specs" | Brief §9.1. The phone's time zone is stored on the device row and never shown to anyone; the server uses it for "7 AM their time". Foldable outer/inner displays are not detected yet (the `displays` field is ready). |
| Preview rendering | The preview renders at 540 px wide with the target's exact aspect through the same `SceneTransform.cover` and renderer as the export; a unit-test matrix checks the crop is identical | Brief §9.5. Home/lock furniture is illustrative and labelled so. "Before" is a neutral sample: Android does not let apps read the current wallpaper. |
| Wallpaper commands (Phase 5) | Postgres functions instead of the brief's `create-wallpaper-command` / `report-wallpaper-result` Edge Functions; images in a private `wallpapers` bucket under the sender's folder; recipients can read an object only while its command is live | Same checks (identity from the JWT, friendship, permissions, limits, blocks, quiet hours) in one transaction, covered by pgTAP. Pushes go out through `pg_net` → `push-dispatch`. |
| Scheduled and held wallpapers | Released by a `pg_cron` job every minute, then pushed and applied | Simpler than on-device alarms; the trade-off is that the recipient's phone needs a connection at the scheduled time. On-device scheduling (brief §10.6) can replace this later. |
| Replay protection | Processed command IDs in Room (unique), a per-sender sequence that must increase, WorkManager unique work per command (`KEEP`) | Brief §10.4. Sender signatures arrive with Phase 6 (libsignal); until then a compromised server could forge an auto-applied wallpaper, and this file says so. |
| Applying | `WallpaperManager.setStream(bytes, null, allowBackup = false, FLAG_SYSTEM/FLAG_LOCK)` after a SHA-256 check; the last 10 per target kept for Undo; a user-picked "home base" for Restore | The system decodes the image, so meyouww never holds a full-size bitmap. No accessibility, overlays or keep-alive services. |
| Notifications when the phone maker stops the app (v0.5.1) | Every visible event is sent as two FCM messages: a generic `notification` (text from the app's own string resources via loc keys, same tag as the app's notification) that Google Play services shows without the app, then the data message | Xiaomi, Oppo, Vivo, Realme and others drop data-only pushes to apps that were swiped away, so nothing appeared. The generic text carries no names or content; when the app can run, its detailed notification replaces the generic one in place. |
| Missed pushes | A 15-minute WorkManager catch-up (and a run whenever the app opens) shows unread inbox items that never got a notification and applies waiting automatic wallpapers | A safety net; it does not replace push. |
| Push diagnostics | `push-dispatch` records counts and FCM error codes per event in `private.push_log` (7 days); `my_push_status()` feeds the server half of the app's notification check | Tells the owner and the person exactly where a notification stopped. Tokens, names and content are never logged. |
