package com.meyouww.core.designsystem.pet

import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.skyGlass
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyIconSize
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyButtonTone
import com.meyouww.core.designsystem.component.SkyButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.horizontalScroll
import com.meyouww.core.designsystem.theme.LocalSky
import com.meyouww.core.designsystem.component.kasukabeBackdrop
import com.meyouww.core.designsystem.component.skyCloudSeat
import androidx.compose.ui.graphics.Color
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
        containerColor = LocalSky.current?.let { Color(it.colors.top) } ?: MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Living Sky: her corner has a little sky of its own.
                .then(if (crayon.living) Modifier.kasukabeBackdrop() else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (crayon.living) {
                SkyCorner(
                    pet = pet,
                    cue = cue,
                    treat = WhityTreat.on(today),
                    festival = WhityFestivals.on(today)?.outfit,
                    onGive = {
                        if (WhityPetStore.feed(context)) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            cue = WhityCue(WhityReaction.Treat)
                        }
                    },
                    onWear = { outfit ->
                        WhityPetStore.update(context) { WhityRules.wear(it, outfit) }
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        cue = WhityCue(WhityReaction.Tap)
                    },
                    onHide = {
                        WhityPetStore.update(context) { it.copy(shown = false) }
                        onDismiss()
                    },
                    onClose = onDismiss,
                )
                return@Column
            }
            TapeLabel(stringResource(R.string.whity_corner_title), tape = crayon.yellow)
            // Living Sky: she peeks over a cloud.
            Box(if (crayon.living) Modifier.skyCloudSeat() else Modifier) { WhityBuddy(width = 210.dp, cue = cue, corner = false) }
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

