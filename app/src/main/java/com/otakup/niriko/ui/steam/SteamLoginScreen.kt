package com.otakup.niriko.ui.steam

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.otakup.niriko.data.remote.steam.SteamOpenIdClient

/**
 * Steam 登录页。
 *
 * 两种方式：
 * 1. WebView 承载 Steam OpenID 2.0 登录页（官方登录体验）；
 *    回跳 `https://localhost/steam-auth` 时拦截并解析 SteamID64（见 SteamOpenIdClient.RETURN_TO_URL）；
 * 2. 手动输入 SteamID64 兜底（steamcommunity.com 不可达时）。
 *
 * 登录成功回调 [onLoginSuccess]（steamId64）。
 */
@Composable
fun SteamLoginScreen(
    viewModel: SteamLoginViewModel,
    onLoginSuccess: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var manualInput by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // 标题
        Text(
            "Steam 登录",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "登录后可从 Steam 游戏库快速导入。官方登录需能访问 steamcommunity.com；网络不可达时可改用下方手动输入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (state.isLoggedIn) {
            // 已登录态
            Text(
                "已登录：${state.steamId64}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = { onLoginSuccess(state.steamId64) }) {
                    Text("继续导入游戏库")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = viewModel::logout) {
                    Text("退出登录")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = viewModel::restartLogin) {
                Text("重新登录")
            }
        } else {
            // WebView 登录
            SteamOpenIdWebView(
                url = state.loginUrl,
                reloadSeq = state.reloadSeq,
                onOpenIdCallback = { url ->
                    viewModel.onOpenIdCallback(url)?.let { onLoginSuccess(it) }
                },
                onSessionCookie = { cookie ->
                    // store 域会话 Cookie 就绪：注入并后台抓取 webapi_token（家庭库用）
                    viewModel.captureWebApiToken(cookie)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            )

            Spacer(Modifier.height(16.dp))

            // 手动输入兜底
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "手动输入 SteamID64（兜底）",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    label = { Text("SteamID64（17 位数字）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (viewModel.loginWithManualSteamId(manualInput)) {
                            onLoginSuccess(manualInput.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("手动登录")
                }
            }

            // 错误提示
            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        }
    }
}

/**
 * 承载 Steam OpenID 登录页的 WebView。
 * 拦截 `https://localhost/steam-auth` 回跳并回调原始 URL（解析由调用方完成）。
 * 回跳后自动导航到 store 域触发 SSO 登录，完成后回调 [onSessionCookie]（store 域会话 Cookie，
 * 用于抓取 webapi_token 家庭库 token）。
 *
 * @param reloadSeq 「重新登录」序号；值变化即强制重载（见 [SteamLoginViewModel.restartLogin]）
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SteamOpenIdWebView(
    url: String,
    reloadSeq: Int,
    onOpenIdCallback: (String) -> Unit,
    onSessionCookie: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 载入/错误页是 WebView 的一次性状态，只在回调与 update 里读写，不能放进 Compose 状态
    val host = remember { SteamWebHost() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // 修复 R4：steamcommunity.com 的登录页是桌面版布局，缺下面这几项时会在
                // 窄 WebView 里被压成极小一条（你反馈的「网页显示页面过小、登不上」）。
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                // 必须显式给桌面 UA：默认 UA 带 `; wv`，Steam 的 Akamai 会把它当非浏览器拦成
                // Access Denied（errors.edgesuite.net）。移动 UA 反而触发另一套风控且页面被压窄。
                settings.userAgentString = SteamOpenIdClient.DESKTOP_USER_AGENT
                // Steam 的 SSO 依赖跨站 Cookie，默认策略会把它拒掉 → 登录后拿不到会话
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?,
                    ) {
                        super.onPageStarted(view, url, favicon)
                        // 403 现场定位：第一条 URL 与真实 UA（adb logcat -s SteamOpenId）
                        Log.i(TAG, "$url | ua=${view?.settings?.userAgentString}")
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        val statusCode = errorResponse?.statusCode ?: return
                        if (!SteamOpenIdClient.isMainFrameHttpError(request?.isForMainFrame == true, statusCode)) {
                            return
                        }
                        val page = SteamOpenIdClient.httpErrorPage(statusCode) ?: return
                        // HTTP 状态码错误（403 Access Denied 等）不触发 onReceivedError：
                        // 不接管的话用户只会看到 Akamai 的英文报错页，不知道还有手动兜底
                        host.failureShown = true
                        view?.loadData(steamErrorPageHtml(page.title, page.detail), "text/html", "UTF-8")
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?,
                    ) {
                        // 失败必须可见：steamcommunity.com 在国区常不可达，改前失败后界面毫无提示
                        if (request?.isForMainFrame == true) {
                            host.failureShown = true
                            view?.loadData(
                                steamErrorPageHtml(
                                    "无法加载 Steam 登录页",
                                    "域名：steamcommunity.com<br>" +
                                        "该域名在部分网络环境下不可达。可改用下方的手动输入 SteamID64。",
                                ),
                                "text/html",
                                "UTF-8",
                            )
                        }
                    }

                    private var pendingCallback = false

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        // 回跳判定抽到 SteamOpenIdClient（纯函数，https/http 两种回跳都认）
                        if (SteamOpenIdClient.isCallbackUrl(url)) {
                            // OpenID 回跳：回调解析，不再继续加载回跳 URL；
                            // 转而加载 store 主页触发 SSO，使 store 域 Cookie 就绪
                            pendingCallback = true
                            onOpenIdCallback(url)
                            view?.loadUrl("https://store.steampowered.com/")
                            return true
                        }
                        // 其余（steamcommunity.com 登录页等）留在 WebView 内
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // 真实页面渲染成功即清掉错误态（错误页是 loadData，url 不会是 http）
                        if (url?.startsWith("http") == true) {
                            host.failureShown = false
                        }
                        if (pendingCallback && url?.startsWith("https://store.steampowered.com/") == true) {
                            pendingCallback = false
                            val cookie = android.webkit.CookieManager.getInstance()
                                .getCookie("https://store.steampowered.com")
                            if (!cookie.isNullOrBlank()) {
                                onSessionCookie(cookie)
                            }
                        }
                    }
                }
                // 首次载入由 factory 负责；先记下 seq，避免随后的 update 再加载一次
                host.lastReloadSeq = reloadSeq
                loadUrl(url)
            }.also { webViewRef = it }
        },
        update = { view ->
            if (url.isNotBlank()) {
                if (reloadSeq != host.lastReloadSeq) {
                    // 「重新登录」只改 VM 状态时 url 不变 → 旧的 `view.url != url` 永远不成立，
                    // WebView 不会重载；seq 变化是唯一可靠的触发信号
                    host.lastReloadSeq = reloadSeq
                    host.failureShown = false
                    view.loadUrl(url)
                } else if (!host.failureShown && view.url != url) {
                    // 错误页是用 loadData 渲染的，此时不要把登录页又加载回来
                    view.loadUrl(url)
                }
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}

/** 诊断日志 tag（adb logcat -s SteamOpenId）。 */
private const val TAG = "SteamOpenId"

/** WebView 的载入状态（不参与重组，仅回调与 update 之间共享）。 */
private class SteamWebHost {
    /** 已消费的 reloadSeq；初值 -1 保证 factory 之后第一次 update 不会重复加载。 */
    var lastReloadSeq: Int = -1

    /** 当前展示的是错误页：阻止 update 把登录页又加载回来、把错误提示冲掉。 */
    var failureShown: Boolean = false
}

/** 错误页 HTML（loadData 渲染，纯内联样式，不依赖网络资源）。 */
private fun steamErrorPageHtml(heading: String, detail: String): String =
    "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>" +
        "<body style=\"font-family:sans-serif;padding:16px;line-height:1.6\">" +
        "<b>$heading</b><p>$detail</p></body></html>"
