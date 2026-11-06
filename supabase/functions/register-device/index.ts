// POST /functions/v1/register-device
// Body: { action: "register" | "unregister", installation_id, push_token?, manufacturer?, os_sdk?, app_version?, state? }
// The user comes from the verified access token, never from the body.

import { adminClient, int, json, readJson, str, verifiedUserId } from "../_shared/supabase.ts";
import { sanitizeState, validInstallationId, validPushToken } from "../_shared/device.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const userId = await verifiedUserId(req);
  if (!userId) return json({ error: "unauthorized" }, 401);

  const body = await readJson(req);
  const installationId = validInstallationId(body?.installation_id);
  if (!body || !installationId) return json({ error: "bad_request" }, 400);

  const db = adminClient();
  const { data: allowed } = await db.rpc("consume_rate_limit", {
    p_key: `register-device:${userId}`,
    p_limit: 60,
    p_window_seconds: 3600,
  });
  if (allowed === false) return json({ error: "rate_limited" }, 429);

  if (body.action === "unregister") {
    const { error } = await db.rpc("unregister_device", { p_user_id: userId, p_installation_id: installationId });
    return error ? json({ error: "server_error" }, 500) : json({ ok: true });
  }

  const { data: deviceId, error } = await db.rpc("upsert_device", {
    p_user_id: userId,
    p_installation_id: installationId,
    p_push_token: validPushToken(body.push_token),
    p_manufacturer: str(body.manufacturer, 40),
    p_os_sdk: int(body.os_sdk),
    p_app_version: str(body.app_version, 40),
    p_state: sanitizeState(body.state),
  });
  if (error) return json({ error: "server_error" }, 500);
  return json({ ok: true, device_id: deviceId });
});
