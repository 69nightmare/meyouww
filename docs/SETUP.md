# Backend setup

## What's already done (6 November 2026)

| Piece | State |
|---|---|
| Supabase project `meyouww` (ref `zjjhihhttsqpmucgnneh`, region ap-south-1 Mumbai, Free plan) | Created, owned by the GitHub account `whityboss2020` |
| Firebase project `meyouww` (ID `meyouww-a4bb1`) | Created under `whity1212020@gmail.com`; Analytics and Gemini off |
| Firebase Android app `com.meyouww.app` | Registered, with the shared debug key's SHA-1 |
| Google sign-in (Google Auth Platform) | App "meyouww", External, **In production** (only name/email/profile requested, so no review needed) |
| OAuth clients | Web client `687362852032-bo34…` (used by Supabase), Android client for `com.meyouww.app` + debug SHA-1 |
| Supabase Auth → Google provider | Enabled with both client IDs; nonce checks on |
| App build settings | `config/backend.properties` (public values only) |
| Database (Phase 1 migration) | Applied from the SQL editor and recorded in `supabase_migrations.schema_migrations`; RLS on all 8 public tables |
| Edge Functions `register-device`, `update-device-state`, `test-push` | Deployed from the dashboard editor using `supabase/bundled/*.ts`; legacy JWT check off (functions verify tokens themselves) |
| Email codes | 6 digits, expire after 600 s; at most 30 emails an hour, 60 s apart per person |
| Email sending (custom SMTP) | Gmail `smtp.gmail.com:587` as `whity1212020@gmail.com`, sender name "meyouww" (app password pasted by the owner) |
| Email templates | "Confirm sign up" and "Magic link or OTP" both send "Your meyouww code" with the 6-digit `{{ .Token }}` |
| Edge Function secret `FCM_SERVICE_ACCOUNT` | Stored (Firebase service-account key, pasted by the owner) |

Nothing else is needed for Phase 1. The first real check is on a phone: sign in with an email code or Google, then open **Settings → Background delivery** and tap **Test closed-app delivery**.

Gmail is a personal mail service, so Supabase shows a deliverability warning and the odd code may land in spam. Before a public launch, switch the SMTP settings to a sending service such as Brevo or Resend; the app needs no change.

### Optional: automatic backend deploys from GitHub

To have every backend change on `main` deployed automatically, add these to **GitHub → meyouww → Settings → Secrets and variables → Actions → New repository secret**:

| Secret name | Where to get it |
|---|---|
| `SUPABASE_ACCESS_TOKEN` | supabase.com → your avatar → **Account preferences → Access Tokens → Generate new token** (name it "meyouww deploy"). Lets the "Deploy backend" workflow apply database changes and deploy server functions. |
| `SMTP_PASSWORD` | A Gmail **app password** for `whity1212020@gmail.com`: myaccount.google.com → Security → turn on **2-Step Verification**, then **App passwords** → create one named "meyouww". Paste the 16 characters. |
| `SMTP_USER` | `whity1212020@gmail.com` (the Gmail address that sends the codes) |
| `FCM_SERVICE_ACCOUNT` | Firebase console → ⚙ Project settings → **Service accounts → Generate new private key**. Open the downloaded `.json` file in Notepad and paste all of it. Then delete the file. |

Then **Actions → Deploy backend → Run workflow**. It:
1. creates the database tables and security rules (`supabase/migrations`),
2. turns on 6-digit email codes with the meyouww email template and Gmail as the sender,
3. stores the Firebase key for the server functions,
4. deploys `register-device`, `update-device-state` and `test-push`.

It runs again automatically whenever backend files change on `main`.

## Before a Google Play release

- Play signs the app with its own key. After the first upload, copy the **app signing key SHA-1** from Play Console → Test and release → App integrity, and add it (a) to the Firebase Android app and (b) as another Android OAuth client in Google Cloud → Google Auth Platform → Clients. Otherwise "Continue with Google" fails in the Play build.
- Optionally restrict the Firebase Android API key to `com.meyouww.app` + the SHA-1s in Google Cloud → Credentials.

## Free-plan limits

- Supabase Free projects **pause after 7 days without activity**; restore from the dashboard. While paused nothing works, including closed-app delivery.
- Gmail sends up to about 500 emails a day, plenty for login codes.
- Firebase Cloud Messaging is free. Don't enable Firebase Cloud Storage (it needs the paid Blaze plan).

## Building on your own computer

Android Studio (JDK 21) builds with the values in `config/backend.properties`; nothing else is needed. To point a build at a different backend, put overrides in `local.properties` (see `local.properties.example`).

## Installing on a phone

Easiest: open **https://github.com/69nightmare/meyouww/releases** on the phone, download the newest `meyouww-v….apk` and open it. No GitHub login needed.

Every push to `main` or to the `release` branch builds the app and publishes it as the release named after `versionName` in `app/build.gradle.kts` (bump it for a new release; the same version replaces that release's APK). Tags `v*` and **Actions → Release APK → Run workflow** work too. Every CI run also keeps the APK as the `meyouww-debug-apk` artifact for 30 days.

All test builds are signed with the same debug key, so new builds install over old ones.
