package com.meyouww.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.meyouww.core.data.common.CrashLog
import com.meyouww.core.notifications.channels.MeyouwwChannels
import com.meyouww.core.notifications.push.FirebaseBootstrap
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Kept deliberately light: a push can cold-start the process while the app is
 * closed, and onMessageReceived only has a few seconds. Heavy setup (the
 * Supabase client, Tink) is created lazily on first use.
 */
@HiltAndroidApp
class MeyouwwApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        MeyouwwChannels.createAll(this)
        FirebaseBootstrap.initialize(this)
    }
}
