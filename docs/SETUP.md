# Setting up the backend

meyouww needs three free accounts: **Supabase** (database, sign-in, server functions), **Firebase** (push notifications only) and a **transactional email provider** (to send the 6-digit login codes). This takes about 30 minutes, once.

Never put any of the *server* secrets below into the app, the repository or a chat: the Supabase secret key, the Firebase service-account JSON and the SMTP password stay on the server.

---

## 1. Supabase project

1. Sign in at <https://supabase.com> and create a new project:
   - Name: `meyouww`
   - Region: **South Asia (Mumbai)**
   - Database password: generate a strong one and keep it in a password manager.
   - Plan: Free (see "Limits" below).
2. **Project settings → API keys**. Note:
   - **Project URL** (`https://<ref>.supabase.co`)
   - **Publishable key** (`sb_publishable_…`). Safe to put in the app.
   - The **secret key** (`sb_secret_…`) is for the server only. Don't copy it anywhere else.

## 2. Email for login codes (custom SMTP, required)

Supabase's built-in email sender only delivers to members of your Supabase team and only a few emails per hour, so real users would never receive codes.

1. Create an account with a transactional email provider that has a free tier and offers SMTP (for example Brevo, Resend or Mailjet; check their current free quotas). Verify a sender address or domain there.
2. In Supabase: **Authentication → Emails → SMTP settings** → enable custom SMTP and enter the provider's host, port, username and password. Sender name: `meyouww`.
3. **Authentication → Emails → Templates**: in both **Confirm signup** and **Magic link**, make the message show the code, for example:

   ```html
   <h2>Your meyouww code</h2>
   <p>Enter this code in the app: <strong>{{ .Token }}</strong></p>
   <p>It expires in 10 minutes. If you didn't ask for it, ignore this email.</p>
   ```
4. **Authentication → Sign In / Providers → Email**: email enabled, OTP length **6**, OTP expiry **600** seconds.
5. **Authentication → Rate limits**: raise "emails per hour" to something sensible for testing (for example 30).

## 3. Firebase (push only)

1. At <https://console.firebase.google.com> create a project `meyouww`. Turn Google Analytics **off**.
2. **Add app → Android**. Package name: `com.meyouww.app`. You can download `google-services.json` to read the values, but **don't add it to the repository**. Note:
   - Project ID
   - App ID (`1:…:android:…`)
   - Web API key (`AIza…`)
   - Project number (this is the sender ID)
3. **Project settings → Service accounts → Generate new private key**. This JSON is a server secret: it goes only into Supabase (step 5).

## 4. Database tables and rules

On your computer, install the Supabase CLI (<https://supabase.com/docs/guides/cli>), then from the repository folder:

```bash
supabase login
supabase link --project-ref <your-project-ref>
supabase db push            # applies supabase/migrations
```

## 5. Server functions and their secrets

```bash
cp .env.example supabase/.env        # fill in FCM_SERVICE_ACCOUNT (one line of JSON)
supabase secrets set --env-file supabase/.env
supabase functions deploy register-device update-device-state test-push
```

`supabase/.env` is git-ignored. Delete your local copy afterwards if you like; the secret now lives in Supabase.

## 6. Connect the app builds (GitHub Actions)

In GitHub: **meyouww repository → Settings → Secrets and variables → Actions → New repository secret**. Add:

| Secret | Value |
|---|---|
| `MEYOUWW_SUPABASE_URL` | Project URL |
| `MEYOUWW_SUPABASE_PUBLISHABLE_KEY` | Publishable key |
| `MEYOUWW_FIREBASE_PROJECT_ID` | Firebase project ID |
| `MEYOUWW_FIREBASE_APP_ID` | Firebase App ID |
| `MEYOUWW_FIREBASE_API_KEY` | Firebase Web API key |
| `MEYOUWW_FIREBASE_SENDER_ID` | Firebase project number |

Then **Actions → CI → Run workflow**. The new `meyouww-debug-apk` artifact is connected to your backend.

These six values are not secrets in the security sense (they ship inside every copy of the app, and Row Level Security protects the data), but keeping them in GitHub secrets keeps them out of the public repository.

For Android Studio builds on your own computer, put the same six values in `local.properties` (see `local.properties.example`).

## 7. Install on a phone

1. GitHub → **Actions** → the latest green **CI** run → **Artifacts → meyouww-debug-apk**. Unzip it.
2. Copy the `.apk` to the phone and open it. Android asks to allow installs from that app once.
3. Open meyouww → sign in → **Profile and settings → Background delivery** → work through the checklist → **Test closed-app delivery**.

## Limits of the free plan

- Supabase Free projects **pause after 7 days without activity** and must be restored from the dashboard. While paused nothing works, including closed-app delivery. Two people using the app daily keep it awake. Consider Pro (about US$25/month) before a public launch.
- No backups and no uptime guarantee on the free plan.
- Firebase Cloud Messaging is free. Don't enable Firebase Cloud Storage (it now needs the paid Blaze plan); meyouww stores media in Supabase.
