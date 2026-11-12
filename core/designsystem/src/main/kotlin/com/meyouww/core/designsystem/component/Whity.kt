package com.meyouww.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.theme.LocalReducedMotion

/** The Whity sticker pack, provided by the app owner (see ASSETS.md). */
/**
 * Every Whity sticker. [pack] groups them in the picker: 0 is the original set, the rest
 * are the owner's sheets. The entry NAME is what travels in a message ("whity:NAME"), so
 * names never change once released.
 */
enum class WhitySticker(@param:DrawableRes val drawable: Int, @param:StringRes val label: Int, val pack: Int = 0) {
    Hi(R.drawable.whity_hi, R.string.whity_hi),
    Love(R.drawable.whity_love, R.string.whity_love),
    LoveYou(R.drawable.whity_love_you, R.string.whity_love_you),
    Sleepy(R.drawable.whity_sleepy, R.string.whity_sleepy),
    Bear(R.drawable.whity_bear, R.string.whity_bear),
    Pat(R.drawable.whity_pat, R.string.whity_pat),
    Confused(R.drawable.whity_confused, R.string.whity_confused),
    Cool(R.drawable.whity_cool, R.string.whity_cool),
    Peek(R.drawable.whity_peek, R.string.whity_peek),
    Frog(R.drawable.whity_frog, R.string.whity_frog),
    NotToday(R.drawable.whity_not_today, R.string.whity_not_today),
    Wink(R.drawable.whity_wink, R.string.whity_wink),
    Burrito(R.drawable.whity_burrito, R.string.whity_burrito),
    Cheeks(R.drawable.whity_cheeks, R.string.whity_cheeks),
    Flowers(R.drawable.whity_flowers, R.string.whity_flowers),
    Butterfly(R.drawable.whity_butterfly, R.string.whity_butterfly),
    Hoodie(R.drawable.whity_hoodie, R.string.whity_hoodie),
    Tired(R.drawable.whity_tired, R.string.whity_tired),
    Teddy(R.drawable.whity_teddy, R.string.whity_teddy),
    // Sticker packs (generated)
    MoodWave(R.drawable.whity_p06_01, R.string.whity_p06_01, pack = 6),
    MoodCozy(R.drawable.whity_p06_02, R.string.whity_p06_02, pack = 6),
    MoodLaughing(R.drawable.whity_p06_03, R.string.whity_p06_03, pack = 6),
    MoodNapping(R.drawable.whity_p06_04, R.string.whity_p06_04, pack = 6),
    MoodSillyWink(R.drawable.whity_p06_05, R.string.whity_p06_05, pack = 6),
    MoodSmug(R.drawable.whity_p06_06, R.string.whity_p06_06, pack = 6),
    MoodShocked(R.drawable.whity_p06_07, R.string.whity_p06_07, pack = 6),
    MoodGrumpy(R.drawable.whity_p06_08, R.string.whity_p06_08, pack = 6),
    MoodZoomies(R.drawable.whity_p06_09, R.string.whity_p06_09, pack = 6),
    MoodSmitten(R.drawable.whity_p06_10, R.string.whity_p06_10, pack = 6),
    LoveHugMe(R.drawable.whity_p07_01, R.string.whity_p07_01, pack = 7),
    LoveShy(R.drawable.whity_p07_02, R.string.whity_p07_02, pack = 7),
    LoveBlowingAKiss(R.drawable.whity_p07_03, R.string.whity_p07_03, pack = 7),
    LovePillowNap(R.drawable.whity_p07_04, R.string.whity_p07_04, pack = 7),
    LoveTeddyHug(R.drawable.whity_p07_05, R.string.whity_p07_05, pack = 7),
    LovePuppyEyes(R.drawable.whity_p07_06, R.string.whity_p07_06, pack = 7),
    LoveSoHappy(R.drawable.whity_p07_07, R.string.whity_p07_07, pack = 7),
    LoveChinScratch(R.drawable.whity_p07_08, R.string.whity_p07_08, pack = 7),
    LoveBlanketBurrito(R.drawable.whity_p07_09, R.string.whity_p07_09, pack = 7),
    LoveHappyWag(R.drawable.whity_p07_10, R.string.whity_p07_10, pack = 7),
    OopsFacepalm(R.drawable.whity_p08_01, R.string.whity_p08_01, pack = 8),
    OopsConfused(R.drawable.whity_p08_02, R.string.whity_p08_02, pack = 8),
    OopsAwkward(R.drawable.whity_p08_03, R.string.whity_p08_03, pack = 8),
    OopsCrying(R.drawable.whity_p08_04, R.string.whity_p08_04, pack = 8),
    OopsSick(R.drawable.whity_p08_05, R.string.whity_p08_05, pack = 8),
    OopsDizzy(R.drawable.whity_p08_06, R.string.whity_p08_06, pack = 8),
    OopsGasp(R.drawable.whity_p08_07, R.string.whity_p08_07, pack = 8),
    OopsEyeRoll(R.drawable.whity_p08_08, R.string.whity_p08_08, pack = 8),
    OopsDetective(R.drawable.whity_p08_09, R.string.whity_p08_09, pack = 8),
    OopsHeartbroken(R.drawable.whity_p08_10, R.string.whity_p08_10, pack = 8),
    GrrBarking(R.drawable.whity_p09_01, R.string.whity_p09_01, pack = 9),
    GrrTalkToThePaw(R.drawable.whity_p09_02, R.string.whity_p09_02, pack = 9),
    GrrBoneGuard(R.drawable.whity_p09_03, R.string.whity_p09_03, pack = 9),
    GrrSulking(R.drawable.whity_p09_04, R.string.whity_p09_04, pack = 9),
    GrrScheming(R.drawable.whity_p09_05, R.string.whity_p09_05, pack = 9),
    GrrSnub(R.drawable.whity_p09_06, R.string.whity_p09_06, pack = 9),
    GrrHuff(R.drawable.whity_p09_07, R.string.whity_p09_07, pack = 9),
    GrrAnnoyed(R.drawable.whity_p09_08, R.string.whity_p09_08, pack = 9),
    GrrOnGuard(R.drawable.whity_p09_09, R.string.whity_p09_09, pack = 9),
    GrrSpeechless(R.drawable.whity_p09_10, R.string.whity_p09_10, pack = 9),
    DayStretch(R.drawable.whity_p10_01, R.string.whity_p10_01, pack = 10),
    DayTeaTime(R.drawable.whity_p10_02, R.string.whity_p10_02, pack = 10),
    DaySnack(R.drawable.whity_p10_03, R.string.whity_p10_03, pack = 10),
    DayMusic(R.drawable.whity_p10_04, R.string.whity_p10_04, pack = 10),
    Typing(R.drawable.whity_p10_05, R.string.whity_p10_05, pack = 10),
    DayWaitingAtTheDoor(R.drawable.whity_p10_06, R.string.whity_p10_06, pack = 10),
    DayBathTime(R.drawable.whity_p10_07, R.string.whity_p10_07, pack = 10),
    DayWalkies(R.drawable.whity_p10_08, R.string.whity_p10_08, pack = 10),
    DayThankYou(R.drawable.whity_p10_09, R.string.whity_p10_09, pack = 10),
    PeekLedge(R.drawable.whity_p10_10, R.string.whity_p10_10, pack = 10),
    PartyPartyPopper(R.drawable.whity_p11_01, R.string.whity_p11_01, pack = 11),
    PartyClapping(R.drawable.whity_p11_02, R.string.whity_p11_02, pack = 11),
    PartyBirthdayCake(R.drawable.whity_p11_03, R.string.whity_p11_03, pack = 11),
    PartyJumping(R.drawable.whity_p11_04, R.string.whity_p11_04, pack = 11),
    PartyChampion(R.drawable.whity_p11_05, R.string.whity_p11_05, pack = 11),
    PartyConfetti(R.drawable.whity_p11_06, R.string.whity_p11_06, pack = 11),
    PartyDancing(R.drawable.whity_p11_07, R.string.whity_p11_07, pack = 11),
    PartyStarGlasses(R.drawable.whity_p11_08, R.string.whity_p11_08, pack = 11),
    PartyWow(R.drawable.whity_p11_09, R.string.whity_p11_09, pack = 11),
    PartyKing(R.drawable.whity_p11_10, R.string.whity_p11_10, pack = 11),
    ReactYes(R.drawable.whity_p12_01, R.string.whity_p12_01, pack = 12),
    ReactNo(R.drawable.whity_p12_02, R.string.whity_p12_02, pack = 12),
    ReactShrug(R.drawable.whity_p12_03, R.string.whity_p12_03, pack = 12),
    ReactThumbsUp(R.drawable.whity_p12_04, R.string.whity_p12_04, pack = 12),
    ReactPhoneCall(R.drawable.whity_p12_05, R.string.whity_p12_05, pack = 12),
    ReactChef(R.drawable.whity_p12_06, R.string.whity_p12_06, pack = 12),
    ReactSleuth(R.drawable.whity_p12_07, R.string.whity_p12_07, pack = 12),
    ReactSuperhero(R.drawable.whity_p12_08, R.string.whity_p12_08, pack = 12),
    ReactFlowerCrown(R.drawable.whity_p12_09, R.string.whity_p12_09, pack = 12),
    ReactSeen(R.drawable.whity_p12_10, R.string.whity_p12_10, pack = 12),
    PupCurious(R.drawable.whity_p13_01, R.string.whity_p13_01, pack = 13),
    PupCurledUp(R.drawable.whity_p13_02, R.string.whity_p13_02, pack = 13),
    PupHi(R.drawable.whity_p13_03, R.string.whity_p13_03, pack = 13),
    PupSurprised(R.drawable.whity_p13_04, R.string.whity_p13_04, pack = 13),
    PupPleading(R.drawable.whity_p13_05, R.string.whity_p13_05, pack = 13),
    PupHappy(R.drawable.whity_p13_06, R.string.whity_p13_06, pack = 13),
    PupBurrito(R.drawable.whity_p13_07, R.string.whity_p13_07, pack = 13),
    PupRunning(R.drawable.whity_p13_08, R.string.whity_p13_08, pack = 13),
    PupInLove(R.drawable.whity_p13_09, R.string.whity_p13_09, pack = 13),
    PupGrumpy(R.drawable.whity_p13_10, R.string.whity_p13_10, pack = 13),
    SayHi(R.drawable.whity_p14_01, R.string.whity_p14_01, pack = 14),
    SayBye(R.drawable.whity_p14_02, R.string.whity_p14_02, pack = 14),
    SayThanks(R.drawable.whity_p14_03, R.string.whity_p14_03, pack = 14),
    SaySorry(R.drawable.whity_p14_04, R.string.whity_p14_04, pack = 14),
    SayOkay(R.drawable.whity_p14_05, R.string.whity_p14_05, pack = 14),
    SayWait(R.drawable.whity_p14_06, R.string.whity_p14_06, pack = 14),
    SayYes(R.drawable.whity_p14_07, R.string.whity_p14_07, pack = 14),
    SayNo(R.drawable.whity_p14_08, R.string.whity_p14_08, pack = 14),
    SayLoveU(R.drawable.whity_p14_09, R.string.whity_p14_09, pack = 14),
    SayMissU(R.drawable.whity_p14_10, R.string.whity_p14_10, pack = 14),
    GreetGM(R.drawable.whity_p15_01, R.string.whity_p15_01, pack = 15),
    GreetGN(R.drawable.whity_p15_02, R.string.whity_p15_02, pack = 15),
    GreetOMW(R.drawable.whity_p15_03, R.string.whity_p15_03, pack = 15),
    GreetWelcome(R.drawable.whity_p15_04, R.string.whity_p15_04, pack = 15),
    GreetTakeCare(R.drawable.whity_p15_05, R.string.whity_p15_05, pack = 15),
    GreetCallMe(R.drawable.whity_p15_06, R.string.whity_p15_06, pack = 15),
    GreetOnIt(R.drawable.whity_p15_07, R.string.whity_p15_07, pack = 15),
    GreetHBD(R.drawable.whity_p15_08, R.string.whity_p15_08, pack = 15),
    GreetCongrats(R.drawable.whity_p15_09, R.string.whity_p15_09, pack = 15),
    GreetGetWellSoon(R.drawable.whity_p15_10, R.string.whity_p15_10, pack = 15),
    ToonHii(R.drawable.whity_p16_01, R.string.whity_p16_01, pack = 16),
    ToonYay(R.drawable.whity_p16_02, R.string.whity_p16_02, pack = 16),
    ToonLoveU(R.drawable.whity_p16_03, R.string.whity_p16_03, pack = 16),
    ToonPlease(R.drawable.whity_p16_04, R.string.whity_p16_04, pack = 16),
    ToonCheeky(R.drawable.whity_p16_05, R.string.whity_p16_05, pack = 16),
    ToonHaha(R.drawable.whity_p16_06, R.string.whity_p16_06, pack = 16),
    ToonSleepy(R.drawable.whity_p16_07, R.string.whity_p16_07, pack = 16),
    ToonWhat(R.drawable.whity_p16_08, R.string.whity_p16_08, pack = 16),
    ToonHmph(R.drawable.whity_p16_09, R.string.whity_p16_09, pack = 16),
    ToonOkayy(R.drawable.whity_p16_10, R.string.whity_p16_10, pack = 16),
    ToonHiGM(R.drawable.whity_p17_01, R.string.whity_p17_01, pack = 17),
    ToonHiGN(R.drawable.whity_p17_02, R.string.whity_p17_02, pack = 17),
    ToonHiHello(R.drawable.whity_p17_03, R.string.whity_p17_03, pack = 17),
    ToonHiByee(R.drawable.whity_p17_04, R.string.whity_p17_04, pack = 17),
    ToonHiWelcome(R.drawable.whity_p17_05, R.string.whity_p17_05, pack = 17),
    ToonHiThanks(R.drawable.whity_p17_06, R.string.whity_p17_06, pack = 17),
    ToonHiTakeCare(R.drawable.whity_p17_07, R.string.whity_p17_07, pack = 17),
    ToonHiOMW(R.drawable.whity_p17_08, R.string.whity_p17_08, pack = 17),
    ToonHiWait(R.drawable.whity_p17_09, R.string.whity_p17_09, pack = 17),
    ToonHiIAmHere(R.drawable.whity_p17_10, R.string.whity_p17_10, pack = 17),
    ToonLoveMissU(R.drawable.whity_p18_01, R.string.whity_p18_01, pack = 18),
    ToonLoveHug(R.drawable.whity_p18_02, R.string.whity_p18_02, pack = 18),
    ToonLoveMwah(R.drawable.whity_p18_03, R.string.whity_p18_03, pack = 18),
    ToonLoveBestie(R.drawable.whity_p18_04, R.string.whity_p18_04, pack = 18),
    ToonLoveCuddle(R.drawable.whity_p18_05, R.string.whity_p18_05, pack = 18),
    ToonLoveLoveYa(R.drawable.whity_p18_06, R.string.whity_p18_06, pack = 18),
    ToonLoveNeedU(R.drawable.whity_p18_07, R.string.whity_p18_07, pack = 18),
    ToonLoveIAmHere(R.drawable.whity_p18_08, R.string.whity_p18_08, pack = 18),
    ToonLoveProudOfU(R.drawable.whity_p18_09, R.string.whity_p18_09, pack = 18),
    ToonLoveBlush(R.drawable.whity_p18_10, R.string.whity_p18_10, pack = 18),
    ToonSassReally(R.drawable.whity_p19_01, R.string.whity_p19_01, pack = 19),
    ToonSassHmm(R.drawable.whity_p19_02, R.string.whity_p19_02, pack = 19),
    ToonSassBruh(R.drawable.whity_p19_03, R.string.whity_p19_03, pack = 19),
    ToonSassDead(R.drawable.whity_p19_04, R.string.whity_p19_04, pack = 19),
    ToonSassHehe(R.drawable.whity_p19_05, R.string.whity_p19_05, pack = 19),
    ToonSassSpillTea(R.drawable.whity_p19_06, R.string.whity_p19_06, pack = 19),
    ToonSassOMG(R.drawable.whity_p19_07, R.string.whity_p19_07, pack = 19),
    ToonSassYikes(R.drawable.whity_p19_08, R.string.whity_p19_08, pack = 19),
    ToonSassSus(R.drawable.whity_p19_09, R.string.whity_p19_09, pack = 19),
    ToonSassNoooo(R.drawable.whity_p19_10, R.string.whity_p19_10, pack = 19),
    ToonPartyWoohoo(R.drawable.whity_p20_01, R.string.whity_p20_01, pack = 20),
    ToonPartyClapClap(R.drawable.whity_p20_02, R.string.whity_p20_02, pack = 20),
    ToonPartyHBD(R.drawable.whity_p20_03, R.string.whity_p20_03, pack = 20),
    ToonPartyLetsGo(R.drawable.whity_p20_04, R.string.whity_p20_04, pack = 20),
    ToonPartyWinner(R.drawable.whity_p20_05, R.string.whity_p20_05, pack = 20),
    ToonPartySlay(R.drawable.whity_p20_06, R.string.whity_p20_06, pack = 20),
    ToonPartyCongrats(R.drawable.whity_p20_07, R.string.whity_p20_07, pack = 20),
    ToonPartyDance(R.drawable.whity_p20_08, R.string.whity_p20_08, pack = 20),
    ToonPartyWow(R.drawable.whity_p20_09, R.string.whity_p20_09, pack = 20),
    ToonPartyYouDidIt(R.drawable.whity_p20_10, R.string.whity_p20_10, pack = 20),
    ToonGrrNo(R.drawable.whity_p21_01, R.string.whity_p21_01, pack = 21),
    ToonGrrStop(R.drawable.whity_p21_02, R.string.whity_p21_02, pack = 21),
    ToonGrrUgh(R.drawable.whity_p21_03, R.string.whity_p21_03, pack = 21),
    ToonGrrMine(R.drawable.whity_p21_04, R.string.whity_p21_04, pack = 21),
    ToonGrrWhatever(R.drawable.whity_p21_05, R.string.whity_p21_05, pack = 21),
    ToonGrrMeh(R.drawable.whity_p21_06, R.string.whity_p21_06, pack = 21),
    ToonGrrGoAway(R.drawable.whity_p21_07, R.string.whity_p21_07, pack = 21),
    ToonGrrWatching(R.drawable.whity_p21_08, R.string.whity_p21_08, pack = 21),
    ToonGrrNuhUh(R.drawable.whity_p21_09, R.string.whity_p21_09, pack = 21),
    ToonGrrTch(R.drawable.whity_p21_10, R.string.whity_p21_10, pack = 21),
    // /Sticker packs

}

