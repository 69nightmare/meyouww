package com.meyouww.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.meyouww.core.designsystem.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Bundled, offline fonts. All are SIL Open Font License (see the Licenses screen). */
object MeyouwwFonts {
    /** Playful headings. */
    val Fredoka = FontFamily(
        variable(R.font.fredoka, 500),
        variable(R.font.fredoka, 600),
        variable(R.font.fredoka, 700),
    )

    /** Body text. */
    val Nunito = FontFamily(
        variable(R.font.nunito, 400),
        variable(R.font.nunito, 600),
        variable(R.font.nunito, 700),
        variable(R.font.nunito, 800),
    )

    /** Labels and captions. */
    val Quicksand = FontFamily(
        variable(R.font.quicksand, 500),
        variable(R.font.quicksand, 600),
        variable(R.font.quicksand, 700),
    )

    /** Handwritten accent for stickers, doodle text and love notes (Latin subset). */
    val Handwritten = FontFamily(
        Font(R.font.gaegu_regular, FontWeight.Normal),
        Font(R.font.gaegu_bold, FontWeight.Bold),
    )

    /** Chunky pop lettering for headings, buttons and sound effects (Latin and kana subset). */
    val Pop = FontFamily(Font(R.font.mochiy_pop_one, FontWeight.Normal))

    /** Soft rounded body text (Latin subset), as on the Kuchhu-chan site. */
    val ZenMaru = FontFamily(
        Font(R.font.zen_maru_gothic_medium, FontWeight.Normal),
        Font(R.font.zen_maru_gothic_medium, FontWeight.Medium),
        Font(R.font.zen_maru_gothic_bold, FontWeight.SemiBold),
        Font(R.font.zen_maru_gothic_bold, FontWeight.Bold),
    )

    /** Crayon handwriting for notes and little asides. */
    val Gochi = FontFamily(Font(R.font.gochi_hand, FontWeight.Normal))
}

// One chunky display face (no fake bold: Mochiy Pop One has a single weight) over soft rounded body text.
private val heading = TextStyle(fontFamily = MeyouwwFonts.Pop, fontWeight = FontWeight.Normal)
private val body = TextStyle(fontFamily = MeyouwwFonts.ZenMaru, fontWeight = FontWeight.Medium)
private val label = TextStyle(fontFamily = MeyouwwFonts.ZenMaru, fontWeight = FontWeight.Bold)

val MeyouwwTypography = Typography(
    displayLarge = heading.copy(fontSize = 46.sp, lineHeight = 54.sp),
    displayMedium = heading.copy(fontSize = 38.sp, lineHeight = 46.sp),
    displaySmall = heading.copy(fontSize = 31.sp, lineHeight = 38.sp),
    headlineLarge = heading.copy(fontSize = 27.sp, lineHeight = 34.sp),
    headlineMedium = heading.copy(fontSize = 23.sp, lineHeight = 30.sp),
    headlineSmall = heading.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = heading.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = heading.copy(fontSize = 15.sp, lineHeight = 21.sp),
    titleSmall = label.copy(fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = body.copy(fontSize = 12.sp, lineHeight = 17.sp),
    // Buttons and chips speak in the display face, like the site's buttons.
    labelLarge = heading.copy(fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = label.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = label.copy(fontSize = 11.sp, lineHeight = 15.sp),
)

/** Extra text styles outside the Material type scale. */
object MeyouwwTextStyles {
    val handwritten = TextStyle(fontFamily = MeyouwwFonts.Gochi, fontSize = 22.sp, lineHeight = 26.sp)
    val soundEffect = TextStyle(fontFamily = MeyouwwFonts.Pop, fontSize = 28.sp, lineHeight = 34.sp)
}
