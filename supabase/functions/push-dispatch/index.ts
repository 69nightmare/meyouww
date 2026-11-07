// POST /functions/v1/push-dispatch
// Called only by Postgres (pg_net) when something lands in a person's inbox, a chat
// message arrives, and so on. Authenticated with the shared token in
// private.settings ("x-meyouww-dispatch" header), not with a user JWT.
// Body: { user_id, data: { type, ... }, channel }
// Sends every active device of that person two FCM messages:
//   1. a generic notification Android shows by itself (see _shared/visible.ts), so
//      something appears even when the phone maker has stopped the app;
//   2. a data-only message that lets the app fetch details, replace that notification
//      with a richer one, or apply an automatic wallpaper.
// Payloads carry IDs only. Each dispatch's outcome is recorded (counts and FCM error
// codes, never tokens) so the app's notification check can explain failures.
// Each call also sweeps a few finished wallpaper pictures out of Storage, so nothing
// a friend sent stays on the server once it has been answered or has expired.

import { adminClient, json, readJson } from "../_shared/supabase.ts";
import { parseDispatch } from "../_shared/dispatch.ts";
import { isFcmConfigured, sendMessage } from "../_shared/fcm.ts";
import { visibleFor } from "../_shared/visible.ts";
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
  if (ids.length === 0) {
    await record(db, request, 0, 0, 0, "no_devices");
    await sweepMedia(db);
    return json({ sent: 0, devices: 0 });
  }

  const [{ data: tokens }, { data: states }] = await Promise.all([
    db.from("push_tokens").select("device_id, token").in("device_id", ids),
    db.from("device_state").select("device_id, notifications_enabled, channels").in("device_id", ids),
  ]);
  const stateOf = new Map((states ?? []).map((s: { device_id: string }) => [s.device_id, s]));
  const visible = visibleFor(request.data, request.channel);

  let sent = 0;
  const errors = new Set<string>();
  await Promise.all((tokens ?? []).map(async (t: { device_id: string; token: string }) => {
    // Every dispatched event leads to a visible notification unless the person turned it off.
    const priority = choosePriority(true, stateOf.get(t.device_id) as never, request.channel);
    if (visible) {
      const shown = await sendMessage(t.token, openOnTap(request.data), priority, TTL, undefined, visible);
      if (!shown.ok) {
        errors.add(`${shown.status} ${shown.error}`);
        if (shown.unregistered) {
          await db.rpc("forget_push_token", { p_token: t.token });
          return;
        }
      }
    }
    const result = await sendMessage(t.token, request.data, priority, TTL);
    if (result.ok) sent++;
    else {
      errors.add(`${result.status} ${result.error}`);
      if (result.unregistered) await db.rpc("forget_push_token", { p_token: t.token });
    }
  }));
  const tokenCount = (tokens ?? []).length;
  await record(db, request, ids.length, tokenCount, sent, tokenCount === 0 ? "no_push_token" : [...errors].join(", ") || null);
  await sweepMedia(db);
  return json({ sent, devices: ids.length });
});

// Tapping the system-shown copy opens this screen (MainActivity reads the extra).
function openOnTap(data: Record<string, string>): Record<string, string> {
  return data.type === "activity" || data.type === "wallpaper" ? { "com.meyouww.extra.DESTINATION": "inbox" } : {};
}

// Best effort, like logging: deletes up to 25 finished pictures; any that fail are handed back.
async function sweepMedia(db: ReturnType<typeof adminClient>) {
  try {
    const { data } = await db.rpc("claim_wallpaper_media_sweep", { p_limit: 25 });
    const paths = Array.isArray(data) ? (data as string[]) : [];
    if (paths.length === 0) return;
    const { error } = await db.storage.from("wallpapers").remove(paths);
    if (error) await db.rpc("release_wallpaper_media_sweep", { p_paths: paths });
  } catch {
    // ignore
  }
}

// Best effort: a logging failure never fails the push.
async function record(
  db: ReturnType<typeof adminClient>,
  request: { userId: string; data: Record<string, string>; channel: string },
  devices: number,
  tokens: number,
  sent: number,
  error: string | null,
) {
  try {
    await db.rpc("record_push_result", {
      p_user: request.userId,
      p_type: request.data.type,
      p_channel: request.channel,
      p_devices: devices,
      p_tokens: tokens,
      p_sent: sent,
      p_error: error ? error.slice(0, 200) : null,
    });
  } catch {
    // ignore
  }
}
