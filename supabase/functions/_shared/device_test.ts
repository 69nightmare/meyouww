import { sanitizeState, validInstallationId, validPushToken } from "./device.ts";

function assert(condition: boolean, msg: string) {
  if (!condition) throw new Error(msg);
}

Deno.test("state keeps known fields and drops the rest", () => {
  const s = sanitizeState({
    notifications_enabled: true,
    channels: { messages_v1: 4, "bad id!": 3, wallpapers_v1: "x" },
    data_saver: "on",
    play_services: "nonsense",
    location: "secret",
  })!;
  assert(s.notifications_enabled === true, "notifications flag");
  assert(JSON.stringify(s.channels) === JSON.stringify({ messages_v1: 4 }), "channels filtered");
  assert(s.data_saver === "on", "data saver kept");
  assert(s.play_services === null, "invalid enum dropped");
  assert(!("location" in s), "unknown field dropped");
});

Deno.test("non-object state is rejected", () => {
  assert(sanitizeState(null) === null, "null");
  assert(sanitizeState([1, 2]) === null, "array");
  assert(sanitizeState("x") === null, "string");
});

Deno.test("installation IDs and tokens are validated", () => {
  assert(validInstallationId("0b7c0f0e-5a8e-4d2b-9b39-9f6f3c2b1a77") !== null, "uuid ok");
  assert(validInstallationId("short") === null, "too short");
  assert(validInstallationId("drop table;--x") === null, "odd characters");
  assert(validPushToken("a".repeat(30)) !== null, "token ok");
  assert(validPushToken("a b".repeat(10)) === null, "whitespace");
  assert(validPushToken("abc") === null, "too short");
});
