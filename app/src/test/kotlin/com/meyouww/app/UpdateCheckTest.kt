package com.meyouww.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun comparesVersionsNumerically() {
        assertThat(isNewer("v0.11.1", "0.11.0-debug")).isTrue()
        assertThat(isNewer("v0.11.0", "0.11.0-debug")).isFalse()
        assertThat(isNewer("v0.12.0", "0.11.9")).isTrue()
        assertThat(isNewer("v0.9.10", "0.10.0")).isFalse()
        assertThat(isNewer("v1.0", "0.99.99")).isTrue()
    }
}
