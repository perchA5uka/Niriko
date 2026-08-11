package com.otakup.niriko.plugin

import android.util.Log

private const val TAG = "PluginManager"

/**
 * 数据源插件管理器。
 *
 * 插件按注册顺序排列优先级。
 * DataSourceChain 按此顺序链式尝试。
 */
class PluginManager {

    private val _plugins = mutableListOf<DataSourcePlugin>()

    /** 所有已注册插件（按优先级排序）。注册越早优先级越高。 */
    val plugins: List<DataSourcePlugin> get() = _plugins.toList()

    /** 注册一个插件。首次注册的插件也成为默认主数据源。 */
    fun register(plugin: DataSourcePlugin) {
        if (_plugins.any { it.id == plugin.id }) {
            Log.w(TAG, "Plugin already registered: ${plugin.id}, skipping")
            return
        }
        _plugins.add(plugin)
        Log.i(TAG, "Plugin registered: ${plugin.id} (${plugin.name})")
    }

    /** 获取指定 ID 的插件。 */
    fun getById(id: String): DataSourcePlugin? = _plugins.find { it.id == id }

    /** 将指定 ID 的插件移动到优先级列表顶部。 */
    fun moveToTop(id: String): Boolean {
        val idx = _plugins.indexOfFirst { it.id == id }
        if (idx <= 0) return false
        val plugin = _plugins.removeAt(idx)
        _plugins.add(0, plugin)
        Log.i(TAG, "Moved plugin to top: $id")
        return true
    }

    /** 将指定 ID 的插件移动一个位置。 */
    fun moveUp(id: String): Boolean {
        val idx = _plugins.indexOfFirst { it.id == id }
        if (idx <= 0) return false
        _plugins[idx] = _plugins[idx - 1].also { _plugins[idx - 1] = _plugins[idx] }
        return true
    }

    /** 将指定 ID 的插件下移一个位置。 */
    fun moveDown(id: String): Boolean {
        val idx = _plugins.indexOfFirst { it.id == id }
        if (idx < 0 || idx >= _plugins.size - 1) return false
        _plugins[idx] = _plugins[idx + 1].also { _plugins[idx + 1] = _plugins[idx] }
        return true
    }

    /** 第一个插件（主数据源）。 */
    val primary: DataSourcePlugin? get() = _plugins.firstOrNull()
}
