package com.otakup.niriko.ui.steam

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
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
 *    回跳 `niriko://steam-auth` 时拦截并解析 SteamID64；
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
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SteamOpenIdWebView(
    url: String,
    onOpenIdCallback: (String) -> Unit,
    onSessionCookie: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    private var pendingCallback = false

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        val isSteamCallback =
                            url.startsWith(SteamOpenIdClient.RETURN_TO_URL) ||
                                // 兜底：Steam 个别情况下仍可能以 http 形式回跳
                                url.startsWith(
                                    SteamOpenIdClient.RETURN_TO_URL.replaceFirst("https://", "http://"),
                                )
                        if (isSteamCallback) {
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
                loadUrl(url)
            }.also { webViewRef = it }
        },
        update = { view ->
            if (view.url != url && url.isNotBlank()) {
                view.loadUrl(url)
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
