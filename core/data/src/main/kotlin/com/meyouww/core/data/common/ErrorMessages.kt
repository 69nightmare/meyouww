package com.meyouww.core.data.common

import androidx.annotation.StringRes
import com.meyouww.core.data.R

/** What to tell the person, and what they can do next. */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.error_network
    AppError.NotConfigured -> R.string.error_not_configured
    AppError.SignedOut -> R.string.error_signed_out
    AppError.InvalidCode -> R.string.error_invalid_code
    AppError.RateLimited -> R.string.error_rate_limited
    AppError.Rejected -> R.string.error_rejected
    AppError.Unknown -> R.string.error_unknown
}
