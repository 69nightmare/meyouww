import { visibleFor } from "./visible.ts";
import { errorCode } from "./fcm.ts";

function assertEquals<T>(actual: T, expected: T, msg = "") {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${msg} expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

const ID = "bbbbbbbb-0000-0000-0000-000000000002";

Deno.test("a friend request shows the app's generic friend-request text", () => {
  assertEquals(visibleFor({ type: "activity", activity_id: ID, kind: "friend_request" }, "friends_v1"), {
    titleLocKey: "activity_friend_request_generic",
    bodyLocKey: "activity_tap_to_see",
    channelId: "friends_v1",
    tag: `activity:${ID}`,
  });
});

Deno.test("an unknown activity kind falls back to the generic title", () => {
  assertEquals(visibleFor({ type: "activity", activity_id: ID, kind: "made_up" }, "friends_v1")?.titleLocKey, "activity_unknown_title");
});

Deno.test("automatic wallpapers and messages get their own tags", () => {
  assertEquals(visibleFor({ type: "wallpaper", command_id: ID }, "wallpapers_v1")?.tag, `wallpaper:${ID}`);
  assertEquals(visibleFor({ type: "message", conversation_id: ID }, "messages_v1")?.tag, `chat:${ID}`);
});

Deno.test("receipts and reactions wake the app without a New message notification", () => {
  assertEquals(visibleFor({ type: "message", conversation_id: ID, silent: "1" }, "messages_v1"), null);
});

Deno.test("sync and test pushes stay silent", () => {
  assertEquals(visibleFor({ type: "sync" }, "friends_v1"), null);
  assertEquals(visibleFor({ type: "activity" }, "friends_v1"), null);
});

Deno.test("only FCM's error code is kept from an error reply", () => {
  const reply = JSON.stringify({
    error: {
      code: 403,
      message: "SenderId mismatch for token abc",
      status: "PERMISSION_DENIED",
      details: [{ "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode: "SENDER_ID_MISMATCH" }],
    },
  });
  assertEquals(errorCode(reply), "SENDER_ID_MISMATCH");
  assertEquals(errorCode(JSON.stringify({ error: { status: "UNAUTHENTICATED" } })), "UNAUTHENTICATED");
  assertEquals(errorCode("<html>bad gateway</html>"), "UNKNOWN");
});

Deno.test("a phone-sound change names its sender key 'sender' (FCM reserves 'from')", () => {
  assertEquals(visibleFor({ type: "ringer", mode: "vibrate", sender: ID }, "friends_v1")?.tag, "ringer");
  assertEquals(visibleFor({ type: "ringer", mode: "vibrate" }, "friends_v1"), null);
});