/**
 * A Whity sticker. Pass [decorative] = true when the sticker only repeats what
 * nearby text says, so TalkBack does not read it twice.
 */
@Composable
fun Sticker(
    sticker: WhitySticker,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    decorative: Boolean = false,
) {
    Image(
        painter = painterResource(sticker.drawable),
        contentDescription = if (decorative) null else stringResource(sticker.label),
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/** Sticker packs in picker order (the original set first), each with its stickers. */
object WhityPacks {
    val all: List<Pair<Int, List<WhitySticker>>> by lazy { WhitySticker.entries.groupBy { it.pack }.toList() }

    /** The pack's name for its tab ("Classic", "Moods", …). */
    @StringRes
    fun title(pack: Int): Int = when (pack) {
        6 -> R.string.whity_pack_06
        7 -> R.string.whity_pack_07
        8 -> R.string.whity_pack_08
        9 -> R.string.whity_pack_09
        10 -> R.string.whity_pack_10
        11 -> R.string.whity_pack_11
        12 -> R.string.whity_pack_12
        13 -> R.string.whity_pack_13
        14 -> R.string.whity_pack_14
        15 -> R.string.whity_pack_15
        16 -> R.string.whity_pack_16
        17 -> R.string.whity_pack_17
        18 -> R.string.whity_pack_18
        19 -> R.string.whity_pack_19
        20 -> R.string.whity_pack_20
        21 -> R.string.whity_pack_21
        else -> R.string.whity_pack_00
    }
}

/** Whity bouncing while something loads. Holds still when animations are off. */
@Composable
fun WhityLoading(
    message: String,
    modifier: Modifier = Modifier,
    sticker: WhitySticker = WhitySticker.PartyJumping,
) {
    val reducedMotion = LocalReducedMotion.current
    val bounce = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "whity-bounce")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bounce",
        )
        value
    }
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Sticker(
            sticker = sticker,
            decorative = true,
            size = 110.dp,
            modifier = Modifier.graphicsLayer {
                translationY = -18.dp.toPx() * bounce
                // squash at the bottom of the bounce, stretch at the top
                scaleX = 1.06f - 0.08f * bounce
                scaleY = 0.94f + 0.08f * bounce
            },
        )
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
