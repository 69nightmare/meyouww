# Codex task checkpoint

Updated: 9 November 2026. Current target: **v0.15.0** (v0.14.0 published).

## Current resume state (read this first)

- Implementation complete, including owner's v0.13 logic. The sections below
  are reference/history; `.git/CODEX_RELEASE_STATE.md` has the latest live outcome.
- Remote UI branch `codex/crayon-people-ui`, local `codex/full-app-crayon-refresh`,
  PR #2. Owner release baseline `9ad384f` is merged. Version 0.14.0 (19).
- UI checkpoint `40d5359`, version integration `48b9cbb`, CI cache fix `7da39fe`.
  Final large-text button correction follows these; inspect HEAD for its commit.
- CI `42595402285` on `7da39fe`: 113 Android unit tests (zero failures), lint,
  debug APK and every-class/Hilt packaging check PASS. Backend job PASS.
  Emulator test was still running when this committed checkpoint was written.
- Real native previews rendered and visually inspected: Welcome, partner chat,
  shared controls and 320dp/200% text. Fixed clipped large-text button labels by
  converting one line's sp height before multiplying (Android nonlinear scaling),
  and using minimum height. Corrected previews compile and render successfully.
- Final CI and release job must finish before reporting publication success.
  Verify https://github.com/69nightmare/meyouww/releases/tag/v0.14.0 and its APK
  plus SHA256. No real-phone/account-flow or exact Figma-parity testing claimed.
- **Do not upload the old local fast APK:** it compiled but contains stale alarm
  bindings from old outputs. Use GitHub's fresh release checkout and mandatory
  packaging check. CI cache is now disabled, matching release builds.
- Resume: fetch/check remote changes, read local release-state file, inspect runs,
  finish/verify publication if needed, save the outcome and return download links.
  Push/publication and SDK license acceptance are already authorized.

## RESUME HERE (9 Nov 2026, night)

- Released: v0.16.0 (`791e646`): chat media incl. Word files, presence (online/typing/peek
  Whity), 160 stickers in packs, reply-swipe fix, notification tidy/clear-on-open, receipt
  polling. Backend for it is live (chat_media + presence migrations, push-dispatch cffe89f4).
