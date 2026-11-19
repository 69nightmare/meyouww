package com.meyouww.feature.onboarding

import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.skyBob
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyChip
import androidx.compose.foundation.layout.offset
import com.meyouww.core.designsystem.component.skyCloudSeat
import com.meyouww.core.designsystem.theme.MeyouwwTextStyles
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.component.Bunting
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import kotlinx.coroutines.launch

private data class WelcomePage(val sticker: WhitySticker, val title: Int, val body: Int)

private val pages = listOf(
    WelcomePage(WhitySticker.GreetWelcome, R.string.welcome_page1_title, R.string.welcome_page1_body),
    WelcomePage(WhitySticker.LoveYou, R.string.welcome_page2_title, R.string.welcome_page2_body),
    WelcomePage(WhitySticker.Teddy, R.string.welcome_page3_title, R.string.welcome_page3_body),
)

/** Three short pages introducing the app, then on to signing in with Google. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex
    val crayon = MeyouwwTheme.crayon
    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = MAX_CONTENT_WIDTH)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append("me")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("you") }
                    append("ww")
                },
                style = MeyouwwTextStyles.wordmark,
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
            )
            if (!last) {
                if (crayon.living) {
                    SkyChip(stringResource(R.string.welcome_skip), onClick = onGetStarted)
                } else {
                    TextButton(onClick = onGetStarted) { Text(stringResource(R.string.welcome_skip), style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            val selected = pagerState.currentPage == index
            val reduced = LocalReducedMotion.current
            val pop by animateFloatAsState(
                targetValue = if (selected || reduced) 1f else 0.94f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
                label = "pop",
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                // Whity on a soft pastel circle, a different colour on each page.
                val blob = listOf(crayon.bubblegum, crayon.lilac, crayon.yellow)[index % 3]
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(250.dp)
                        .graphicsLayer {
                            scaleX = pop
                            scaleY = pop
                            rotationZ = (1f - pop) * -12f
                        }
                        // Living Sky: she sits on a cloud instead of a circle.
                        .then(if (crayon.living) Modifier.skyCloudSeat() else Modifier.background(blob, CircleShape)),
                ) {
                    Sticker(sticker = page.sticker, decorative = true, size = 210.dp)
                    if (crayon.living && index == 0) {
                        // The board's three things meyouww is for, floating round her.
                        Box(Modifier.matchParentSize().clearAndSetSemantics { }) {
                            SkyChip(stringResource(R.string.welcome_sky_chat), tone = SkyTone.Pink, icon = SkyIcons.Chat, modifier = Modifier.align(Alignment.TopStart).offset(x = (-30).dp, y = (-6).dp).skyBob(0))
                            SkyChip(stringResource(R.string.welcome_sky_doodles), tone = SkyTone.Butter, icon = SkyIcons.Pencil, modifier = Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = 24.dp).skyBob(1))
                            SkyChip(stringResource(R.string.welcome_sky_wallpapers), tone = SkyTone.Blue, icon = SkyIcons.Wallpaper, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-24).dp, y = (-24).dp).skyBob(2))
                        }
                    }
                }
                Text(
                    stringResource(page.title),
                    style = if (crayon.living) SkyType.h1.copy(shadow = skyHalo()) else MaterialTheme.typography.headlineLarge,
                    color = if (crayon.living) SkyInk.onSky else androidx.compose.ui.graphics.Color.Unspecified,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(page.body),
                    style = if (crayon.living) SkyType.body.copy(shadow = skyHalo()) else MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = if (crayon.living) SkyInk.onSkySoft else crayon.textSoft,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        val indicator = stringResource(R.string.welcome_page_indicator, pagerState.currentPage + 1, pages.size)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .semantics { contentDescription = indicator },
        ) {
            pages.indices.forEach { i ->
                val current = i == pagerState.currentPage
                Box(
                    Modifier
                        .size(width = if (current) (if (crayon.living) 22.dp else 26.dp) else (if (crayon.living) 8.dp else 9.dp), height = if (crayon.living) 8.dp else 9.dp)
                        .background(
                            when {
                                crayon.living && current -> crayon.red
                                crayon.living -> crayon.card.copy(alpha = 0.8f)
                                current -> MaterialTheme.colorScheme.primary
                                else -> crayon.edge.copy(alpha = 0.5f)
                            },
                            CircleShape,
                        ),
                )
            }
        }
        CrayonButton(
            text = stringResource(if (last) R.string.welcome_get_started else R.string.welcome_next),
            onClick = {
                if (last) onGetStarted() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            kind = ButtonKind.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
