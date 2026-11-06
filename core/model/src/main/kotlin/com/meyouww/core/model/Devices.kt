package com.meyouww.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Screen facts a phone shares for previews. Never the wallpaper, apps or location. */
@Serializable
data class ScreenProfile(
    @SerialName("width_px") val widthPx: Int,
    @SerialName("height_px") val heightPx: Int,
    @SerialName("density_dpi") val densityDpi: Int,
    @SerialName("status_bar_px") val statusBarPx: Int = 0,
    @SerialName("nav_bar_px") val navBarPx: Int = 0,
    @SerialName("cutout_top_px") val cutoutTopPx: Int = 0,
    @SerialName("cutout_bottom_px") val cutoutBottomPx: Int = 0,
    @SerialName("corner_radius_px") val cornerRadiusPx: Int = 0,
    @SerialName("sdk_int") val sdkInt: Int? = null,
    val maker: String? = null,
    val model: String? = null,
    @SerialName("wallpaper_supported") val wallpaperSupported: Boolean = true,
    @SerialName("set_wallpaper_allowed") val setWallpaperAllowed: Boolean = true,
    @SerialName("separate_lock") val separateLock: Boolean = true,
    @SerialName("live_wallpaper") val liveWallpaper: Boolean = false,
    @SerialName("lock_rotates") val lockRotates: Boolean = false,
    val timezone: String? = null,
)

/** A friend's phone as returned by `list_contact_devices` (only if they share specs with you). */
@Serializable
data class ContactDevice(
    @SerialName("device_id") val deviceId: String,
    @SerialName("width_px") val widthPx: Int,
    @SerialName("height_px") val heightPx: Int,
    @SerialName("density_dpi") val densityDpi: Int = 420,
    @SerialName("status_bar_px") val statusBarPx: Int = 0,
    @SerialName("nav_bar_px") val navBarPx: Int = 0,
    @SerialName("cutout_top_px") val cutoutTopPx: Int = 0,
    @SerialName("cutout_bottom_px") val cutoutBottomPx: Int = 0,
    @SerialName("corner_radius_px") val cornerRadiusPx: Int = 0,
    val maker: String? = null,
    val model: String? = null,
    @SerialName("separate_lock") val separateLock: Boolean = true,
    @SerialName("live_wallpaper") val liveWallpaper: Boolean = false,
    @SerialName("lock_rotates") val lockRotates: Boolean = false,
    @SerialName("can_set") val canSet: Boolean = true,
) {
    val label: String? get() = listOfNotNull(maker, model).joinToString(" ").takeIf { it.isNotBlank() }
}

/** What a preview needs to draw a phone: size and the bars that cover the wallpaper. */
data class PreviewTarget(
    val widthPx: Int,
    val heightPx: Int,
    val statusBarPx: Int,
    val navBarPx: Int,
    val cutoutTopPx: Int,
    val cornerRadiusPx: Int,
    /** True when the numbers are a guess (no profile shared). */
    val estimate: Boolean,
    val deviceId: String? = null,
) {
    val aspect: Float get() = heightPx.toFloat() / widthPx

    companion object {
        fun of(device: ContactDevice) = PreviewTarget(
            device.widthPx, device.heightPx, device.statusBarPx, device.navBarPx, device.cutoutTopPx,
            device.cornerRadiusPx, estimate = false, deviceId = device.deviceId,
        )
    }
}

/** Standard phones for when no profile is shared. Labelled as estimates in the UI. */
enum class PhonePreset(val target: PreviewTarget) {
    Typical(PreviewTarget(1080, 2400, 96, 63, 80, 110, estimate = true)),
    Classic(PreviewTarget(1080, 2340, 90, 132, 70, 90, estimate = true)),
    Tall(PreviewTarget(1080, 2520, 100, 63, 90, 120, estimate = true)),
    Compact(PreviewTarget(1080, 1920, 72, 144, 0, 40, estimate = true)),
    FoldableInner(PreviewTarget(1812, 2176, 96, 63, 0, 60, estimate = true)),
}
