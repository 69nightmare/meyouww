package com.meyouww.feature.onboarding

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
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import kotlinx.coroutines.launch

private data class WelcomePage(val sticker: WhitySticker, val title: Int, val body: Int)

private val pages = listOf(
    WelcomePage(WhitySticker.Hi, R.string.welcome_page1_title, R.string.welcome_page1_body),
    WelcomePage(WhitySticker.LoveYou, R.string.welcome_page2_title, R.string.welcome_page2_body),
    WelcomePage(WhitySticker.Teddy, R.string.welcome_page3_title, R.string.welcome_page3_body),
)

/** Three short pages introducing the app, then on to the age gate. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex
    val crayon = MeyouwwTheme.crayon
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Bunting()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) {
                TextButton(onClick = onGetStarted) { Text(stringResource(R.string.welcome_skip)) }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            val selected = pagerState.currentPage == index
            val reduced = LocalReducedMotion.current
            val pop by animateFloatAsState(
                targetValue = if (selected || reduced) 1f else 0.7f,
                animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
                label = "pop",
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            ) {
                Sticker(
                    sticker = page.sticker,
                    decorative = true,
                    size = 220.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pop
                        scaleY = pop
                        rotationZ = (1f - pop) * -12f
                    },
                )
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = crayon.textSoft,
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
                Box(
                    Modifier
                        .size(if (i == pagerState.currentPage) 18.dp else 12.dp)
                        .crayonSurface(
                            fill = if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary else crayon.yellow,
                            outline = crayon.outline,
                            seed = i,
                            cornerRadius = 9.dp,
                            strokeWidth = 1.5.dp,
                            wobble = 0.3.dp,
                        ),
                )
            }
        }
        CrayonButton(
            text = stringResource(if (last) R.string.welcome_get_started else R.string.welcome_next),
            onClick = {
                if (last) onGetStarted() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            kind = if (last) ButtonKind.Primary else ButtonKind.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
