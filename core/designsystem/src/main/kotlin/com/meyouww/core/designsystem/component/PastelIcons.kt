package com.meyouww.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Soft, rounded line icons drawn for the pastel look (2 dp strokes on a 24 dp grid). Use
 * them with Material's Icon, which tints them; give the Icon a contentDescription unless a
 * nearby label already says the same thing.
 */
object PastelIcons {
    val Home by lazy { icon("Home", "M4 11.5 12 5l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5h-5v5H5a1 1 0 0 1-1-1z") }
    val Chat by lazy { icon("Chat", "M5 6h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7l-5 4v-4H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z") }
    val Pencil by lazy { icon("Pencil", "M4 20l1.2-4.6L15.5 5.1a2 2 0 0 1 2.8 0l.6.6a2 2 0 0 1 0 2.8L8.6 18.8zM13.5 7l3.5 3.5") }
    val Heart by lazy { icon("Heart", "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z") }
    val Plus by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Back by lazy { icon("Back", "M15 5l-7 7 7 7", autoMirror = true) }
    val Forward by lazy { icon("Forward", "M9 5l7 7-7 7", autoMirror = true) }
    val Close by lazy { icon("Close", "M6 6l12 12M18 6 6 18") }
    val Check by lazy { icon("Check", "M5 12.5l4.5 4.5L19 7") }
    val Search by lazy { icon("Search", circle(11f, 11f, 6f) + "M20 20l-4.5-4.5") }
    val AddPerson by lazy { icon("AddPerson", circle(10f, 8f, 3.5f) + "M3.5 19a6.5 6.5 0 0 1 13 0M19 8v6M16 11h6") }
    val Alarm by lazy { icon("Alarm", circle(12f, 13f, 7f) + "M12 9.5V13l2.5 1.5M5 4 3 6M19 4l2 2") }
    val Calendar by lazy { icon("Calendar", "M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3zM4 10h16M9 3v4M15 3v4") }
    val Bell by lazy { icon("Bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20a2 2 0 0 0 4 0") }
    val Send by lazy { icon("Send", "M4 12l15-7-5 15-3-6z", autoMirror = true) }
    val Mic by lazy { icon("Mic", "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3zM6 11a6 6 0 0 0 12 0M12 17v4") }
    val Smile by lazy { icon("Smile", circle(12f, 12f, 8.5f) + "M8.5 14c1.8 2 5.2 2 7 0M9 9.5v.5M15 9.5v.5") }
    val Lock by lazy { icon("Lock", "M7.5 11h9a2.5 2.5 0 0 1 2.5 2.5v4a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 17.5v-4A2.5 2.5 0 0 1 7.5 11zM8 11V8a4 4 0 0 1 8 0v3") }
    val Trash by lazy { icon("Trash", "M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13") }
    val Shield by lazy { icon("Shield", "M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6zM9 12l2 2 4-4") }
    val Alert by lazy { icon("Alert", circle(12f, 12f, 9f) + "M12 7.5v5.5M12 16.5v.1") }
    val People by lazy { icon("People", circle(9f, 9f, 3f) + circle(16.5f, 10f, 2.5f) + "M3.5 19a5.5 5.5 0 0 1 11 0M14 19a4.5 4.5 0 0 1 7 0") }

    /** An SVG circle as path data (two arcs). */
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun icon(name: String, pathData: String, autoMirror: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = "Pastel.$name",
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
