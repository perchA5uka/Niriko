package com.otakup.niriko.ui.components

import android.app.Application
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.kyant.backdrop.Backdrop
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GlassProviderIdentityTest {
    private class NoDrawApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) {}
        override fun insertBottomUp(index: Int, instance: Unit) {}
        override fun move(from: Int, to: Int, count: Int) {}
        override fun remove(index: Int, count: Int) {}
        override fun onClear() {}
    }

    @Test fun nullLiveAndFallbackSourcesDoNotDisposeOrReplaceRememberedSharedChildren() {
        val backdrop = Proxy.newProxyInstance(Backdrop::class.java.classLoader, arrayOf(Backdrop::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TestBackdrop"
                "isCoordinatesDependent", "getIsCoordinatesDependent" -> false
                else -> null
            }
        } as Backdrop
        val recomposer = Recomposer(Dispatchers.Unconfined)
        val composition = Composition(NoDrawApplier(), recomposer)
        var remembered = 0
        var disposed = 0
        val identities = mutableListOf<Any>()
        val child: @Composable () -> Unit = {
            val identity = remember { remembered++; Any() }
            DisposableEffect(Unit) { onDispose { disposed++ } }
            SideEffect { identities += identity }
        }
        fun render(source: Backdrop?) {
            composition.setContent { ProvideCardGlassBackdrop(source, child) }
        }
        try {
            render(null)
            render(backdrop)
            render(null)
            render(backdrop)
            assertEquals("Source changes must not create another remembered shared node", 1, remembered)
            assertEquals("Shared children must stay attached throughout source switching", 0, disposed)
            assertTrue(identities.isNotEmpty())
            assertTrue(identities.all { it === identities.first() })
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
        assertEquals(1, disposed)
    }
}
