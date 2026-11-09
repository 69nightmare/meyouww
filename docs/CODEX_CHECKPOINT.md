# Codex task checkpoint

Updated: 9 November 2026. Current target: **v0.14.0**.

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
  No conflicts. Nothing pushed yet. Version is now 0.14.0 / versionCode 19.
- Protected backend/data/encryption/rendering paths and ChatViewModel are byte
  identical to v0.13. The new settings switch and clear-chat confirmation remain
  connected in the refreshed UI.
- The old build log is named `build-v0.13.0.log`; it is NOT the owner's release.
  Latest local build compiled most source but failed resolving dependencies due
  to `No route to host` from Google's Maven repository. Not a successful APK.
- Before this merge: string-resource check, whitespace check, unchanged backend
  and ViewModel audit, syntax and palette checks passed. 70 core model/scene unit
  tests passed. Do not claim post-merge Android lint/build/tests until rerun.

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
