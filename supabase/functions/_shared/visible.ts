// Which generic notification Android should show by itself for a dispatched event.
//
// Data-only pushes need the app's process. Some phone makers (Xiaomi, Oppo, Vivo,
// Realme and others) stop apps that were swiped away and then drop those pushes, so
// every visible event also carries a notification block that Google Play services
// displays without the app. Its text is the app's own generic string ("New friend
// request", "Tap to see"): no names or content ever leave the server. The tag
// equals the tag the app posts under, so the app's detailed version replaces it.

import type { VisibleNotification } from "./fcm.ts";

const ACTIVITY_KINDS = new Set([
  "friend_request",
  "friend_accepted",
  "relationship_invite",
  "relationship_accepted",
  "wallpaper_request",
  "wallpaper_received",
  "wallpaper_applied",
  "wallpaper_declined",
  "doodle_received",
  "event_invite",
  "event_added",
  "event_accepted",
  "event_declined",
  "event_cancelled",
  "alarm_request",
  "alarm_added",
  "alarm_accepted",
  "alarm_declined",
]);

export function visibleFor(data: Record<string, string>, channel: string): VisibleNotification | null {
  switch (data.type) {
    case "activity": {
      if (!data.activity_id) return null;
      const kind = ACTIVITY_KINDS.has(data.kind) ? data.kind : null;
      return {
        titleLocKey: kind ? `activity_${kind}_generic` : "activity_unknown_title",
        bodyLocKey: "activity_tap_to_see",
        channelId: channel,
        tag: `activity:${data.activity_id}`,
      };
    }
    case "wallpaper":
      if (!data.command_id) return null;
      return {
        titleLocKey: "push_wallpaper_title",
        bodyLocKey: "push_wallpaper_text",
        channelId: channel,
        tag: `wallpaper:${data.command_id}`,
      };
    case "message":
      if (!data.conversation_id) return null;
      return {
        titleLocKey: "chat_push_title",
        bodyLocKey: "chat_push_text",
        channelId: channel,
        tag: `chat:${data.conversation_id}`,
      };
    default:
      return null;
  }
}
