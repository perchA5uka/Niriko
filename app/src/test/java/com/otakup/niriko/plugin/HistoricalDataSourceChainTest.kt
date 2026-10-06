package com.otakup.niriko.plugin

import android.app.Application
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HistoricalDataSourceChainTest {
    private fun plugin(id: String, capable: Boolean = true, query: () -> List<SubjectEntity>): DataSourcePlugin = object : DataSourcePlugin {
        override val id = id
        override val name = id
        override val description = id
        override val capabilities = DataSourceCapabilities(supportsSubjectsByMonth = capable)
        override val dataSource = proxy<SubjectRemoteDataSource> { method ->
            if (method == "getSubjectsInDateRange" || method == "getSubjectsByMonth") query()
            else error("Unexpected source call: " + method)
        }
    }
    private fun chain(vararg plugins: DataSourcePlugin): DataSourceChain = DataSourceChain(plugins.toList(),
        proxy<SubjectDao> { error("Unexpected DAO call: " + it) })

    @Test fun unsupportedEmptySourceCannotMaskNetworkFailure() = runBlocking {
        var unsupportedCalls = 0
        val failure = IllegalStateException("historical network failed")
        val chain = chain(plugin("primary") { throw failure }, plugin("unsupported", false) { unsupportedCalls++; emptyList() })
        try { chain.getSubjectsInDateRange(2, "2023-01-01", "2023-02-01"); fail("Failure became empty") }
        catch (actual: IllegalStateException) { assertSame(failure, actual) }
        assertEquals(0, unsupportedCalls)
    }
    @Test fun completedEmptyMonthIsLegitimateSuccess() = runBlocking {
        assertTrue(chain(plugin("primary") { emptyList() }).getSubjectsInDateRange(2, "2023-01-01", "2023-02-01").isEmpty())
    }
    @Test fun cancellationIsNotTreatedAsFallbackEmpty() = runBlocking {
        var fallbackCalls = 0
        val cancelled = CancellationException("user cancelled")
        val chain = chain(plugin("primary") { throw cancelled }, plugin("fallback") { fallbackCalls++; emptyList() })
        try { chain.getSubjectsInDateRange(2, "2023-01-01", "2023-02-01"); fail("Cancellation swallowed") }
        catch (actual: CancellationException) { assertSame(cancelled, actual) }
        assertEquals(0, fallbackCalls)
    }
    @Test fun monthQueryPreservesFailureContractToo() = runBlocking {
        val failure = IllegalStateException("month failed")
        try { chain(plugin("primary") { throw failure }).getSubjectsByMonth(2, 2023, 1); fail("Failure became empty") }
        catch (actual: IllegalStateException) { assertSame(failure, actual) }
    }
    @Test fun noCapableSourceIsNotSuccessfulEmptyMonth() = runBlocking {
        try { chain(plugin("unsupported", false) { error("Must not query") }).getSubjectsInDateRange(2, "2023-01-01", "2023-02-01"); fail("No source became empty") }
        catch (_: UnsupportedOperationException) {}
    }
    private inline fun <reified T> proxy(crossinline call: (String) -> Any?): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, _ -> call(method.name) } as T
}
