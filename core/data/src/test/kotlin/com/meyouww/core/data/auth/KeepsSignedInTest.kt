package com.meyouww.core.data.auth

import com.google.common.truth.Truth.assertThat
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import org.junit.Test
import java.io.IOException

/** The widget's "signed in?" rule: a failed token refresh is not a sign-out. */
class KeepsSignedInTest {

    @Test
    fun `a failed refresh while offline still counts as signed in`() {
        val offline = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("no network")))
        assertThat(keepsSignedIn(offline)).isTrue()
    }

    @Test
    fun `signing out counts as signed out`() {
        assertThat(keepsSignedIn(SessionStatus.NotAuthenticated(isSignOut = true))).isFalse()
    }

    @Test
    fun `never signing in counts as signed out`() {
        assertThat(keepsSignedIn(SessionStatus.NotAuthenticated(isSignOut = false))).isFalse()
    }

    @Test
    fun `still loading the saved session is not yet signed in`() {
        assertThat(keepsSignedIn(SessionStatus.Initializing)).isFalse()
    }
}
