// Firebase Cloud Messaging HTTP v1 sender.
//
// The service-account JSON lives only in the Edge Function secret FCM_SERVICE_ACCOUNT.
// It is never in the app or the repository.

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

let cachedToken: { value: string; expiresAt: number } | null = null;

function serviceAccount(): ServiceAccount {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) throw new Error("FCM_SERVICE_ACCOUNT secret is not set");
  const parsed = JSON.parse(raw) as ServiceAccount;
  if (!parsed.project_id || !parsed.client_email || !parsed.private_key) {
    throw new Error("FCM_SERVICE_ACCOUNT is missing fields");
  }
  return parsed;
}

function base64url(data: Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : data;
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const binary = atob(body);
  const out = new ArrayBuffer(binary.length);
  const view = new Uint8Array(out);
  for (let i = 0; i < binary.length; i++) view[i] = binary.charCodeAt(i);
  return out;
}

async function accessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt - 60 > now) return cachedToken.value;

  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`)),
  );
  const assertion = `${header}.${claims}.${base64url(signature)}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!response.ok) throw new Error(`Google OAuth failed: ${response.status}`);
  const json = await response.json() as { access_token: string; expires_in: number };
  cachedToken = { value: json.access_token, expiresAt: now + json.expires_in };
  return json.access_token;
}

export type SendResult =
  | { ok: true }
  | { ok: false; unregistered: boolean; status: number; error: string };

/**
 * A notification Android shows by itself (through Google Play services), even when
 * the phone maker has stopped the app. Text comes from the app's own string
 * resources (loc keys), so the server never sends names or message content.
 * [tag] matches the tag the app uses, so the app's richer version replaces it.
 */
export interface VisibleNotification {
  titleLocKey: string;
  bodyLocKey: string;
  channelId: string;
  tag: string;
}

/**
 * Sends a data-only message (no `notification` block), so the app's
 * onMessageReceived always runs, even in the background.
 */
export function sendDataMessage(
  token: string,
  data: Record<string, string>,
  priority: "high" | "normal",
  ttl: string,
  collapseKey?: string,
): Promise<SendResult> {
  return sendMessage(token, data, priority, ttl, collapseKey);
}

/** Sends a message; with [visible] it carries a generic notification Android displays itself. */
export async function sendMessage(
  token: string,
  data: Record<string, string>,
  priority: "high" | "normal",
  ttl: string,
  collapseKey?: string,
  visible?: VisibleNotification,
): Promise<SendResult> {
  const sa = serviceAccount();
  const bearer = await accessToken(sa);
  const android: Record<string, unknown> = { priority: priority === "high" ? "HIGH" : "NORMAL", ttl };
  if (collapseKey) android.collapse_key = collapseKey;
  if (visible) {
    android.notification = {
      title_loc_key: visible.titleLocKey,
      body_loc_key: visible.bodyLocKey,
      channel_id: visible.channelId,
      tag: visible.tag,
      icon: "ic_stat_meyouww",
      color: "#CC2E26",
      default_sound: true,
      default_vibrate_timings: true,
      notification_priority: priority === "high" ? "PRIORITY_HIGH" : "PRIORITY_DEFAULT",
    };
  }

  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
    body: JSON.stringify({ message: { token, data, android } }),
  });
  if (response.ok) return { ok: true };

  const text = await response.text();
  const unregistered = response.status === 404 || text.includes("UNREGISTERED") ||
    (response.status === 400 && text.includes("registration token"));
  return { ok: false, unregistered, status: response.status, error: errorCode(text) };
}

/** FCM's error code ("SENDER_ID_MISMATCH", "UNREGISTERED", ...) without anything else from the reply. */
export function errorCode(text: string): string {
  const fcm = /"errorCode"\s*:\s*"([A-Z_]{3,40})"/.exec(text);
  if (fcm) return fcm[1];
  const status = /"status"\s*:\s*"([A-Z_]{3,40})"/.exec(text);
  return status ? status[1] : "UNKNOWN";
}

export function isFcmConfigured(): boolean {
  return !!Deno.env.get("FCM_SERVICE_ACCOUNT");
}
