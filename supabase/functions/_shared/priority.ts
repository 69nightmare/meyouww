// FCM priority policy (see docs/ARCHITECTURE.md, "Push").
//
// High priority only when the phone will show a notification for this message.
// FCM watches each installation: if high-priority messages keep arriving without a
// visible notification, it starts delivering them as normal priority. So we never
// spend high priority on a phone whose notifications (or the relevant channel) are off.

export interface DeviceStateLike {
  notifications_enabled?: boolean | null;
  channels?: Record<string, number> | null;
}

export type FcmPriority = "high" | "normal";

export function choosePriority(
  showsNotification: boolean,
  state: DeviceStateLike | null | undefined,
  channelId: string,
): FcmPriority {
  if (!showsNotification) return "normal";
  if (!state || state.notifications_enabled !== true) return "normal";
  const importance = state.channels?.[channelId];
  // 0 = IMPORTANCE_NONE: the person turned this channel off.
  if (importance === 0) return "normal";
  return "high";
}

/** FCM v1 wants TTL as a duration string such as "86400s". Clamped to FCM's 28-day maximum. */
export function ttlSeconds(expiresAtMs: number, nowMs: number): string {
  const seconds = Math.max(0, Math.min(2_419_200, Math.floor((expiresAtMs - nowMs) / 1000)));
  return `${seconds}s`;
}
