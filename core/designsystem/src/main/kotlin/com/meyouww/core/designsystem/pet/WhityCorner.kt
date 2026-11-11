package com.meyouww.core.designsystem.pet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.TapeLabel
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme

/**
 * Whity's corner, a sheet that opens when you hold her: a big Whity to pet, her mood meter, the
 * treat jar (three treats a day, refilled each morning), how often she's been petted, and a way
 * to hide her from the screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhityCorner(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pet by remember(context) { WhityPetStore.state(context) }.collectAsState()
    val crayon = MeyouwwTheme.crayon
    val haptics = LocalHapticFeedback.current
    var cue by remember { mutableStateOf<WhityCue?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TapeLabel(stringResource(R.string.whity_corner_title), tape = crayon.yellow)
            WhityBuddy(width = 210.dp, cue = cue, corner = false)
            Text(
                stringResource(R.string.whity_corner_how),
                style = MaterialTheme.typography.bodyMedium,
                color = crayon.textSoft,
                textAlign = TextAlign.Center,
            )
            MoodMeter(pet)
            TreatJar(
                treats = pet.treats,
                onGive = {
                    if (WhityPetStore.feed(context)) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        cue = WhityCue(WhityReaction.Treat)
                    }
                },
            )
            CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.lilac, seed = 905) {
                Text(pluralStringResource(R.plurals.whity_pets_today, pet.petsToday, pet.petsToday), style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.whity_total_pets, pet.totalPets, pet.totalPets) + " · " +
                        pluralStringResource(R.plurals.whity_total_treats, pet.totalTreats, pet.totalTreats),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (pet.shown) {
                TextButton(onClick = {
                    WhityPetStore.update(context) { it.copy(shown = false) }
                    onDismiss()
                }) { Text(stringResource(R.string.whity_hide)) }
                Text(stringResource(R.string.whity_hide_detail), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
            }
        }
    }
}

@Composable
private fun MoodMeter(pet: WhityPetState) {
    val crayon = MeyouwwTheme.crayon
    val target = pet.mood / WhityPetState.MAX_MOOD.toFloat()
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = if (LocalReducedMotion.current) androidx.compose.animation.core.snap() else androidx.compose.animation.core.spring(dampingRatio = 0.6f),
        label = "mood",
    )
    val label = stringResource(moodLabel(pet.level))
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.peach, seed = 903) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.whity_mood), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .crayonSurface(fill = MaterialTheme.colorScheme.surface, outline = crayon.outline, seed = 904, cornerRadius = 11.dp, strokeWidth = 2.dp)
                .padding(4.dp)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
                },
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                    .background(crayon.bubblegum, RoundedCornerShape(8.dp)),
            )
        }
        Text(stringResource(moodTip(pet.level)), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
    }
}

@Composable
private fun TreatJar(treats: Int, onGive: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 906) {
        Text(stringResource(R.string.whity_treat_jar), style = MaterialTheme.typography.titleMedium)
        // The cookies are a picture of the count, which the line under them says.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.clearAndSetSemantics { }) {
            repeat(WhityPetState.DAILY_TREATS) { i ->
                val full = i < treats
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .crayonSurface(
                            fill = MaterialTheme.colorScheme.surface.copy(alpha = if (full) 1f else 0.45f),
                            outline = crayon.outline.copy(alpha = if (full) 1f else 0.35f),
                            seed = 907 + i,
                            cornerRadius = 24.dp,
                            strokeWidth = 2.dp,
                        ),
                ) {
                    if (full) Text("🍪", fontSize = 22.sp)
                }
            }
        }
        Text(
            if (treats > 0) pluralStringResource(R.plurals.whity_treats_left, treats, treats) else stringResource(R.string.whity_treats_none),
            style = MaterialTheme.typography.bodyMedium,
        )
        CrayonButton(
            text = stringResource(R.string.whity_give_treat),
            onClick = onGive,
            enabled = treats > 0,
            kind = ButtonKind.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
