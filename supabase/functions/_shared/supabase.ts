import { createClient, type SupabaseClient } from "jsr:@supabase/supabase-js@2";

/**
 * The secret key never leaves the server. New projects use `sb_secret_...` keys;
 * Supabase exposes them to Edge Functions through SUPABASE_SECRET_KEYS. A key can
 * also be set explicitly with `supabase secrets set MEYOUWW_SECRET_KEY=...`.
 */
function secretKey(): string {
  const explicit = Deno.env.get("MEYOUWW_SECRET_KEY");
  if (explicit) return explicit;
  const keys = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (keys) {
    try {
      const parsed = JSON.parse(keys);
      if (typeof parsed === "string") return parsed;
      const value = parsed.default ?? Object.values(parsed)[0];
      if (typeof value === "string" && value) return value;
    } catch {
      return keys;
    }
  }
  const legacy = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (legacy) return legacy;
  throw new Error("No Supabase secret key is available to this function");
}

let admin: SupabaseClient | null = null;

/** Server-side client. Bypasses RLS, so every query must scope by the verified user. */
export function adminClient(): SupabaseClient {
  if (!admin) {
    admin = createClient(Deno.env.get("SUPABASE_URL")!, secretKey(), {
      auth: { persistSession: false, autoRefreshToken: false },
    });
  }
  return admin;
}

/** Verifies the caller's access token and returns their user ID, or null. */
export async function verifiedUserId(req: Request): Promise<string | null> {
  const header = req.headers.get("Authorization") ?? "";
  const jwt = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!jwt) return null;
  const { data, error } = await adminClient().auth.getUser(jwt);
  if (error || !data.user) return null;
  return data.user.id;
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Reads a JSON body of at most `maxBytes`. */
export async function readJson(req: Request, maxBytes = 16_384): Promise<Record<string, unknown> | null> {
  const text = await req.text();
  if (text.length > maxBytes) return null;
  try {
    const value = JSON.parse(text);
    return value && typeof value === "object" && !Array.isArray(value) ? value : null;
  } catch {
    return null;
  }
}

export function str(value: unknown, max: number): string | null {
  return typeof value === "string" && value.length > 0 && value.length <= max ? value : null;
}

export function int(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) ? value : null;
}
