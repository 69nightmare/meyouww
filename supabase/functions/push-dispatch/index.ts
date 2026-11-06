// POST /functions/v1/push-dispatch
// Called only by Postgres (pg_net) when something lands in a person's inbox, a chat
// message arrives, and so on. Authenticated with the shared token in
// private.settings ("x-meyouww-dispatch" header), not with a user JWT.
// Body: { user_id, data: { type, ... }, channel }
// Sends a data-only FCM message to every active device of that person. The payload
// carries IDs only; the app posts the notification and fetches details itself.

import { adminClient, json, readJson } from "../_shared/supabase.ts";
import { parseDispatch } from "../_shared/dispatch.ts";
import { isFcmConfigured, sendDataMessage } from "../_shared/fcm.ts";
import { choosePriority } from "../_shared/priority.ts";

const TTL = "259200s"; // 3 days; the inbox keeps everything anyway

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const token = req.headers.get("x-meyouww-dispatch") ?? "";
  if (token.length < 16 || token.length > 200) return json({ error: "unauthorized" }, 401);

  const db = adminClient();
  const { data: valid } = await db.rpc("check_dispatch_token", { p_token: token });
  if (valid !== true) return json({ error: "unauthorized" }, 401);
  if (!isFcmConfigured()) return json({ error: "push_not_configured" }, 503);

  const request = parseDispatch(await readJson(req));
  if (!request) return json({ error: "bad_request" }, 400);

  const { data: devices } = await db
    .from("devices")
    .select("id")
    .eq("user_id", request.userId)
    .is("revoked_at", null);
  const ids = (devices ?? []).map((d: { id: string }) => d.id);
  if (ids.length === 0) return json({ sent: 0, devices: 0 });

  const [{ data: tokens }, { data: states }] = await Promise.all([
    db.from("push_tokens").select("device_id, token").in("device_id", ids),
    db.from("device_state").select("device_id, notifications_enabled, channels").in("device_id", ids),
  ]);
  const stateOf = new Map((states ?? []).map((s: { device_id: string }) => [s.device_id, s]));

  let sent = 0;
  await Promise.all((tokens ?? []).map(async (t: { device_id: string; token: string }) => {
    // Every dispatched event leads to a visible notification unless the person turned it off.
    const priority = choosePriority(true, stateOf.get(t.device_id) as never, request.channel);
    const result = await sendDataMessage(t.token, request.data, priority, TTL);
    if (result.ok) sent++;
    else if (result.unregistered) await db.rpc("forget_push_token", { p_token: t.token });
  }));
  return json({ sent, devices: ids.length });
});
