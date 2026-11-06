import { parseDispatch, sameSecret, validUuid } from "./dispatch.ts";

function assertEquals<T>(actual: T, expected: T, msg = "") {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${msg} expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

const USER = "aaaaaaaa-0000-0000-0000-000000000001";

Deno.test("a well-formed activity push is accepted", () => {
  const parsed = parseDispatch({
    user_id: USER,
    data: { type: "activity", activity_id: "bbbbbbbb-0000-0000-0000-000000000002", kind: "friend_request", v: "1" },
    channel: "friends_v1",
  });
  assertEquals(parsed?.userId, USER);
  assertEquals(parsed?.data.kind, "friend_request");
  assertEquals(parsed?.channel, "friends_v1");
});

Deno.test("numbers and booleans become strings for FCM", () => {
  const parsed = parseDispatch({ user_id: USER, data: { type: "sync", n: 3, ok: true } });
  assertEquals(parsed?.data, { type: "sync", n: "3", ok: "true" });
});

Deno.test("unknown channels fall back to friends", () => {
  assertEquals(parseDispatch({ user_id: USER, data: { type: "sync" }, channel: "spam" })?.channel, "friends_v1");
});

Deno.test("bad bodies are refused", () => {
  assertEquals(parseDispatch(null), null);
  assertEquals(parseDispatch({ user_id: "nope", data: { type: "sync" } }), null);
  assertEquals(parseDispatch({ user_id: USER, data: {} }), null);
  assertEquals(parseDispatch({ user_id: USER, data: { kind: "x" } }), null, "type is required");
  assertEquals(parseDispatch({ user_id: USER, data: { type: "sync", nested: { a: 1 } } }), null);
  assertEquals(parseDispatch({ user_id: USER, data: { type: "sync", "Bad-Key": "x" } }), null);
  assertEquals(parseDispatch({ user_id: USER, data: { type: "sync", long: "x".repeat(300) } }), null);
});

Deno.test("uuids are validated and lower-cased", () => {
  assertEquals(validUuid("AAAAAAAA-0000-0000-0000-000000000001"), USER);
  assertEquals(validUuid("aaaaaaaa00000000"), null);
});

Deno.test("secret comparison", () => {
  assertEquals(sameSecret("abc", "abc"), true);
  assertEquals(sameSecret("abc", "abd"), false);
  assertEquals(sameSecret("abc", "abcd"), false);
  assertEquals(sameSecret("", "a"), false);
});
