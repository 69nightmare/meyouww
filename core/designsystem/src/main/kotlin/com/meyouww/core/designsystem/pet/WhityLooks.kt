package com.meyouww.core.designsystem.pet

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.meyouww.core.designsystem.R

/** The outfit, drawn to lay over any of her reactions (made by scripts/whity_reactions.py). */
@get:DrawableRes
internal val WhityOutfit.picture: Int
    get() = when (this) {
        WhityOutfit.Bow -> R.drawable.whity_outfit_bow
        WhityOutfit.PartyHat -> R.drawable.whity_outfit_party_hat
        WhityOutfit.FlowerCrown -> R.drawable.whity_outfit_flower_crown
        WhityOutfit.CoolShades -> R.drawable.whity_outfit_cool_shades
        WhityOutfit.Beanie -> R.drawable.whity_outfit_beanie
        WhityOutfit.SantaHat -> R.drawable.whity_outfit_santa_hat
        WhityOutfit.Diyas -> R.drawable.whity_outfit_diyas
        WhityOutfit.Holi -> R.drawable.whity_outfit_holi
        WhityOutfit.Kite -> R.drawable.whity_outfit_kite
    }

@get:StringRes
internal val WhityOutfit.label: Int
    get() = when (this) {
        WhityOutfit.Bow -> R.string.whity_outfit_bow
        WhityOutfit.PartyHat -> R.string.whity_outfit_party_hat
        WhityOutfit.FlowerCrown -> R.string.whity_outfit_flower_crown
        WhityOutfit.CoolShades -> R.string.whity_outfit_cool_shades
        WhityOutfit.Beanie -> R.string.whity_outfit_beanie
        WhityOutfit.SantaHat -> R.string.whity_outfit_santa_hat
        WhityOutfit.Diyas -> R.string.whity_outfit_diyas
        WhityOutfit.Holi -> R.string.whity_outfit_holi
        WhityOutfit.Kite -> R.string.whity_outfit_kite
    }

@get:DrawableRes
internal val WhityTreat.picture: Int
    get() = when (this) {
        WhityTreat.Cookie -> R.drawable.whity_treat_cookie
        WhityTreat.Momo -> R.drawable.whity_treat_momo
        WhityTreat.IceCream -> R.drawable.whity_treat_ice_cream
        WhityTreat.Bone -> R.drawable.whity_treat_bone
        WhityTreat.Laddoo -> R.drawable.whity_treat_laddoo
        WhityTreat.Gujiya -> R.drawable.whity_treat_gujiya
        WhityTreat.Cupcake -> R.drawable.whity_treat_cupcake
    }

@get:StringRes
internal val WhityTreat.label: Int
    get() = when (this) {
        WhityTreat.Cookie -> R.string.whity_treat_cookie
        WhityTreat.Momo -> R.string.whity_treat_momo
        WhityTreat.IceCream -> R.string.whity_treat_ice_cream
        WhityTreat.Bone -> R.string.whity_treat_bone
        WhityTreat.Laddoo -> R.string.whity_treat_laddoo
        WhityTreat.Gujiya -> R.string.whity_treat_gujiya
        WhityTreat.Cupcake -> R.string.whity_treat_cupcake
    }

/** The line in words. */
@Composable
internal fun WhityLine.words(): String = when (this) {
    is WhityLine.Earned -> stringResource(
        when (kind) {
            WhityEarn.Message -> R.string.whity_say_earned_message
            WhityEarn.Doodle -> R.string.whity_say_earned_doodle
            WhityEarn.Reminder -> R.string.whity_say_earned_reminder
        },
    )
    is WhityLine.Unlocked -> stringResource(R.string.whity_say_unlocked, days, stringResource(outfit.label))
    is WhityLine.Festival -> greeting
    is WhityLine.Next -> text
    is WhityLine.Streak -> stringResource(R.string.whity_say_streak, days)
    is WhityLine.Doodles -> pluralStringResource(R.plurals.whity_say_doodles, count, count)
    is WhityLine.Messages -> pluralStringResource(R.plurals.whity_say_messages, count, count)
    is WhityLine.Reminders -> pluralStringResource(R.plurals.whity_say_reminders, count, count)
    WhityLine.Hungry -> stringResource(R.string.whity_say_hungry)
    WhityLine.Morning -> stringResource(R.string.whity_say_morning)
    WhityLine.Evening -> stringResource(R.string.whity_say_evening)
    WhityLine.Night -> stringResource(R.string.whity_say_night)
    is WhityLine.Fact -> stringArrayResource(R.array.whity_facts).let { it[Math.floorMod(index, it.size)] }
    WhityLine.HoldHint -> stringResource(R.string.whity_hint_hold)
}
