package com.meyouww.core.data.common

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Result of a repository call. Failures carry a reason the UI can explain. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

enum class AppError {
    /** No connection, or the server could not be reached. */
    Network,

    /** This build has no backend settings (see docs/SETUP.md). */
    NotConfigured,

    /** Not signed in, or the session could not be refreshed. */
    SignedOut,

    /** The 6-digit code was wrong or expired. */
    InvalidCode,

    /** Too many attempts; wait and try again. */
    RateLimited,

    /** The server refused the request (validation, permissions). */
    Rejected,

    /** Anything else. */
    Unknown,
}

/** Runs [block], mapping exceptions to [AppError] and never swallowing cancellation. */
suspend fun <T> outcomeOf(classify: (Throwable) -> AppError? = { null }, block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Outcome.Failure(classify(e) ?: defaultClassify(e))
    }

fun defaultClassify(e: Throwable): AppError = when (e) {
    is HttpRequestException, is IOException -> AppError.Network
    is RestException -> when (e.statusCode) {
        401, 403 -> AppError.SignedOut
        429 -> AppError.RateLimited
        in 400..499 -> AppError.Rejected
        else -> AppError.Unknown
    }
    is BackendNotConfiguredException -> AppError.NotConfigured
    else -> AppError.Unknown
}

class BackendNotConfiguredException : IllegalStateException("Backend settings are missing from this build")
