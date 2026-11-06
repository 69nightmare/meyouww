# End-to-end encryption in meyouww chat

meyouww chats use the **Signal Protocol** through Signal's own library,
[libsignal](https://github.com/signalapp/libsignal) (`org.signal:libsignal-android` 0.86.5,
AGPL-3.0). meyouww does not implement any cryptography itself.

## What is protected

| | Who can read it |
|---|---|
| Message text, stickers, replies, reactions, edits, deletions | Only the phones in the chat |
| Delivered / read receipts | Only the phones in the chat (they travel as encrypted messages) |
| Safety numbers | Computed on each phone from both identity keys |

Messages are encrypted per recipient phone with the Double Ratchet. New chats start with
PQXDH: each phone publishes an elliptic-curve signed prekey **and** a Kyber-1024 prekey, so
the first message is also protected against future quantum computers.

## What the server sees

The server is untrusted for content but still learns:

- who chats with whom, and when;
- how many messages, and their approximate sizes (plaintext is padded to 160-byte blocks
  before encryption, as Signal does, to blur this);
- which phones belong to an account and their push tokens;
- public keys (identity key, signed prekeys, one-time prekeys).

Ciphertext is deleted as soon as the recipient phone acknowledges it, and after 30 days
if it never does.

## Keys and storage

- Each phone has its own **identity key pair**, created on first use. The private key never
  leaves the phone.
- Private keys, sessions and decrypted messages live in a **SQLCipher** database
  (`meyouww_chat.db`). Its 32-byte passphrase is stored encrypted by a Tink key held in
  Android Keystore, and is excluded from backups.
- The phone publishes 50 one-time EC prekeys and 50 one-time Kyber prekeys at a time and
  tops them up when fewer than 15 remain. A Kyber "last resort" key covers the gap if they
  run out. The signed prekey rotates every 30 days.
- If another account signs in on the same phone, the previous account's keys and chats are
  wiped first.

## Trust and verification

- **Trust on first use.** The first identity key seen for a friend's phone is accepted.
- If it later changes (reinstall, new phone), the message still arrives, the chat shows
  "Riya's safety number changed", and any earlier verification is cleared.
- **Safety numbers.** Each chat has a 60-digit number (libsignal's
  `NumericFingerprintGenerator`, 5200 iterations, version 2, using account IDs). Compare it
  aloud, or scan the other phone's QR code to mark the chat as verified.

## Delivery

1. The sender claims one prekey bundle per recipient phone (one-time keys are removed as
   they are handed out) and starts a session with any phone it has none for. libsignal
   checks the signatures on the signed prekey and the Kyber key.
2. The message is encrypted once per recipient phone and sent with `send_message`. The
   server requires a copy for **every** active phone of the recipient, so a newly added
   phone can't silently miss messages.
3. The server stores the ciphertext and pushes `{type: "message", conversation_id}`. The
   push carries no content; a generic "New message" notification is shown even if the
   app was stopped.
4. The recipient phone fetches, decrypts, stores and acknowledges. The server deletes the
   ciphertext.

## Known limits

- Your **own** other phones don't get copies of messages you send (no linked-device sync
  yet). Most people use one phone.
- Group chats are not built yet.
- Wallpapers and doodles sent to a friend's screen are **not** end-to-end encrypted yet.
  They are stored privately (only the sender and recipient can read them, over HTTPS), but
  the server could read them. Encrypting them, and signing wallpaper commands, are planned
  next (brief §10.4, §11.8).
- Message notifications show the sender's name and text in the shade (like WhatsApp). The
  lock screen shows only "New message".

## Licence

Because meyouww links libsignal (AGPL-3.0), meyouww is distributed under the
**GNU Affero General Public License v3.0** (see `LICENSE`). The full source is public at
https://github.com/69nightmare/meyouww.
