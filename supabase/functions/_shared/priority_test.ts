import { choosePriority, ttlSeconds } from "./priority.ts";

function assertEquals<T>(actual: T, expected: T, msg: string) {
  if (actual !== expected) throw new Error(`${msg}: expected ${expected}, got ${actual}`);
}

const ready = { notifications_enabled: true, channels: { "delivery_status_v1": 2, "messages_v1": 4 } };

Deno.test("high priority only when a notification will be shown", () => {
  assertEquals(choosePriority(true, ready, "messages_v1"), "high", "ready device");
  assertEquals(choosePriority(false, ready, "messages_v1"), "normal", "silent sync");
});

Deno.test("notifications off or unknown state means normal priority", () => {
  assertEquals(choosePriority(true, { notifications_enabled: false, channels: {} }, "messages_v1"), "normal", "off");
  assertEquals(choosePriority(true, null, "messages_v1"), "normal", "never reported");
  assertEquals(choosePriority(true, { channels: {} }, "messages_v1"), "normal", "flag missing");
});

Deno.test("a channel the person turned off means normal priority", () => {
  const state = { notifications_enabled: true, channels: { "messages_v1": 0 } };
  assertEquals(choosePriority(true, state, "messages_v1"), "normal", "channel off");
  // A channel not created yet is not treated as off.
  assertEquals(choosePriority(true, state, "wallpapers_v1"), "high", "channel unknown");
});

Deno.test("ttl is clamped to FCM limits", () => {
  assertEquals(ttlSeconds(10_000, 0), "10s", "simple");
  assertEquals(ttlSeconds(0, 10_000), "0s", "already expired");
  assertEquals(ttlSeconds(100 * 86_400_000, 0), "2419200s", "max 28 days");
});
