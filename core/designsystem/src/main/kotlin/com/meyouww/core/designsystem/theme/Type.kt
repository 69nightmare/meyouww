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

    /** Japanese pop lettering for sound effects (kana subset). */
    val Pop = FontFamily(Font(R.font.mochiy_pop_one, FontWeight.Normal))
}

private val heading = TextStyle(fontFamily = MeyouwwFonts.Fredoka, fontWeight = FontWeight.SemiBold)
private val body = TextStyle(fontFamily = MeyouwwFonts.Nunito, fontWeight = FontWeight.Normal)
private val label = TextStyle(fontFamily = MeyouwwFonts.Quicksand, fontWeight = FontWeight.SemiBold)

val MeyouwwTypography = Typography(
    displayLarge = heading.copy(fontSize = 52.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold),
    displayMedium = heading.copy(fontSize = 42.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
    displaySmall = heading.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineLarge = heading.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = heading.copy(fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = heading.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = heading.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = heading.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = heading.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = body.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = label.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    labelMedium = label.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = label.copy(fontSize = 11.sp, lineHeight = 16.sp),
)

/** Extra text styles outside the Material type scale. */
object MeyouwwTextStyles {
    val handwritten = TextStyle(fontFamily = MeyouwwFonts.Handwritten, fontSize = 22.sp, lineHeight = 26.sp)
    val soundEffect = TextStyle(fontFamily = MeyouwwFonts.Pop, fontSize = 28.sp, lineHeight = 34.sp)
}
