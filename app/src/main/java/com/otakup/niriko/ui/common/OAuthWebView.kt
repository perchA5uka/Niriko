package com.otakup.niriko.ui.common

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** 带移动标识的 UA（部分站点按 UA 强跳桌面版）。 */
private const val MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * 统一 OAuth / 第三方登录 WebView。
 *
 * ## 为什么要有这个组件
 *
 * 第 3 轮定位到的确切根因（R4）：三个登录入口各自实现 WebView，配置互不一致——
 * Steam 缺视口设置（桌面页被压成一条）、Bilibili 容器写死 300dp、
 * Bangumi 干脆没有 OAuth UI。本组件把「视口 / 缩放 / Cookie / 错误可见化 / 进度」一次做对，
 * 三个入口共用，后续接第四个源也不必再犯同样错。
 *
 * ## 视口设置为什么是这几项
 *
 * 登录页（steamcommunity.com / passport.bilibili.com / bgm.tv）都是**桌面版布局**：
 * - [android.webkit.WebSettings.useWideViewPort] + [android.webkit.WebSettings.loadWithOverviewMode]
 *   让页面按设备宽度渲染并自动缩放到可见——**缺失时页面会被裁成左上角一小块**；
 * - 缩放开关让用户能自己放大看不清的部分；
 * - [android.webkit.WebSettings.setInitialScale] 在本工程的编译环境里会报 unresolved（见踩坑记录），
 *   因此用上面两项替代，不调用它。
 *
 * ## Cookie
 *
 * 三个站的登录态都依赖 Cookie，且 OAuth 回跳可能跨到另一个域，因此必须开
 * [CookieManager.setAcceptThirdPartyCookies]——默认策略会把它拒掉，表现为「登录成功但拿不到会话」。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OAuthWebView(
    url: String,
    onRedirect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 命中该前缀的 URL 视为 OAuth 回调：不再加载，转交 [onRedirect]。 */
    redirectPrefix: String,
    /** 是否用移动端 UA（部分页面会强跳桌面版）。 */
    mobileUserAgent: Boolean = false,
    /** 自定义 User-Agent（非空时覆盖默认）。 */
    userAgent: String? = null,
    /** 加载失败时展示的域名（错误文案用）。 */
    failureHostHint: String = "",
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var pageTitle by remember { mutableStateOf("") }

    val animatedProgress by animateFloatAsState(progress / 100f, label = "oauthProgress")

    Column(modifier = modifier) {
        // —— 进度 + 当前页标题：让用户知道「正在加载 / 卡在哪一步」 ——
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
            }
        }
        if (pageTitle.isNotBlank() || progress in 1..99) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pageTitle.ifBlank { "正在加载…" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f, fill = true)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // —— 视口（R4 的确切根因，绝不能省） ——
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        // —— 第三方 Cookie（OAuth 回跳跨域必需） ——
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        userAgent?.takeIf { it.isNotBlank() }?.let { settings.userAgentString = it }
                            ?: run {
                                if (mobileUserAgent) {
                                    // 部分站点（B 站）会按 UA 强跳桌面版；补一个移动标识即可
                                    settings.userAgentString = MOBILE_USER_AGENT
                                }
                            }

                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title.orEmpty()
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                pageError = null
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                // 失败必须可见：改造前失败后界面毫无提示，用户只能看到白屏
                                if (request?.isForMainFrame == true) {
                                    pageError = buildString {
                                        append("无法加载登录页")
                                        if (failureHostHint.isNotBlank()) append("（$failureHostHint）")
                                        append("\n错误码 ${error?.errorCode ?: -1}：")
                                        append(error?.description?.toString() ?: "未知错误")
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url?.toString() ?: return false
                                if (redirectPrefix.isNotBlank() && target.startsWith(redirectPrefix)) {
                                    onRedirect(target)
                                    return true
                                }
                                return false
                            }

                            @Deprecated("Android 6 兼容路径")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                val target = url ?: return false
                                if (redirectPrefix.isNotBlank() && target.startsWith(redirectPrefix)) {
                                    onRedirect(target)
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(url)
                    }.also { webViewRef = it }
                },
                update = { view ->
                    if (url.isNotBlank() && view.url != url && !view.url.orEmpty().startsWith(redirectPrefix)) {
                        view.loadUrl(url)
                    }
                },
            )

            pageError?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "可检查网络或更换数据源端点后重试。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (progress in 1..99 && pageError == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}

/**
 * 登录工作流外壳：标题 + 说明 + 可展开的全屏 WebView + 错误/结果行。
 *
 * Bilibili 登录容器此前写死 300dp（反馈 5 的根因之一），这里默认给到屏幕大部分高度，
 * 且允许用户折叠/展开。
 */
@Composable
fun OAuthWebViewShell(
    title: String,
    description: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onToggleExpanded) { Text(if (expanded) "收起" else "展开") }
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            ) { content() }
        }
    }
}
