package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.backup.BackupManager
import com.otakup.niriko.data.backup.ImportResult
import com.otakup.niriko.data.settings.AppSettings
import com.otakup.niriko.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 备份/恢复页面 UI 状态。 */
data class BackupUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val exportResult: String? = null,
    val importResult: ImportResult? = null,
    val error: String? = null,
)

class BackupViewModel(
    private val backupManager: BackupManager,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** 生成导出文件名。 */
    fun getExportFileName(): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "niriko_backup_$now.json"
    }

    /**
     * 导出数据为 JSON 字符串。
     * @return JSON 字符串，失败时返回 null
     */
    suspend fun export(): String? {
        _uiState.update { it.copy(isExporting = true, error = null, exportResult = null) }
        return try {
            val settings = withContext(Dispatchers.IO) {
                settingsDataStore.settings.first()
            }
            val json = withContext(Dispatchers.IO) {
                backupManager.exportToJson(settings = settings)
            }
            _uiState.update { it.copy(isExporting = false, exportResult = "导出成功") }
            json
        } catch (e: Exception) {
            _uiState.update { it.copy(isExporting = false, error = "导出失败: ${e.message}") }
            null
        }
    }

    /**
     * 从 JSON 字符串导入数据。
     */
    fun import(jsonString: String, merge: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null, importResult = null) }
            val result = withContext(Dispatchers.IO) {
                backupManager.importFromJson(jsonString, merge = merge)
            }
            if (result.success) {
                // 恢复设置
                result.settings?.let { settings ->
                    withContext(Dispatchers.IO) {
                        settingsDataStore.restoreFrom(settings)
                    }
                }
                _uiState.update { it.copy(isImporting = false, importResult = result) }
            } else {
                _uiState.update { it.copy(isImporting = false, error = result.error) }
            }
        }
    }

    /** 清除状态（用于关闭 Snackbar 等）。 */
    fun clearMessages() {
        _uiState.update { it.copy(exportResult = null, error = null, importResult = null) }
    }
}

class BackupViewModelFactory(
    private val backupManager: BackupManager,
    private val settingsDataStore: SettingsDataStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
            return BackupViewModel(backupManager, settingsDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
