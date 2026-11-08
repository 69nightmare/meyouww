// Validation for push-dispatch requests. They come from Postgres (pg_net), but the
// body is still checked: only a user ID, a small map of string values and a known
// channel are accepted. FCM data values must be strings.

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const KEY = /^[a-z_]{1,32}$/;
// FCM refuses a data message that uses any of these keys (400 INVALID_ARGUMENT).
const RESERVED_KEY = /^(from|message_type|notification|google.*|gcm.*|collapse_key)$/;
export const CHANNELS = new Set([
  "messages_v1",
  "wallpapers_v1",
  "wallpaper_requests_v1",
  "alarms_v1",
  "reminders_v1",
  "friends_v1",
  "security_v1",
  "delivery_status_v1",
]);

export interface DispatchRequest {
  userId: string;
  data: Record<string, string>;
  channel: string;
}

export function validUuid(value: unknown): string | null {
  return typeof value === "string" && UUID.test(value) ? value.toLowerCase() : null;
}

export function parseDispatch(body: Record<string, unknown> | null): DispatchRequest | null {
  if (!body) return null;
  const userId = validUuid(body.user_id);
  const raw = body.data;
  if (!userId || !raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const entries = Object.entries(raw as Record<string, unknown>);
  if (entries.length === 0 || entries.length > 12) return null;
  const data: Record<string, string> = {};
  for (const [key, value] of entries) {
    if (!KEY.test(key) || RESERVED_KEY.test(key)) return null;
    if (typeof value === "string" && value.length <= 256) data[key] = value;
    else if (typeof value === "number" && Number.isFinite(value)) data[key] = String(value);
    else if (typeof value === "boolean") data[key] = value ? "true" : "false";
    else return null;
  }
  if (!data.type || !/^[a-z_]{2,32}$/.test(data.type)) return null;
  const channel = typeof body.channel === "string" && CHANNELS.has(body.channel) ? body.channel : "friends_v1";
  return { userId, data, channel };
}

/** Constant-time string comparison for the shared dispatch token. */
export function sameSecret(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}
