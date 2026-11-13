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

## v0.25.1: no more surprise sign-in screen when offline (13 Nov)

- Fixed: after a while offline (or when Android had paused meyouww's connection), opening the app could show the welcome and sign-in screens although you were still signed in. It now opens your chats and calendar as usual and catches up by itself once the internet is back.

## v0.25.0: Pastel Whity, a look switch, Google-only sign-in (13 Nov)

- A fresh pastel look: strawberry-milk pages with faint dots, white sticker cards, soft rounded fonts and one berry pink for the main thing to tap. Buttons are big pills that press down, the bottom bar floats and always shows its words, and Whity has one pose for each moment: hopping while things load, waiting at the door when a list is empty, facepalming when something goes wrong.
- Prefer the old crayon look? Profile and settings, Look, Crayon. It changes at once and stays on that phone.
- Sign-in is Google only, and there is no age form any more: Google tells meyouww your birthday (you allow it on Google's screen). Without a birthday from Google, meyouww can't let you in and says how to fix it.
- Fixed: the home-screen widget sometimes asked you to sign in when you were signed in (when the phone was offline for a while). It now keeps showing your chats.

## v0.24.0: Whity talks, dresses up and earns treats (12 Nov)

- Whity now says things in a little speech bubble: thanks when you earn her a treat, festival wishes, what's coming up on your calendar (tap her on Home), how your week went, and silly Whity facts. She chats by herself only now and then.
- Now and then she pops up from the bottom of Create, Inbox, Alarms and Friends to say hi, then ducks back down. Tap her bubble to send her off sooner. She never pops up while you're typing.
- Earn her treats by chatting with someone, sending a doodle, or adding a reminder or alarm: one of each a day. Her jar now holds six, and it still fills back up to three every morning.
- Her treats change: cookies, momos, ice cream and bone biscuits take turns, with laddoos at Diwali, gujiyas at Holi and cupcakes at Christmas and New Year.
- Open the app on days in a row to grow your streak (miss one day and she waits for you). Streaks unlock outfits that stay yours: a bow, a party hat, a flower crown, cool shades and a cosy beanie. Pick one in her corner.
- On festivals she dresses up by herself: diyas at Diwali, Holi colours, a Santa hat at Christmas, a party hat for New Year and a kite for Makar Sankranti, Basant Panchami and Independence Day.
- Her bounce, sway and wobble now move her outfit with her, and her animations are about half the size they were.
- Settings has a new switch, "Whity pops in to say hi", to keep her from popping up and chatting.
- Voice messages show a wave instead of a flat line. It fills in as the message plays, the bars at the play point bounce, and you can tap the wave to jump ahead or back. While you record, a live wave follows your voice.

## v0.23.0: Whity is your pet now (11 Nov)

- Whity lives in the app: she sits on the calendar on Home, in the top card on People, and peeks into a chat while your friend has it open.
- She moves her eyes and looks around. Tap her for a happy bounce with hearts, or stroke her with your finger to pet her: her eyes close, she blushes and the phone purrs. Lots of quick taps make her dizzy!
- Hold her to open Whity's corner: her mood, a treat jar with three treats a day (it refills every morning), and how often you've petted her. Give her a treat and watch her munch it.
- She gets heart eyes on People when you have new messages, hops in a chat when you send something, and dozes off at night or when nobody has played with her for a while. She's never sad.
- Settings has a new Whity card to hide her from your screens (or bring her back) and to open her corner.
- With your phone's animations turned off, she changes poses without moving.

## v0.22.3: a cuter cloud for the widget (11 Nov)

- The widget is now a soft, puffy cloud: round puffs on top, little scallops underneath, two tiny thought-bubble puffs, a cream-to-sky colour, a bit of shine and two sparkles.
- Everything inside (the logo, your pinned people and the buttons) still fits within the cloud at any widget size.

## v0.22.2: nicknames in every notification (11 Nov)

- The custom name you give someone now shows in all notifications: their messages (in groups too), wallpapers and doodles, alarms (also on the ringing screen), invites and requests, and phone-sound changes.
- Nicknames you gave before this update work straight away after opening the app once.

## v0.22.1: a tidier widget with your pinned people, and updates download once (11 Nov)

- Whity's cloud now fits your widget exactly: the meyouww logo, the text and the buttons all sit inside the cloud.
- Your pinned chats show as round photos in the widget. Tap one to open that chat; a red dot means something new.
- Chats and Doodle are now two round buttons in the corner.
- Tapping "Update now" starts one download and closes the pop-up. It no longer pops up again for the same version, so the update isn't downloaded two or three times.
- After that, the button on Home says "Open Downloads", which takes you to the file to install. "Download again" is there only if you need it.

## v0.22.0: Whity's cloud, a home-screen widget (11 Nov)

- New home-screen widget: a crayon cloud with Whity peeking over the edge. Long-press your home screen, tap Widgets, find meyouww and drag "Whity's cloud" out.
- It shows how many new messages you have and from whom, plus your next alarm or reminder.
- Whity's face changes: love eyes for new messages, a wave when you're all caught up, a cosy hoodie at night.
- Tap the message line to open that chat. On the taller size, "Chats" and "Doodle" buttons jump straight there.
- Make it small for just Whity and the count, or bigger for the buttons.

## v0.21.0: voice messages, photos in notifications, a tidier People screen (11 Nov)

- Voice messages: with nothing typed, the red button becomes a microphone. Tap it, talk, then tap send (or the bin to throw it away). Up to 5 minutes, end-to-end encrypted like photos.
- Tap a voice message to play or pause it; only one plays at a time.
- Notifications show the sender's profile photo instead of the first letter of their name.
- People: the big search box is gone. The round button at the top searches; "New group" and "Friends & requests" sit just below.
- Friends need v0.21.0 to hear voice messages; older versions skip them.

## v0.20.1: chats open at the newest message, links you can tap with previews (11 Nov)

- Opening a chat now lands straight on the newest message, without first showing the oldest ones and scrolling down.
- Links in messages are underlined and open when you tap them.
- A link you send shows a preview card with the page's title, a short description and its picture. Your phone makes the card and sends it encrypted, so your friend's phone never visits the site unless they tap.

## v0.20.0: chat backup to Google Drive, and updates through your browser (11 Nov)

- Chat backup: Settings, Chat backup saves your chats in a hidden folder of your own Google Drive. Tap "Back up now", or turn on "Back up every night".
- The backup is locked before it leaves the phone. Google can't read it, and meyouww's server never has it.
- Photos, videos and documents are included if you want; only new ones are sent each time.
- New phone or fresh install: sign in, open Settings, Chat backup, and tap "Restore from Google Drive". Pins, stars and nicknames come back too.
- Signing out reminds you to back up first.
- Updates: "Update now" opens the download in your browser, then you tap the file to install. meyouww no longer installs updates by itself, because Google Play Protect blocks apps that do.

## v0.19.0: pins, stars, search, nicknames, sharing into meyouww, a fuller calendar (10 Nov)

- Pin up to 3 chats to the top: press and hold a chat in People, or use the menu inside a chat.
- Star a message (press and hold it) and find all your stars under the chat's name, in "Media, docs & links".
- Search your chats by name on the People screen, and search the words inside a chat from its menu.
- Give a friend a nickname only you can see: tap their name in the chat. Change it as often as you like.
- Change your own name and ID in Settings, once every 15 days. Your ID stays unique.
- Share photos, videos, PDFs and Word files straight from other apps: meyouww now shows up in the share sheet.
- The calendar shows Indian holidays, a bell on days with a reminder and a clock on days with an alarm. Tap a day to see all of them.
- Reminders now come 10 hours, 2 hours and 1 hour before, and when it starts.
- Setting an alarm asks you to confirm what, when and for whom first.

## v0.18.1: notifications with names again, instant sending, sticker replies (10 Nov)

- Notifications: when the phone wakes the app for a message, a failed first try is now repeated straight away instead of leaving a bare "New message".
- A "delivered" or "read" tick can no longer turn into an empty "New message" on your friend's phone.
- Messages leave the moment you tap Send, and the red, yellow and green lights follow faster.
- Nothing is marked as read while the app is closed or in the background.
- You can reply with a sticker.
- The little peeking Whity in a chat now sits on the left.
- Settings, Background delivery has a new "Push diary" that shows what happened to recent notifications, to help find problems.

## v0.18.0: group chats with polls and reminders for everyone (9 Nov)

- Group chats: tap + on People, then "New group 👥", pick friends and give it a name. Up to 32 people.
- Send texts, stickers, photos, videos and documents to the whole group, end to end encrypted for every person's phone.
- Polls: ask a question with up to 8 choices; everyone taps to vote and sees the results live.
- Reminder for all: pick what and when, and it goes into everyone's calendar with a reminder at that time.
- Each person's name shows above their messages, and notifications say who wrote what.
- Tap the group's name to see who's in it, add or remove people (admins), rename it, mute it, or leave.
- The traffic light turns yellow once everyone has your message and green once everyone has seen it.
- No more empty notifications when a friend on an old version gets or reads your message.

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
