package com.meyouww.core.designsystem.pet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.TapeLabel
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import java.time.LocalDate

/**
 * Whity's corner, a sheet that opens when you hold her: a big Whity to pet, her mood meter, the
 * treat jar (topped up each morning, more earned by using the app), your visit streak, the
 * outfits it unlocks, how often she's been petted, and a way to hide her from the screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhityCorner(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pet by remember(context) { WhityPetStore.state(context) }.collectAsState()
    val crayon = MeyouwwTheme.crayon
    val haptics = LocalHapticFeedback.current
    var cue by remember { mutableStateOf<WhityCue?>(null) }
    val today = LocalDate.now()
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
                pet = pet,
                treat = WhityTreat.on(today),
                onGive = {
                    if (WhityPetStore.feed(context)) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        cue = WhityCue(WhityReaction.Treat)
                    }
                },
            )
            StreakCard(pet)
            Outfits(
                pet = pet,
                festival = WhityFestivals.on(today)?.outfit,
                onWear = { outfit ->
                    WhityPetStore.update(context) { WhityRules.wear(it, outfit) }
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    cue = WhityCue(WhityReaction.Tap)
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
        animationSpec = if (LocalReducedMotion.current) snap() else spring(dampingRatio = 0.6f),
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
                .crayonSurface(fill = MaterialTheme.colorScheme.surface, outline = crayon.edge, seed = 904, cornerRadius = 11.dp, strokeWidth = 1.5.dp)
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
                    .background(crayon.red, RoundedCornerShape(8.dp)),
            )
        }
        Text(stringResource(moodTip(pet.level)), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreatJar(pet: WhityPetState, treat: WhityTreat, onGive: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val treats = pet.treats
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 906) {
        Text(stringResource(R.string.whity_treat_jar), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.whity_treat_today, stringResource(treat.label)), style = MaterialTheme.typography.bodyMedium)
        // The treats are a picture of the count, which the line under them says.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clearAndSetSemantics { },
        ) {
            val snack = painterResource(treat.picture)
            repeat(WhityPetState.JAR_SIZE) { i ->
                val full = i < treats
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .crayonSurface(
                            fill = MaterialTheme.colorScheme.surface.copy(alpha = if (full) 1f else 0.45f),
                            outline = crayon.outline.copy(alpha = if (full) 1f else 0.35f),
                            seed = 907 + i,
                            cornerRadius = 22.dp,
                            strokeWidth = 2.dp,
                        ),
                ) {
                    if (full) Image(snack, contentDescription = null, modifier = Modifier.size(32.dp))
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
        Text(stringResource(R.string.whity_earn_how), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                WhityEarn.Message to R.string.whity_earn_message,
                WhityEarn.Doodle to R.string.whity_earn_doodle,
                WhityEarn.Reminder to R.string.whity_earn_reminder,
            ).forEach { (kind, name) ->
                val done = (pet.earnedToday and kind.bit) != 0
                val words = stringResource(name)
                val said = stringResource(if (done) R.string.whity_earn_done else R.string.whity_earn_open, words)
                Text(
                    (if (done) "✓ " else "") + words,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .crayonSurface(
                            fill = if (done) crayon.green else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            outline = crayon.outline.copy(alpha = if (done) 1f else 0.5f),
                            seed = 930 + kind.ordinal,
                            cornerRadius = 14.dp,
                            strokeWidth = 2.dp,
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clearAndSetSemantics { contentDescription = said },
                )
            }
        }
    }
}

@Composable
private fun StreakCard(pet: WhityPetState) {
    val crayon = MeyouwwTheme.crayon
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.blue, seed = 910) {
        Text(stringResource(R.string.whity_streak_title), style = MaterialTheme.typography.titleMedium)
        Text("💛 " + pluralStringResource(R.plurals.whity_streak_days, pet.streak, pet.streak), style = MaterialTheme.typography.headlineSmall)
        if (pet.bestStreak > pet.streak) {
            Text(pluralStringResource(R.plurals.whity_streak_best, pet.bestStreak, pet.bestStreak), style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.whity_streak_how), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Outfits(pet: WhityPetState, festival: WhityOutfit?, onWear: (WhityOutfit) -> Unit) {
    val crayon = MeyouwwTheme.crayon
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.green, seed = 912) {
        Text(stringResource(R.string.whity_outfits_title), style = MaterialTheme.typography.titleMedium)
        if (festival != null) {
            Text(stringResource(R.string.whity_outfit_festival, stringResource(festival.label)), style = MaterialTheme.typography.bodyMedium)
        }
        Text(stringResource(R.string.whity_outfits_how), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WhityOutfit.forStreaks.forEach { outfit ->
                OutfitTile(
                    outfit = outfit,
                    unlocked = outfit in pet.unlocked,
                    wearing = pet.outfit == outfit,
                    onWear = { onWear(outfit) },
                )
            }
        }
    }
}

@Composable
private fun OutfitTile(outfit: WhityOutfit, unlocked: Boolean, wearing: Boolean, onWear: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val name = stringResource(outfit.label)
    val state = when {
        wearing -> stringResource(R.string.whity_outfit_wearing)
        unlocked -> null
        else -> pluralStringResource(R.plurals.whity_outfit_locked, outfit.streak, outfit.streak)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(92.dp)
            .crayonSurface(
                fill = if (wearing) crayon.bubblegum else MaterialTheme.colorScheme.surface,
                outline = if (wearing) crayon.red else crayon.outline.copy(alpha = if (unlocked) 1f else 0.4f),
                seed = 940 + outfit.ordinal,
                cornerRadius = 18.dp,
                strokeWidth = if (wearing) 2.5.dp else 1.5.dp,
            )
            .toggleable(value = wearing, enabled = unlocked, role = Role.Checkbox, onValueChange = { onWear() })
            .padding(6.dp)
            .semantics(mergeDescendants = true) { if (state != null) stateDescription = state },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(WHITY_ASPECT)
                .alpha(if (unlocked) 1f else 0.35f),
        ) {
            Image(painterResource(R.drawable.whity_react_watch_still), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Image(painterResource(outfit.picture), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Text(name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, color = if (wearing) crayon.onFill else MaterialTheme.colorScheme.onSurface)
        if (!unlocked && state != null) {
            Text("🔒 $state", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = crayon.textSoft)
        }
    }
}
