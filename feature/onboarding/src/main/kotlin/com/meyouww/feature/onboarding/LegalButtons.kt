package com.meyouww.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton

/** "Terms" and "Privacy Policy" as two equal buttons side by side. */
@Composable
fun LegalButtons(onOpenTerms: () -> Unit, onOpenPrivacy: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CrayonButton(
            text = stringResource(R.string.signin_terms_link),
            onClick = onOpenTerms,
            kind = ButtonKind.Quiet,
            modifier = Modifier.weight(1f),
        )
        CrayonButton(
            text = stringResource(R.string.signin_privacy_link),
            onClick = onOpenPrivacy,
            kind = ButtonKind.Quiet,
            modifier = Modifier.weight(1f),
        )
    }
}
