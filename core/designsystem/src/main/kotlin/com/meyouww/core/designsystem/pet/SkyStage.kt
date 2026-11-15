package com.meyouww.core.designsystem.pet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.meyouww.core.designsystem.component.skyHalo

/**
 * Living Sky's stage at the top of a main screen ("stage, then story"): Whity on the sky on the
 * right (tap to pet her, hold her for her corner), and the one thing to know right now on the left.
 * Her paws hang a little over whatever comes next.
 */
@Composable
fun SkyStage(
    text: String?,
    modifier: Modifier = Modifier,
    whity: Dp = 124.dp,
    height: Dp = 136.dp,
    idle: WhityReaction? = null,
    chatty: Boolean = true,
    next: String? = null,
) {
    Box(modifier.fillMaxWidth().height(height).zIndex(1f)) {
        text?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge.copy(shadow = skyHalo()),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(0.56f).padding(start = 4.dp, bottom = 20.dp),
            )
        }
        WhityBuddy(
            width = whity,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp).offset(y = 18.dp),
            idle = idle,
            chatty = chatty,
            next = next,
        )
    }
}