/**
 * Whity's corner in the Living Sky look, as on the board: the title and your streak with a close
 * button; her mood as hearts; Whity on her cloud; the treat jar with today's treat and how to earn
 * more; the outfits as coins (worn, unlocked or still locked); three counts; and "Hide Whity".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SkyCorner(
    pet: WhityPetState,
    cue: WhityCue?,
    treat: WhityTreat,
    festival: WhityOutfit?,
    onGive: () -> Unit,
    onWear: (WhityOutfit) -> Unit,
    onHide: () -> Unit,
    onClose: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.whity_corner_title), style = SkyType.h2.copy(shadow = skyHalo()), color = SkyInk.onSky, modifier = Modifier.semantics { heading() })
            if (pet.streak > 0) {
                Text(pluralStringResource(R.plurals.whity_sky_streak, pet.streak, pet.streak), style = SkyType.sm.copy(fontWeight = FontWeight.Bold, shadow = skyHalo()), color = SkyInk.onSkySoft)
            }
        }
        SkyIconButton(SkyIcons.Close, stringResource(R.string.whity_sky_close), onClose, size = SkyIconSize.Small)
    }
    // Her mood: up to three hearts and the word.
    val hearts = ((pet.mood * 3 + WhityPetState.MAX_MOOD - 1) / WhityPetState.MAX_MOOD).coerceIn(0, 3)
    val mood = stringResource(moodLabel(pet.level))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .align(Alignment.End)
            .skyGlass(999.dp, crayon.card, crayon.hardShadow, Color.Transparent, 0f)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = mood },
    ) {
        repeat(3) { i -> Icon(SkyIcons.Heart, contentDescription = null, tint = if (i < hearts) crayon.red else crayon.edge, modifier = Modifier.size(14.dp)) }
        Text(mood, style = SkyType.pill, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
    }
    Box(Modifier.skyCloudSeat()) { WhityBuddy(width = 190.dp, cue = cue, corner = false, chatty = true) }
    Text(stringResource(R.string.whity_corner_how), style = SkyType.sm.copy(shadow = skyHalo()), color = SkyInk.onSkySoft, textAlign = TextAlign.Center)
    Text(stringResource(moodTip(pet.level)), style = SkyType.hp.copy(shadow = skyHalo()), color = SkyInk.onSkySoft, textAlign = TextAlign.Center)
    // The treat jar.
    SkyCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val snack = painterResource(treat.picture)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .skySurface(crayon.card, crayon.hardShadow, Color.Transparent, 0f, cornerRadius = 16.dp, depth = 3.dp, belly = false, outline = crayon.edge, strokeWidth = 2.dp)
                    .padding(8.dp)
                    .clearAndSetSemantics { },
            ) {
                (0 until WhityPetState.JAR_SIZE).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { i ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(22.dp).drawBehind {
                                    if (i >= pet.treats) drawCircle(crayon.edge, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
                                },
                            ) { if (i < pet.treats) Image(snack, contentDescription = null, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.whity_sky_jar, pet.treats, WhityPetState.JAR_SIZE), style = SkyType.tt)
                Text(stringResource(R.string.whity_treat_today, stringResource(treat.label)), style = SkyType.sb, color = crayon.textSoft)
                SkyButton(stringResource(R.string.whity_sky_give), onGive, small = true, tone = SkyButtonTone.Pink, enabled = pet.treats > 0, modifier = Modifier.padding(top = 4.dp))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.whity_sky_earn), style = SkyType.cap, color = crayon.textSoft)
            listOf(
                WhityEarn.Message to R.string.whity_sky_earn_chat,
                WhityEarn.Doodle to R.string.whity_sky_earn_doodle,
                WhityEarn.Reminder to R.string.whity_sky_earn_reminder,
            ).forEach { (kind, name) ->
                val done = (pet.earnedToday and kind.bit) != 0
                val words = stringResource(name)
                val said = stringResource(if (done) R.string.whity_earn_done else R.string.whity_earn_open, words)
                SkyPill(if (done) "$words ✓" else words, tone = if (done) SkyTone.Mint else SkyTone.Lilac, modifier = Modifier.clearAndSetSemantics { contentDescription = said })
            }
        }
    }
    // Outfits.
    SkyCard(modifier = Modifier.fillMaxWidth(), spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.whity_outfits_title), style = SkyType.tt, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.whity_sky_outfits_tap), style = SkyType.cap, color = crayon.textSoft)
        }
        if (festival != null) {
            Text(stringResource(R.string.whity_outfit_festival, stringResource(festival.label)), style = SkyType.sb, color = crayon.textSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            WhityOutfit.forStreaks.forEach { outfit ->
                val unlocked = outfit in pet.unlocked
                val wearing = pet.outfit == outfit
                val name = stringResource(outfit.label)
                val state = when {
                    wearing -> stringResource(R.string.whity_outfit_wearing)
                    unlocked -> null
                    else -> pluralStringResource(R.plurals.whity_outfit_locked, outfit.streak, outfit.streak)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .width(62.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .toggleable(value = wearing, enabled = unlocked, role = Role.Checkbox, onValueChange = { onWear(outfit) })
                        .padding(vertical = 4.dp)
                        .semantics(mergeDescendants = true) { if (state != null) stateDescription = state },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .drawBehind {
                                if (wearing) drawCircle(crayon.red, radius = size.minDimension / 2 + 2.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                            }
                            .skySurface(crayon.card, crayon.hardShadow, Color.Transparent, 0f, cornerRadius = 26.dp, depth = 3.dp, belly = false)
                            .clip(CircleShape),
                    ) {
                        Image(painterResource(outfit.picture), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(40.dp).alpha(if (unlocked) 1f else 0.3f))
                        if (!unlocked) Icon(SkyIcons.Lock, contentDescription = null, tint = crayon.textSoft, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(14.dp))
                    }
                    Text(
                        if (wearing) stringResource(R.string.whity_outfit_wearing) else if (unlocked) name else pluralStringResource(R.plurals.whity_sky_days, outfit.streak, outfit.streak),
                        style = SkyType.pill.copy(fontWeight = FontWeight.Bold),
                        color = if (wearing) crayon.red else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(pluralStringResource(R.plurals.whity_sky_best, pet.bestStreak, pet.bestStreak), style = SkyType.sb, color = crayon.textSoft)
    }
    // Three counts.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
        listOf(
            pet.petsToday to R.plurals.whity_sky_pets_today,
            pet.totalPets to R.plurals.whity_sky_pets_all,
            pet.totalTreats to R.plurals.whity_sky_treats_all,
        ).forEach { (n, label) ->
            val words = pluralStringResource(label, n, n)
            SkyCard(modifier = Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { contentDescription = "$n $words" }, padding = PaddingValues(horizontal = 8.dp, vertical = 10.dp), corner = 22.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(n.toString(), style = SkyType.h3)
                    Text(words, style = SkyType.pill.copy(fontWeight = FontWeight.Bold), color = crayon.textSoft, textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (pet.shown) {
        SkyButton(stringResource(R.string.whity_hide), onHide, tone = SkyButtonTone.Text)
        Text(stringResource(R.string.whity_hide_detail), style = SkyType.hp.copy(shadow = skyHalo()), color = SkyInk.onSkySoft, textAlign = TextAlign.Center)
    }
}
