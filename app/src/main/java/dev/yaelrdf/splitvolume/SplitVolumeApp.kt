package dev.yaelrdf.splitvolume

import android.app.Application
import android.content.Context

class SplitVolumeApp : Application() {
    val store by lazy { ProfileStore(this) }
    val controller by lazy { SplitController(this, store) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
    }
}

val Context.app: SplitVolumeApp get() = applicationContext as SplitVolumeApp
