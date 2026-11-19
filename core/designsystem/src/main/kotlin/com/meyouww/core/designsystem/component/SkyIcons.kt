package com.meyouww.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Living Sky icon set, exactly as drawn on the design boards: rounded 2 dp lines on a 24 dp
 * grid. Use them with Material's Icon, which tints them; give the Icon a contentDescription
 * unless a nearby label already says the same thing.
 */
object SkyIcons {
    val Back by lazy { icon("Back", "M15 5l-7 7 7 7", autoMirror = true) }
    val Next by lazy { icon("Next", "M9 5l7 7-7 7", autoMirror = true) }
    val Down by lazy { icon("Down", "M6 9l6 6 6-6") }
    val Up by lazy { icon("Up", "M6 15l6-6 6 6") }
    val Plus by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Minus by lazy { icon("Minus", "M5 12h14") }
    val Close by lazy { icon("Close", "M6 6l12 12M18 6 6 18") }
    val Check by lazy { icon("Check", "M5 12.5l4.5 4.5L19 7.5") }
    val Heart by lazy { icon("Heart", "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z") }
    val Home by lazy { icon("Home", "M4 11.5 12 5l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5h-5v5H5a1 1 0 0 1-1-1z") }
    val Chat by lazy { icon("Chat", "M5 6h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7l-5 4v-4H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z") }
    val Pencil by lazy { icon("Pencil", "M4 20l1.2-4.6L15.5 5.1a2 2 0 0 1 2.8 0l.6.6a2 2 0 0 1 0 2.8L8.6 18.8z") }
    val Calendar by lazy { icon("Calendar", "M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1 -3 3h-10a3 3 0 0 1 -3 -3v-9a3 3 0 0 1 3 -3zM4 10h16M9 3v4M15 3v4") }
    val Alarm by lazy { icon("Alarm", "M5 13a7 7 0 1 0 14 0a7 7 0 1 0 -14 0M12 9.5V13l2.5 2M5 4 3 6M19 4l2 2") }
    val Bell by lazy { icon("Bell", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20a2 2 0 0 0 4 0") }
    val Send by lazy { icon("Send", "M4 12 20 4l-5 16-3-7z", autoMirror = true) }
    val Mic by lazy { icon("Mic", "M12 3h0a3 3 0 0 1 3 3v5a3 3 0 0 1 -3 3h-0a3 3 0 0 1 -3 -3v-5a3 3 0 0 1 3 -3zM6 11a6 6 0 0 0 12 0M12 17v4") }
    val Smile by lazy { icon("Smile", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M9 14.5c.9 1 1.9 1.5 3 1.5s2.1-.5 3-1.5M9 10h.01M15 10h.01") }
    val Image by lazy { icon("Image", "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-8a3 3 0 0 1 3 -3zM3 16l5-5 4 4 3-3 6 6M15.5 9.5h.01") }
    val File by lazy { icon("File", "M7 3h7l4 4v14H7zM14 3v4h4M10 13h5M10 17h5") }
    val Phone by lazy { icon("Phone", "M9.5 2h5a2.5 2.5 0 0 1 2.5 2.5v15a2.5 2.5 0 0 1 -2.5 2.5h-5a2.5 2.5 0 0 1 -2.5 -2.5v-15a2.5 2.5 0 0 1 2.5 -2.5zM11 18h2") }
    val Wallpaper by lazy { icon("Wallpaper", "M8.5 3h7a2.5 2.5 0 0 1 2.5 2.5v13a2.5 2.5 0 0 1 -2.5 2.5h-7a2.5 2.5 0 0 1 -2.5 -2.5v-13a2.5 2.5 0 0 1 2.5 -2.5zM6 15l3.5-3.5 3 3 2-2L18 16") }
    val Search by lazy { icon("Search", "M5 11a6 6 0 1 0 12 0a6 6 0 1 0 -12 0M20 20l-4.5-4.5") }
    val AddPerson by lazy { icon("AddPerson", "M6 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M3 20c.7-3.6 3.4-5.5 7-5.5s6.3 1.9 7 5.5M19 7v6M16 10h6") }
    val Group by lazy { icon("Group", "M5.5 9a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0M2.5 19c.6-3 2.9-4.6 6.5-4.6s5.9 1.6 6.5 4.6M16 5.6a3.5 3.5 0 0 1 0 6.8M18.5 14.8c1.7.6 2.7 2 3 4.2") }
    val Lock by lazy { icon("Lock", "M7.5 11h9a2.5 2.5 0 0 1 2.5 2.5v4a2.5 2.5 0 0 1 -2.5 2.5h-9a2.5 2.5 0 0 1 -2.5 -2.5v-4a2.5 2.5 0 0 1 2.5 -2.5zM8 11V8a4 4 0 0 1 8 0v3") }
    val Shield by lazy { icon("Shield", "M12 3l7 3v6c0 4.5-3 7.6-7 9-4-1.4-7-4.5-7-9V6z") }
    val Play by lazy { icon("Play", "M8 5l11 7-11 7z") }
    val Undo by lazy { icon("Undo", "M9 7 4 12l5 5M4 12h10a5 5 0 0 1 0 10h-2", autoMirror = true) }
    val Redo by lazy { icon("Redo", "M15 7l5 5-5 5M20 12H10a5 5 0 0 0 0 10h2", autoMirror = true) }
    val Sun by lazy { icon("Sun", "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M12 2.5V5M12 19v2.5M2.5 12H5M19 12h2.5M5.3 5.3 7 7M17 17l1.7 1.7M5.3 18.7 7 17M17 7l1.7-1.7") }
    val Moon by lazy { icon("Moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z") }
    val More by lazy { icon("More", "M4 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0M18 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0") }
    val Trash by lazy { icon("Trash", "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13") }
    val Qr by lazy { icon("Qr", "M5 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1zM15 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1zM5 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1zM14 14h2v2h-2zM18 18h2v2h-2zM18 14h2M14 18v2") }
    val Share by lazy { icon("Share", "M12 15V4M8 8l4-4 4 4M5 13v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5") }
    val Clock by lazy { icon("Clock", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0M12 7.5V12l3 2") }
    val Vibrate by lazy { icon("Vibrate", "M10 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-4a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2zM4 9v6M20 9v6") }
    val Pin by lazy { icon("Pin", "M9 4h6l-1 6 3 3H7l3-3zM12 13v7") }
    val Mute by lazy { icon("Mute", "M4 10v4h4l5 4V6L8 10zM17 9.5l4 5M21 9.5l-4 5") }
    val Bars by lazy { icon("Bars", "M6 20V11M12 20V4M18 20v-6") }
    val Marker by lazy { icon("Marker", "M14 4l6 6-8.5 8.5H6v-5.5zM4 21h16") }
    val Eraser by lazy { icon("Eraser", "M4 15l9-9a2 2 0 0 1 2.8 0l3.2 3.2a2 2 0 0 1 0 2.8L12 19H7zM9 10l6 6M12 19h8") }
    val Shapes by lazy { icon("Shapes", "M4 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M13.5 12h5a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1 -1.5 1.5h-5a1.5 1.5 0 0 1 -1.5 -1.5v-5a1.5 1.5 0 0 1 1.5 -1.5z") }
    val Text by lazy { icon("Text", "M5 6h14M12 6v13M9 19h6") }
    val Sticker by lazy { icon("Sticker", "M5 4h14a1 1 0 0 1 1 1v9l-6 6H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zM14 20v-5a1 1 0 0 1 1-1h5") }
    val Spark by lazy { icon("Spark", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z") }
    val Cookie by lazy { icon("Cookie", "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0M9 9h.01M14.5 10.5h.01M10 14.5h.01M14.5 15h.01") }
    val Cloud by lazy { icon("Cloud", "M7 18a4.5 4.5 0 0 1-.6-9A6 6 0 0 1 18 10.5a3.8 3.8 0 0 1-.5 7.5z") }
    val Info by lazy { icon("Info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0M12 11v6M12 7.5h.01") }
    val SignOut by lazy { icon("SignOut", "M10 4H6a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h4M14 8l4 4-4 4M18 12H9") }

    private fun icon(name: String, pathData: String, autoMirror: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = "Sky.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
