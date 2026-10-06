package com.otakup.niriko.data.settings

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WallpaperParallaxSettingsTest {
    @Test fun parallaxDefaultsOffAndPersistsThroughExistingSettingsChain() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = SettingsDataStore(context)
        assertFalse(AppSettings().wallpaperParallaxEnabled)
        assertFalse(store.settings.first().wallpaperParallaxEnabled)
        store.setWallpaperParallaxEnabled(true)
        assertTrue(store.settings.first().wallpaperParallaxEnabled)
        assertTrue(SettingsDataStore(context).settings.first().wallpaperParallaxEnabled)
        store.setWallpaperParallaxEnabled(false)
        assertFalse(store.settings.first().wallpaperParallaxEnabled)
        store.restoreFrom(AppSettings(wallpaperParallaxEnabled = true))
        assertTrue(store.settings.first().wallpaperParallaxEnabled)
        store.restoreFrom(AppSettings())
        assertFalse(store.settings.first().wallpaperParallaxEnabled)
    }
}
