package com.otakup.niriko.navigation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-9：页面级预测返回「跟手」由 navigation-compose 自带的
 * PredictiveBackHandler + SeekableTransitionState 提供（2.8 起）。
 *
 * 本测试不改变行为，只把「我们依赖的是哪一半能力」钉住：
 * 1. 依赖版本 >= 2.8（该版本 NavHost 内部用 SeekableTransitionState 让 popEnter/popExit 跟手 seek）；
 * 2. 清单开着 enableOnBackInvokedCallback（否则系统根本不给预测返回事件）；
 * 3. NavHost 四条过渡里 popEnter/popExit 都必须存在且 reduceMotion 时返回 None——
 *    预测返回 seek 的是 pop 过渡，缺一条就会退化成「只有系统箭头、页面不动」；
 * 4. popExit 的缩放幅度不超过 1.12：跟手 seek 会把整页缩到最大幅度，1.08 已经接近观感上限。
 *
 * 未覆盖：真机边缘滑动逐帧观感（仓库没有录屏/真机测试设施，见 docs/v1.2.2/02 §B-9）。
 */
class PredictiveBackSupportTest {

    private val navHost = File("src/main/java/com/otakup/niriko/navigation/NirikoNavHost.kt").readText()
    private val animations = File("src/main/java/com/otakup/niriko/ui/animation/NirikoAnimation.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val catalog = File("../gradle/libs.versions.toml").readText()

    @Test
    fun navigationComposeIsNewEnoughForSeekablePredictiveBack() {
        val version = Regex("navigationCompose\\s*=\\s*\"([^\"]+)\"")
            .find(catalog)?.groupValues?.get(1)
            ?: error("libs.versions.toml 里找不到 navigationCompose 版本")
        val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
        val atLeast28 = parts[0] > 2 || (parts[0] == 2 && parts.getOrElse(1) { 0 } >= 8)
        assertTrue("navigation-compose 需要 >= 2.8 才有 SeekableTransitionState 预测返回 seek，当前 " + version, atLeast28)
    }

    @Test
    fun manifestEnablesPredictiveBackCallback() {
        assertTrue(
            "AndroidManifest 必须保持 android:enableOnBackInvokedCallback=\"true\"，否则系统不会派发预测返回事件",
            manifest.contains("enableOnBackInvokedCallback=\"true\""),
        )
    }

    @Test
    fun navHostDeclaresSeekablePopTransitionsWithReduceMotionFallback() {
        listOf("enterTransition", "exitTransition", "popEnterTransition", "popExitTransition").forEach { name ->
            assertTrue("NirikoNavHost 缺少 " + name + "（预测返回 seek 的是 pop 过渡）", navHost.contains(name + " = {"))
        }
        val popEnter = navHost.substringAfter("popEnterTransition = {").substringBefore("},")
        val popExit = navHost.substringAfter("popExitTransition = {").substringBefore("},")
        listOf("popEnterTransition" to popEnter, "popExitTransition" to popExit).forEach { (name, block) ->
            assertTrue(name + " 缺少 reduceMotion -> None 分支", block.contains("reduceMotion ->") && block.contains("None"))
        }
    }

    @Test
    fun popExitScaleStaysWithinTwelvePercent() {
        val scale = Regex("fun popExitSharedAxisZ[\\s\\S]*?targetScale = ([0-9.]+)f")
            .find(animations)?.groupValues?.get(1)?.toFloatOrNull()
            ?: error("NirikoAnimation.kt 里找不到 popExitSharedAxisZ 的 targetScale")
        assertTrue("预测返回跟手会把整页缩到 targetScale，" + scale + " 超过 12% 会露出太多背景", scale <= 1.12f)
    }
}
