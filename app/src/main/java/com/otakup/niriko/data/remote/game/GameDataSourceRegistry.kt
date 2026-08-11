package com.otakup.niriko.data.remote.game

/**
 * 游戏数据源注册表。
 *
 * 注册序即优先级（首个注册的为默认源）；按 [GameDataSource.id] 精确路由。
 * 与现有 DataSourcePlugin/PluginManager 解耦：游戏数据源是"补充查询通道"，
 * 主语义源仍由 Bangumi 数据源链负责。
 */
class GameDataSourceRegistry {
    private val _sources = mutableListOf<GameDataSource>()

    /** 已注册数据源（按注册序）。 */
    val sources: List<GameDataSource> get() = _sources.toList()

    /** 注册数据源（重复 id 忽略）。 */
    fun register(source: GameDataSource) {
        if (_sources.none { it.id == source.id }) {
            _sources.add(source)
        }
    }

    /** 按 id 查找数据源；未注册返回 null。 */
    fun get(id: String): GameDataSource? = _sources.firstOrNull { it.id == id }

    /** 支持搜索的数据源列表（按注册序）。 */
    fun searchable(): List<GameDataSource> = _sources.filter { it.capabilities.supportsSearch }

    /** 支持详情的源（按注册序，第一个为默认详情源）。 */
    fun detailSources(): List<GameDataSource> = _sources.filter { it.capabilities.supportsDetail }
}
