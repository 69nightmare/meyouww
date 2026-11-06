// POST /functions/v1/update-device-state
// Body: { installation_id, state }
// Keeps the server's view of notification permission, channels, battery and data
// restrictions current, so it can choose push priority and explain delivery problems.

import { adminClient, json, readJson, verifiedUserId } from "../_shared/supabase.ts";
import { sanitizeState, validInstallationId } from "../_shared/device.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const userId = await verifiedUserId(req);
  if (!userId) return json({ error: "unauthorized" }, 401);

  const body = await readJson(req);
  const installationId = validInstallationId(body?.installation_id);
  const state = sanitizeState(body?.state);
  if (!installationId || !state) return json({ error: "bad_request" }, 400);

  const db = adminClient();
  const { data: allowed } = await db.rpc("consume_rate_limit", {
    p_key: `device-state:${userId}`,
    p_limit: 120,
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

  const { error } = await db.rpc("set_device_state", { p_device_id: device.id, p_state: state });
  return error ? json({ error: "server_error" }, 500) : json({ ok: true });
});
