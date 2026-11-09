# Status

_Updated 9 November 2026._

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

- Wallpapers and doodles are stored privately (and deleted once delivered) but not end-to-end encrypted, and wallpaper commands are not signed yet.
- Your own second phone doesn't receive copies of chat messages you send.
- Group chats.
- The closed-app device matrix (brief §24.4) has not been run on real phones; no results are claimed.

## v0.17.0: media tabs, update pop-up, Google-only, one phone at a time (9 Nov)

- Tap a friend's name in a chat, then "Media, docs & links": every photo, video, document, wallpaper, doodle and link from that chat.
- New versions show a Whity pop-up with everything that's new; "Update now" installs it right from the app.
- After updating, a pop-up lists every change since the version you had.
- Sign in with Google only.
- One phone at a time: signing in on a new phone signs the old one out.

## v0.16.0: photos, videos, PDFs, online status and new stickers (9 Nov)

- Chats send photos, videos and PDFs end to end encrypted (AES-256-GCM per file, key inside
  the Signal message; see E2EE.md). Save to phone for each. Needs migration
  `20261109150000_chat_media.sql` and the push-dispatch bundle (sweeps unfetched files).
- Online / last seen / typing for friends, a typing Whity in the chat and a tiny peeking
  Whity while they have your chat open. Needs migration `20261109160000_presence.sql`.
- 160 new Whity stickers in 16 packs, with a tabbed sticker picker.
- Swipe-to-reply no longer leaves a bubble stuck halfway or bouncing.
- Notifications: a bare "New message" left by a receipt-only push is removed (now and again
  a few seconds later); opening the app clears meyouww's notifications; while a chat is open
  and your messages aren't seen yet, receipts are fetched every 3 s so the lights keep up.

## v0.15.1: lock-screen notifications and phone sound (9 Nov)

- Chat, wallpaper and inbox notifications show in full on the lock screen.
- Ring / vibrate turn Do Not Disturb off first when the app has DND access, and the
  phone gets a moment to apply the switch before it counts as refused.
- Silent receipts need the *receiving* phone on v0.15+: a v0.13 partner still sends
  them the old way. Server side deployed 9 Nov (migration + push-dispatch `a92414f`).

## v0.15.0: smoother chat and navigation (9 Nov)

- Chat keeps the newest message in view when the keyboard or the + tray opens; the
  tray fits its contents; swipe a bubble right to reply.
- Home, People and Create are swipeable pages; every screen change is a full-width
  slide (no more two screens showing at once during the back gesture).
- People: search, Add friend and Requests live in a pop-up behind a + button.
- Chat notifications show the sender and the 4 newest messages. Receipts, reactions,
  edits and saves are sent "silent": no New message for them. Server side needs
  migration `20261109120000_silent_chat_push.sql` and the new `push-dispatch` bundle;
  until deployed, the app falls back to the old call.
- Save to phone for chat pictures; frame a new profile photo in a circle (pinch, drag).
- More clouds and hearts in the sky.

## v0.14.0: crayon UI refresh (9 Nov)

- Builds on v0.13.0: persistent local chats, optional disappearing chats and
  confirmed local chat clearing all retain their existing behavior.
- Refreshed shared cards, buttons, spacing, headings and navigation motion;
  improved onboarding, Home/calendar, People/chat, settings and wallpaper studio
  layouts, including narrow-screen and large-text handling.
- Wallpaper preview opened from a chat stays with that chat's recipient.
  Standalone Create keeps its destination picker.
- Backend, storage, encryption, notification and delivery logic are unchanged
  from v0.13.0. Validation/release progress is in `CODEX_CHECKPOINT.md`.
- CI uses fresh transformed classes to avoid missing Hilt bindings. Native
  previews cover normal and 200% text; large-text button labels grow to fit.

## v0.13.0: chats stay on the phone (9 Nov)

- **Chats are kept on the phone by default.** Messages no longer vanish after being seen. They stay in the phone's encrypted chat database until the person deletes them. The server is unchanged: it only ever holds encrypted envelopes and deletes each one as soon as it reaches the phone (undelivered ones expire after 7 days).
- **Settings → Chats → Disappearing chats** (off by default) brings back the v0.9 Snapchat style for this phone only. *Save in chat* and the "Saved" label only show when it's on.
- **⋯ → Clear chat on this phone** (with a confirmation) deletes one chat's messages from this phone only.
- Chats are still lost if the app is uninstalled or the phone is reset: the chat database and its keys never leave the phone (no cloud backup, by design).

## v0.12.2: phone sound works; light only (8 Nov)

- **Ring / vibrate / mute never reached the other phone.** FCM answered every phone-sound push with 400 INVALID_ARGUMENT (seen in `private.push_log`): the payload carried the sender's ID under `from`, a key FCM reserves. It is now `sender` (`20261108150000_ringer_sender_key.sql`, `visible.ts`, the app), and `push-dispatch` refuses reserved keys.
- **Light only**: the phone's dark mode no longer turns the app dark.

