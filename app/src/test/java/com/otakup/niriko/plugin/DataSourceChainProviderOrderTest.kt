package com.otakup.niriko.plugin

import android.app.Application
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.remote.PersonSubjectInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D7：数据源链必须**按需读取**插件顺序，而不是构造时的快照。
 *
 * 背景：插件顺序在运行期是可变的（注册先后、可能的优先级调整），而改造前
 * DataSourceChain 把 `pluginManager.plugins` 的一次 toList() 结果存成 val ——
 * 之后无论顺序怎么变，链都按老顺序调度，只有重建对象（重启进程）才生效。
 *
 * 这里用 getCharacterSubjects（首个非空即返回、且不落库）作为观察口：
 * provider 每次换序，链的「谁先响应」必须跟着换。
 *
 * 注意：不涉及持久化（源顺序偏好已随 X2 删除），所以不存在「重启后仍在」这类断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DataSourceChainProviderOrderTest {

    private fun plugin(id: String, subjectId: Long): DataSourcePlugin = object : DataSourcePlugin {
        override val id = id
        override val name = id
        override val description = id
        override val capabilities = DataSourceCapabilities()
        override val dataSource = proxy<SubjectRemoteDataSource> { method ->
            when (method) {
                "getCharacterSubjects" -> listOf(
                    PersonSubjectInfo(
                        subjectId = subjectId,
                        title = id,
                        titleCN = null,
                        type = 0,
                        staff = null,
                        imageUrl = null,
                    ),
                )
                else -> error("Unexpected source call: " + method)
            }
        }
    }

    private fun chainWith(order: () -> List<DataSourcePlugin>): DataSourceChain = DataSourceChain(
        pluginProvider = order,
        subjectDao = proxy { error("Unexpected DAO call: " + it) },
    )

    private fun answeredBy(chain: DataSourceChain): String =
        runBlocking { chain.getCharacterSubjects(1L).single().title }

    @Test
    fun providerIsReadOnEveryCallSoRuntimeOrderChangesAreVisible() {
        val alpha = plugin("alpha", 1L)
        val beta = plugin("beta", 2L)
        var order = listOf(alpha, beta)
        val chain = chainWith { order }

        assertEquals("alpha", answeredBy(chain))

        // 运行期换序：链必须立刻跟上，无需重建对象
        order = listOf(beta, alpha)
        assertEquals("beta", answeredBy(chain))

        // 再翻回来 —— 证明不是「只在第一次读时就固定」
        order = listOf(alpha, beta)
        assertEquals("alpha", answeredBy(chain))
    }

    @Test
    fun listOverloadKeepsSnapshotSemanticsForExistingCallers() {
        val alpha = plugin("alpha", 1L)
        val beta = plugin("beta", 2L)
        var order = listOf(alpha)
        val chain = DataSourceChain(
            order,
            proxy { error("Unexpected DAO call: " + it) },
        )

        assertEquals("alpha", answeredBy(chain))

        // 旧构造是快照语义：外部列表变了，链不变（与改造前一致，测试依赖它）
        order = listOf(beta, alpha)
        assertEquals("alpha", answeredBy(chain))
    }

    private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, _ -> call(method.name) } as T
}
