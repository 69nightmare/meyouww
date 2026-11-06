// POST /functions/v1/test-push
// Body: { installation_id }
// Schedules a data-only test push to the caller's own phone about 20 seconds from now,
// giving them time to swipe the app away and lock the screen. Returns immediately.

import { adminClient, json, readJson, verifiedUserId } from "../_shared/supabase.ts";
import { validInstallationId } from "../_shared/device.ts";
import { isFcmConfigured, sendDataMessage } from "../_shared/fcm.ts";
import { choosePriority, type FcmPriority } from "../_shared/priority.ts";

declare const EdgeRuntime: { waitUntil(promise: Promise<unknown>): void };

const DELAY_MS = 20_000;
const CHANNEL = "delivery_status_v1";

async function sendLater(testId: string, token: string, priority: FcmPriority, sendAtMs: number) {
  const db = adminClient();
  await new Promise((resolve) => setTimeout(resolve, Math.max(0, sendAtMs - Date.now())));
  const sentAt = Date.now();
  const result = await sendDataMessage(
    token,
    { type: "delivery_test", test_id: testId, sent_at_ms: String(sentAt), v: "1" },
    priority,
    "120s",
  );
  if (result.ok) {
    await db.from("delivery_tests").update({ sent_at: new Date(sentAt).toISOString() }).eq("id", testId);
  } else {
    await db.from("delivery_tests").update({ send_error: `${result.status} ${result.error}`.slice(0, 200) }).eq("id", testId);
    if (result.unregistered) await db.rpc("forget_push_token", { p_token: token });
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const userId = await verifiedUserId(req);
  if (!userId) return json({ error: "unauthorized" }, 401);
  if (!isFcmConfigured()) return json({ error: "push_not_configured" }, 503);

  const body = await readJson(req);
  const installationId = validInstallationId(body?.installation_id);
  if (!installationId) return json({ error: "bad_request" }, 400);

  const db = adminClient();
  const { data: allowed } = await db.rpc("consume_rate_limit", {
    p_key: `test-push:${userId}`,
    p_limit: 10,
    p_window_seconds: 3600,
  });
  if (allowed === false) return json({ error: "rate_limited" }, 429);

  const { data: device } = await db
    .from("devices")
    .select("id")
    .eq("user_id", userId)
    .eq("installation_id", installationId)
    .is("revoked_at", null)
    .maybeSingle();
  if (!device) return json({ error: "device_not_registered" }, 404);

  const { data: token } = await db.from("push_tokens").select("token").eq("device_id", device.id).maybeSingle();
  if (!token) return json({ error: "no_push_token" }, 409);

  const { data: state } = await db
    .from("device_state")
    .select("notifications_enabled, channels")
    .eq("device_id", device.id)
    .maybeSingle();
  const priority = choosePriority(true, state, CHANNEL);

  const sendAtMs = Date.now() + DELAY_MS;
  const { data: test, error } = await db
    .from("delivery_tests")
    .insert({ user_id: userId, device_id: device.id, send_at: new Date(sendAtMs).toISOString(), priority })
    .select("id")
    .single();
  if (error || !test) return json({ error: "server_error" }, 500);

  EdgeRuntime.waitUntil(sendLater(test.id, token.token, priority, sendAtMs));
  return json({ test_id: test.id, send_at_ms: sendAtMs, server_now_ms: Date.now(), priority });
});
