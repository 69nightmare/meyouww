package com.meyouww.feature.studio

/** Presentation destinations for a wallpaper preview; delivery permissions remain with the existing flow. */
internal data class PreviewRecipientUi(
    val canChooseDestination: Boolean,
    val selectedFriendId: String?,
)

/** A preview opened from a chat must never present another person as its selected recipient. */
internal fun previewRecipientUi(chatFriendId: String?, loadedFriendId: String?): PreviewRecipientUi =
    PreviewRecipientUi(
        canChooseDestination = chatFriendId == null,
        selectedFriendId = loadedFriendId?.takeIf { chatFriendId == null || it == chatFriendId },
    )
