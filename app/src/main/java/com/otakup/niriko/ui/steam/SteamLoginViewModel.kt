package com.otakup.niriko.ui.steam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.remote.steam.SteamApiClient
import com.otakup.niriko.data.remote.steam.SteamOpenIdClient
import com.otakup.niriko.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Steam 登录阶段。 */
enum class SteamLoginStatus {
    /** 未登录，等待操作。 */
    IDLE,
    /** 正在构造登录（WebView 加载中）。 */
    LOADING,
    /** 已登录（steamId64 已保存）。 */
    LOGGED_IN,
    /** 登录失败（网络/解析错误）。 */
    ERROR,
}

/** 登录页 UI 状态。 */
data class SteamLoginUiState(
    val status: SteamLoginStatus = SteamLoginStatus.IDLE,
    val steamId64: String = "",
    /** 错误信息（status=ERROR 时非空）。 */
    val error: String? = null,
    /** OpenID 登录 URL（WebView 加载用）。 */
    val loginUrl: String = "",
) {
    /** 已登录且 SteamID64 合法。 */
    val isLoggedIn: Boolean get() = steamId64.isNotBlank()
}

/**
 * Steam 登录 ViewModel。
 *
 * 支持两种方式：
 * 1. OpenID 2.0 WebView 登录（[onOpenIdCallback] 解析回跳提取 SteamID64）；
 * 2. 手动输入 SteamID64（[loginWithManualSteamId]，绕开 steamcommunity.com 被墙风险）。
 * 登录态持久化到 [SettingsDataStore.steamId64]。
 */
class SteamLoginViewModel(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SteamLoginUiState())
    val uiState: StateFlow<SteamLoginUiState> = _uiState.asStateFlow()

    init {
        // 初始加载：OpenID URL + 已有登录态
        _uiState.update {
            it.copy(
                loginUrl = SteamOpenIdClient.buildLoginUrl(),
            )
        }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                settingsDataStore.settings.first().steamId64
            }
            if (saved.isNotBlank()) {
                _uiState.update {
                    it.copy(status = SteamLoginStatus.LOGGED_IN, steamId64 = saved)
                }
            }
        }
    }

    /** 当前已保存的 SteamID64（供调用方判断是否已登录）。 */
    suspend fun getSavedSteamId64(): String? = withContext(Dispatchers.IO) {
        settingsDataStore.settings.first().steamId64.takeIf { it.isNotBlank() }
    }

    /** 重新开始登录（WebView 重载）。 */
    fun restartLogin() {
        _uiState.update { it.copy(status = SteamLoginStatus.LOADING, error = null) }
    }

    /**
     * OpenID 回跳解析（WebView shouldOverrideUrlLoading 拦截到 niriko://steam-auth 时调用）。
     * @param url 回跳完整 URL
     * @return 解析出的 SteamID64（未命中返回 null）
     */
    fun onOpenIdCallback(url: String): String? {
        val callback = SteamOpenIdClient.parseCallback(url)
        val steamId64 = callback.steamId64 ?: return null
        persistLogin(steamId64)
        return steamId64
    }

    /** 手动输入 SteamID64 登录（校验 17 位数字）。 */
    fun loginWithManualSteamId(input: String): Boolean {
        val trimmed = input.trim()
        if (!isValidSteamId64(trimmed)) {
            _uiState.update { it.copy(status = SteamLoginStatus.ERROR, error = "SteamID64 格式不正确（应为 17 位数字，可从个人资料页 URL 获取）") }
            return false
        }
        persistLogin(trimmed)
        return true
    }

    /**
     * 登录会话 Cookie 就绪后：注入 [SteamApiClient.webCookieProvider] 并后台抓取
     * webapi_token（用户 access token，家庭库 IFamilyGroupsService 用），持久化到
     * [SettingsDataStore.steamWebApiToken]。失败静默（家庭库拉取时再提示）。
     */
    fun captureWebApiToken(cookie: String) {
        if (cookie.isBlank()) return
        SteamApiClient.webCookieProvider = { cookie }
        viewModelScope.launch {
            val token = withContext(Dispatchers.IO) {
                SteamApiClient.fetchWebApiToken()
            }
            if (!token.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    settingsDataStore.setSteamWebApiToken(token)
                }
            }
        }
    }

    /** 退出登录（清除本地登录态）。 */
    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                settingsDataStore.setSteamId64("")
                settingsDataStore.setSteamWebApiToken("")
            }
            SteamApiClient.webCookieProvider = null
            _uiState.update {
                it.copy(status = SteamLoginStatus.IDLE, steamId64 = "", error = null)
            }
        }
    }

    /** 持久化登录态并更新 UI。 */
    private fun persistLogin(steamId64: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { settingsDataStore.setSteamId64(steamId64) }
            _uiState.update {
                it.copy(status = SteamLoginStatus.LOGGED_IN, steamId64 = steamId64, error = null)
            }
        }
    }

    companion object {
        /** SteamID64 合法格式：17 位数字。 */
        fun isValidSteamId64(s: String): Boolean =
            s.length == 17 && s.all(Char::isDigit)
    }
}

class SteamLoginViewModelFactory(
    private val settingsDataStore: SettingsDataStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SteamLoginViewModel::class.java)) {
            return SteamLoginViewModel(settingsDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