- In progress: v0.17.0 (versionCode 23): Media/Docs/Wallpapers/Links tabs (ChatGallery.kt,
  from the name sheet), update pop-up (UpdateBanner.kt: lists /releases incl. pre-releases,
  shows "What's new" from the release body, downloads + SHA-256 check + installer; needs
  REQUEST_INSTALL_PACKAGES; release-apk.yml copies the STATUS.md section into the notes),
  Google-only sign-in (email form only when Google isn't configured), one phone at a time
  (migration `20261109170000_single_device.sql` + pgTAP 15; claim after sign-in, device_active
  check on start/resume; replaced phones stay revoked).
- Deploys: the auto-mode classifier blocks Claude's clicks on Run/Deploy in the Supabase
  dashboard. Claude loads the SQL/code (Monaco model from GitHub raw, verified) and the owner
  clicks Run / Deploy. Read-only verification queries are fine.
- Next: v0.18 nightly Google Drive backup (appDataFolder, per-user key kept by the server, no
  password; restore offer after sign-in; reminder before sign-out; needs Drive API enabled in
  GCP for the whity1212020 project), v0.19 group chats (reminders for all, polls, media).
- Owner's extra sticker sheet ("ayega / modi / Diwali", 2x4) still needs saving as a file.

## After v0.14.0 (9 Nov 2026, Claude session)

- v0.14.0 published by Release APK run `42597142484` (APK + SHA256 verified).
- Owner's next requests, done on `codex/full-app-crayon-refresh` (not released yet):
  chat follows the newest message when the keyboard/tray opens; plus tray fits its
  content; People search/Add friend/Requests moved into a + pop-up; full-width screen
  slides (screens are see-through, so short slides + fades overlapped); Home/People/
  Create are one swipeable pager (`TabsRoute`); swipe a bubble right to reply; more
  clouds and hearts in the sky; Save to phone for chat pictures (`MediaSaver`);
  profile photo framing dialog (circle guide, pinch, drag; `AvatarCrop`).
- Notifications: receipts/reactions/edits/saves are sent with `p_silent` and get no
  "New message" (app placeholder skipped; push-dispatch drops the visible block).
  Decrypted chat notifications show the 4 newest messages under the sender's name.
  Deployed 9 Nov: migration `20261109120000_silent_chat_push.sql` and `push-dispatch`
  from `a92414f` (silent pushes also go at normal FCM priority). Verified live.
- Still to build (backend + E2EE, owner asked): photo/video/PDF in chats (encrypted
  upload to a private bucket, key inside the Signal payload, Room v3 column), then
  group chats (fan-out envelopes, members table, group reminders for all, polls,
  media). Supabase/Firebase/GCP use the owner's whity1212020 Google account.

## Reference and earlier checkpoints

## User request and authorization

- Refresh the whole Android UI, animation and component placement using the
  shared design brief, while preserving backend/business logic.
- Fix wallpaper preview opened from a chat showing other friends: lock it to
  that chat's recipient; standalone Create can still choose a destination.
- The owner published v0.13.0 while this work was in progress. **Integrate that
  updated logic first and release this UI work as v0.14.0**, never replace v0.13.
- Code push and APK publication to GitHub are explicitly authorized.
- Android SDK license acceptance and building are explicitly authorized.
- Save/update this checkpoint so continuation does not need the full chat history.

## Source and design

- Repository: https://github.com/69nightmare/meyouww
- Local work branch: `codex/full-app-crayon-refresh`.
- Original UI base: `ac6e6c8dbbc57b33d6a16f137ce1e6647aa15487`, existing draft PR #2
  (`codex/crayon-people-ui` into `release`). Includes combined People navigation,
  colorful calendar and chat actions.
- Latest owner release: `v0.13.0`, commit
  `9ad384f626180408f7e8b736e005137e97fc394e`, `origin/release` and `origin/phase-7`.
- Shared brief: https://chatgpt.com/share/6af0494d-7228-83e8-8f71-070e99610f46
- Figma: https://www.figma.com/design/Hn4110bLamnMYnvp0RRLHz/Meyouww-Crayon-App-Experience?node-id=0-1
- Figma detailed reads hit the account's Starter quota. The shared chat and
  overall canvas were reviewed; do not claim exact Figma parity.
- Look: pale sky, cream paper cards, colorful tape, existing Whity mascot and
  bundled fonts. Reduced-motion support, readable layouts and touch targets.

## Behavior to preserve from v0.13

- Chats stay in the encrypted local database by default until deleted.
- Settings > Chats > Disappearing chats is optional, per phone.
- Save in chat and saved indicators only show when disappearing chats is on.
- Clear chat on this phone confirms first and clears only that local conversation.
- Owner changes to ChatRepository, AppPreferences, ChatViewModel and settings
  ViewModel must remain intact. Server envelope deletion/encryption is unchanged.
- Do not change Supabase/Firebase configuration, workers, permissions, data models,
  rendering/export, alarm scheduling, notification delivery, or backend policies.
- Standalone alarms are separate from calendar events by design.

## UI work already implemented

- Shared rounded cards/buttons/chips, quieter outlines/shadows, press motion,
  larger text handling and sticky headers (scroll normally on compact heights).
- Navigation transitions/colored tabs; Home/calendar, People/chat/action trays,
  friend detail, onboarding, settings/delivery/licenses, alarm/history/draft and
  studio/preview layout improvements. Existing callbacks retained.
- `PreviewRecipientUi.kt` prevents a chat preview from selecting another friend
  and suppresses sending until the matching recipient is loaded.
- Five regression tests in `PreviewRecipientUiTest.kt`.
- Debug-only real Compose previews in `CrayonUiPreviews.kt` (Welcome, partner
  chat, shared components, 200% font). No mock production account/data changes.

## State at this checkpoint

- UI checkpoint committed as `40d5359`; owner's v0.13 merged successfully.
  No conflicts. Version is now 0.14.0 / versionCode 19. Current UI commit
  `48b9cbb785a7794afa46ad14d7a856dc6f0d6443` is pushed to
  `codex/crayon-people-ui`; PR #2 is updated. Release branch remains at v0.13.
- Protected backend/data/encryption/rendering paths and ChatViewModel are byte
  identical to v0.13. The new settings switch and clear-chat confirmation remain
  connected in the refreshed UI.
- The old build log is named `build-v0.13.0.log`; it is NOT the owner's release.
  Latest local build compiled most source but failed resolving dependencies due
  to `No route to host` from Google's Maven repository. Not a successful APK.
- Before this merge: string-resource check, whitespace check, unchanged backend
  and ViewModel audit, syntax and palette checks passed. 70 core model/scene unit
  tests passed. Do not claim post-merge Android lint/build/tests until rerun.
- Post-merge audit: 28 changed Kotlin files have no new syntax errors; existing
  class bodies (including every ViewModel) match v0.13. Protected paths, palette,
  strings and whitespace checks pass.
- GitHub CI run `42594592824` completed for `48b9cbb`; backend, all unit tests,
  Android lint and compilation passed. APK check caught missing Hilt creation of
  AgeGate/ProfileSetup/SignIn ViewModels, the project's previously documented
  stale build-cache failure. Smoke test skipped. CI now uses `--no-build-cache`
  for every Gradle invocation, matching the release workflow; revalidation pending.
  https://github.com/69nightmare/meyouww/actions/runs/42594592824
- Local build session `35830` is active, output persists in
  `%TEMP%/meyouww-build-tools/build-v0.14.0.log`. It uses NDK stripping and the
  official Gradle. Quote dotted `-P`/`-D` arguments in PowerShell.
- Android CLI is available at the temporary SDK's `cmdline-tools/latest/bin/android.exe`.
  Real preview rendering (after the current build): `android preview render :app
  --fqn=com.meyouww.app.CrayonUiPreviewsKt.WelcomePhonePreview` (and PartnerChatPhonePreview,
  PaperComponentsPhonePreview, PaperComponentsLargeTextPreview), `--component-tree`
  and `--output` to a temporary directory. Gradle wrapper distribution is cached.

Later local notes: session 35830 finished; local.properties now escapes `C\:`.
Build variants one at a time with `--no-parallel --max-workers=2` to reduce local
annotation-processing interference. `render-previews.py` persists real PNG/JSON
files under the temporary build tools' `ui-previews-v014` directory.

## Build environment and next steps

1. Fetch remote refs; inspect any newer owner changes. Preserve local work in git.
2. Integrate v0.13, bump to 0.14.0 (19), review combined behavior and update docs.
3. Build/test/lint with JDK 21 and local Gradle 9.8.2. Temporary build tools live
   at `%TEMP%/meyouww-build-tools`; `run-gradle.py LOG TASKS...` persists output.
   Official SDK 37, build tools 36/37 and NDK 30 are installed under its `sdk`.
   Ignored `local.properties` points there. Keep machine paths out of tracked config.
4. Use `--no-build-cache :app:assembleFast -Pmeyouww.abis=arm64-v8a,armeabi-v7a`
   for the phone APK. Run Android lint and design-system/studio unit tests.
   Render the real Compose previews if available; don't claim device testing.
5. Run `scripts/check_strings.py`, `git diff --check`, and
   `scripts/check_apk_hilt.py` on the APK. Verify version, signature, ARM ABIs,
   size under 120 MiB, checksum. Preserve shared test signing key for upgrades.
6. Push UI branch, update PR #2, integrate into `release` without overwriting
   newer owner commits. Release branch push triggers Release APK automatically.
   Avoid also pushing a tag, which starts a duplicate release job.
7. Monitor GitHub release job to completion. Verify v0.14.0 APK and checksum,
   then report release/download links and update this checkpoint with outcome.

The release workflow can overwrite assets for its version. Do not manually upload
while that workflow is running. Backend deploy only triggers on main/backend
changes; this task needs no backend deployment. GitHub CLI is authenticated for
the correct owner; never print tokens. No agents are required to resume the work.
