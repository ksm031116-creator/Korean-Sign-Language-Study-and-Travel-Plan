package app.signkorean.adapter.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.signkorean.domain.entitlement.Entitlement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreEntitlementCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file by lazy { File(tmp.root, "entitlement.preferences_pb") }

    private suspend fun <T> withStore(block: suspend (DataStoreEntitlementCache) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        return try {
            block(DataStoreEntitlementCache(PreferenceDataStoreFactory.create(scope = scope) { file }))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin() // 같은 파일은 앞의 DataStore 가 끝나야 다시 연다
        }
    }

    @Test
    fun `처음에는 Free, 저장한 권한은 다시 열어도 남는다`() = runBlocking {
        withStore { assertEquals(Entitlement.Free, it.load()); it.save(Entitlement.Paid) }
        withStore { assertEquals(Entitlement.Paid, it.load()); it.save(Entitlement.Free) }
        withStore { assertEquals(Entitlement.Free, it.load()) }
    }

    @Test
    fun `알 수 없는 값은 Free 로 읽는다`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        PreferenceDataStoreFactory.create(scope = scope) { file }.edit { it[stringPreferencesKey("status")] = "gold" }
        scope.coroutineContext[Job]!!.cancelAndJoin()
        withStore { assertEquals(Entitlement.Free, it.load()) }
    }
}
