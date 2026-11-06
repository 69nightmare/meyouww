package com.meyouww.core.model

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * One-time nonce for "Continue with Google". Google receives the SHA-256 hex
 * of [raw] and embeds it in the ID token; Supabase receives [raw] and checks it,
 * so an intercepted token can't be replayed in another sign-in.
 */
data class SignInNonce(val raw: String, val hashed: String) {
    companion object {
        private val random = SecureRandom()

        fun create(): SignInNonce {
            val bytes = ByteArray(32).also(random::nextBytes)
            val raw = bytes.joinToString("") { "%02x".format(it) }
            return SignInNonce(raw, sha256Hex(raw))
        }

        fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