## v0.12.1: chat opens again (8 Nov)

- v0.12.0 crashed when a chat was opened: a stale Gradle build-cache entry left `feature:chat`'s ViewModels (chat list, chat, safety number) out of Hilt's graph, which only fails at runtime. Releases are now built without the build cache, KSP always runs fresh, and `scripts/check_apk_hilt.py` fails CI and the release if any declared class, Hilt ViewModel or worker is missing from the APK.

## v0.12.0: a colourful crayon chat (8 Nov)

- **Chat redesign.** Crayon speech bubbles with tails and comic shadows, coloured by the relationship (lilac friend, yellow best friend, pink girlfriend / boyfriend), faint clouds / stars / hearts behind the messages, day headings on tape, grouped runs, reactions tucked under the bubble, a squish on press, messages sliding in. Wallpapers show as taped-in polaroids; ones from before v0.11 show the phone's Undo copy if it still has it.
- **Tap the name** for a sheet: full photo, Make Best Friend / Girlfriend / Boyfriend (answer or take back an invite), Ring / Vibrate / Mute for a partner, mute, safety, profile.
- **Smoothness.** GitHub builds use a non-debuggable `fast` build type (release speed, shared test key); the app asks for the display's fastest mode (90 / 120 Hz); decoded profile pictures are cached; screens slide and the predictive back gesture plays the pop animation.
- A debug-only `DesignPreviewActivity` lets the emulator smoke test screenshot the chat (`20_chat_*` on `ci-screens`).

## v0.11: instant wallpapers, three levels (8 Nov)

- Wallpapers, chat messages, invitations and alarms are processed inside the push itself (WorkManager is only the fallback), so a wallpaper goes on within seconds even with the phone locked. Every wallpaper is kept on the phone and shown in the chat; ticks became a red / yellow / green traffic light.
- Three levels: friend (everything asks first), best friend (alarms and reminders set themselves; wallpapers ask), girlfriend / boyfriend (all automatic, plus switching the phone to ring / vibrate / mute). One partner at a time, and they must accept. See `DECISIONS.md`.

## v0.10.0: the Kuchhu-chan look, automatic wallpapers, one-go permissions (8 Nov)

- **New look, from the owner's Kuchhu-chan birthday site** (`69nightmare/Kuuchuuu`): a sky-blue world with soft clouds (a starry indigo night in dark mode), cream paper cards with thick crayon outlines, fat pill buttons with a hard ink shadow they press into, titles on pink washi tape, party bunting on Home and Welcome. Fonts: Mochiy Pop One (headings, buttons), Zen Maru Gothic (body), Gochi Hand (handwriting). Contrast rules still tested.
- **The sender picks the screen** (home, lock or both) and the wallpaper goes on by itself for every friend; the receiver is always told, with Undo and Pause, and can still choose Ask first, Pause or Never per friend. Existing friendships were switched to automatic.
- **First launch asks for everything in one go**: notifications, the battery dialog, exact alarms (granted at install on Android 13+ via `USE_EXACT_ALARM`) and full-screen alarms, back to back from one "Allow all" button.
- **Google sign-in reads birthday and gender** (Google's consent screen, People API): adults skip the age question, under-18s are stopped, gender goes to the profile (private, editable in Settings). Email sign-in asks the age question after sign-in. The People API is enabled on `meyouww-a4bb1`; these are sensitive scopes, so until Google verifies the app people see an "unverified app" notice and at most 100 accounts can grant them.

## v0.9.0: nothing kept on the server, simpler studio (8 Nov)

- **Snapchat-style chat.** Messages disappear from both phones once they've been seen and the chat is left; long-press → *Save in chat* keeps one for both people (the save travels inside the encryption). The server only ever held encrypted envelopes, deleted on delivery; undelivered ones now expire after 7 days instead of 30. Saved messages live only in each phone's encrypted database.
- **Wallpapers and doodles leave the server.** The recipient's phone deletes the picture right after downloading it, and a Not now deletes it too; anything left (expired, cancelled, failed) is swept by `push-dispatch` through the Storage API (`20261108110000_ephemeral_media.sql`).
- **Home screen, lock screen or both.** When a friend's wallpaper needs your OK, the notification has *Home screen / Lock screen / Both* buttons, and the inbox asks where to put it, with "let them change it without asking next time". The studio's *Use it* page has *Set as my wallpaper* with the same choice.
- **Quicker sending.** Pictures go as WebP instead of PNG (a fraction of the size).
- **Simpler doodle studio.** One row of tools (Draw, Eraser, Shapes, Text, Stickers, Photo, Move), brush style and size only when drawing, extras (background, save, share, clear) in the ⋯ menu, and one *Next* button.
- **Fits the phone.** Screens use tighter spacing on small phones and a centred column on tablets and unfolded foldables; the preview phone and chat bubbles size themselves to the screen.
- **APK size:** 648 MB → 58 MB (debug, all four ABIs); the release APK carries only the two phone ABIs.

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
