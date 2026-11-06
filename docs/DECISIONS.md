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
| Supabase | Free plan, region closest to India (South Asia / Mumbai) | Owner decides on Pro before any public launch. Free projects pause after 7 days of inactivity. |
| Login email | A free-tier transactional email provider via custom SMTP | Required: Supabase's built-in sender only emails project members. |
| End-to-end encryption library | **Decide before Phase 6** | Options and trade-offs: brief §11.6. Crypto will sit behind `:core:crypto`. |
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
