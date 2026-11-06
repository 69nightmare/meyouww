# meyouww Privacy Policy (draft)

> **Draft for review.** This is a working draft written alongside the code, not legal advice. Before any public release the owner must add a grievance officer contact, review it with a lawyer for India's Digital Personal Data Protection Act and Rules, and publish the final text.

_Last updated: 6 November 2026 (covers app version 0.1, "Phase 1")._

## Who we are

meyouww is a private app for chatting, doodling and sharing wallpapers, reminders and alarms with people you choose. It is for adults (18+).

## What we collect today

| Data | Why | Where it's stored |
|---|---|---|
| Email address | To send your 6-digit sign-in code and let you recover your account | Supabase (Mumbai region) |
| Birth year and your confirmation that you are 18+ | To keep meyouww adults-only. We never ask for your full birthday | Supabase |
| Your ID (username), display name and optional nickname | So friends can find and recognise you | Supabase |
| A random installation ID, phone maker, Android version and app version | To register this phone for notifications and to explain delivery problems | Supabase |
| Firebase push token | To deliver notifications when the app is closed | Supabase (server-only table) |
| Delivery settings: whether notifications, background data, battery use and exact alarms are allowed, Google Play services status | To decide how to send notifications and to show the "work like WhatsApp" checklist | Supabase |
| Background-delivery test times | To show you whether closed-app delivery works | Supabase |

We do **not** collect your location, contacts, photos you don't send, microphone or camera data, battery level, screen contents, advertising ID or hardware identifiers. There are no ads and no analytics SDKs.

## Who else processes data

- **Supabase** hosts the database, sign-in and server functions.
- **Google Firebase Cloud Messaging** delivers push notifications. Google sees your push token and when pushes are delivered. Push messages carry only opaque IDs, never message text, names or previews.
- **Our email provider** sends your sign-in codes and sees your email address.

## Your choices

- You control every permission on your own phone and can turn notifications off at any time in Android settings.
- You can sign out at any time; signing out stops notifications to that phone.
- Account deletion and data export will be available inside the app before public release. Until then, ask the owner to delete your account.

## Security

All connections use HTTPS. Your sign-in session is stored encrypted on your phone. Server access is protected by Row Level Security so each person can read only their own records. Chat end-to-end encryption arrives in a later version; until then the app does not claim it.

## Children

meyouww is not for anyone under 18. If we learn that an account belongs to someone under 18, we delete it.

## Contact and grievances

Grievance officer: _to be added by the owner before release._
