package com.otakup.niriko.plugin

import com.otakup.niriko.data.remote.SubjectRemoteDataSource

/**
 * 数据源插件核心接口。
 *
 * 每个数据源（Bangumi、AniList 等）实现此接口注册到 PluginManager。
 * capabilities 声明插件的能力，DataSourceChain 据此调度。
 */
interface DataSourcePlugin {

    /** 唯一标识符，用于持久化和路由。如 "bangumi"、"anilist"。 */
    val id: String

    /** 对人类可读的名称，如 "Bangumi 番组计划"。 */
    val name: String

    /** 简短描述。 */
    val description: String

    /** 能力声明。 */
    val capabilities: DataSourceCapabilities

    /** 数据源实现。 */
    val dataSource: SubjectRemoteDataSource
}
