// Validation of the device-state report sent by the app. Only known fields with the
// right types are kept; everything else is dropped.

const DATA_SAVER = new Set(["off", "allowed", "on"]);
const PLAY_SERVICES = new Set(["ok", "update_required", "missing", "error"]);

export function sanitizeState(raw: unknown): Record<string, unknown> | null {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return null;
  const s = raw as Record<string, unknown>;
  const channels: Record<string, number> = {};
  if (s.channels && typeof s.channels === "object" && !Array.isArray(s.channels)) {
    for (const [id, importance] of Object.entries(s.channels as Record<string, unknown>).slice(0, 32)) {
      if (/^[a-z0-9_]{1,40}$/.test(id) && typeof importance === "number" && Number.isInteger(importance)) {
        channels[id] = Math.max(-1000, Math.min(1000, importance));
      }
    }
  }
  const bool = (v: unknown) => (typeof v === "boolean" ? v : null);
  return {
    notifications_enabled: bool(s.notifications_enabled) ?? false,
    channels,
    exact_alarms_allowed: bool(s.exact_alarms_allowed),
    full_screen_allowed: bool(s.full_screen_allowed),
    background_restricted: bool(s.background_restricted),
    data_saver: typeof s.data_saver === "string" && DATA_SAVER.has(s.data_saver) ? s.data_saver : null,
    play_services: typeof s.play_services === "string" && PLAY_SERVICES.has(s.play_services) ? s.play_services : null,
    standby_bucket: typeof s.standby_bucket === "number" && Number.isInteger(s.standby_bucket) ? s.standby_bucket : null,
  };
}

export function validInstallationId(value: unknown): string | null {
  return typeof value === "string" && /^[A-Za-z0-9-]{8,64}$/.test(value) ? value : null;
}

export function validPushToken(value: unknown): string | null {
  return typeof value === "string" && value.length >= 20 && value.length <= 4096 && !/\s/.test(value) ? value : null;
}
