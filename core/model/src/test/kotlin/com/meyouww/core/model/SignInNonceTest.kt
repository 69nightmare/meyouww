package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SignInNonceTest {

    @Test
    fun `sha256 matches the standard test vector`() {
        assertThat(SignInNonce.sha256Hex("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    @Test
    fun `hashed value is the sha256 of the raw value`() {
        val nonce = SignInNonce.create()
        assertThat(nonce.hashed).isEqualTo(SignInNonce.sha256Hex(nonce.raw))
        assertThat(nonce.raw).hasLength(64)
    }

    @Test
    fun `every nonce is different`() {
        val nonces = (1..50).map { SignInNonce.create().raw }.toSet()
        assertThat(nonces).hasSize(50)
    }
}
