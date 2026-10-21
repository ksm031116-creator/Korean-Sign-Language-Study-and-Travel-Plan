package app.signkorean.adapter.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.signkorean.domain.content.CardId
import app.signkorean.domain.upgrade.PendingAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStorePendingActionStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file by lazy { File(tmp.root, "pending_action.preferences_pb") }

    private suspend fun <T> withStore(block: suspend (DataStorePendingActionStore) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        return try {
            block(DataStorePendingActionStore(PreferenceDataStoreFactory.create(scope = scope) { file }))
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test
    fun `B-07 보류 동작은 다시 열어도(프로세스 재시작) 남고, 지우면 사라진다`() = runBlocking {
        val action = PendingAction.SaveBookmark(CardId("t-market-1"))
        withStore { assertNull(it.peek()); it.save(action) }
        withStore { assertEquals(action, it.peek()); it.clear() }
        withStore { assertNull(it.peek()) }
    }

    @Test
    fun `읽을 수 없는 값은 없는 것으로 본다`() {
        assertNull(DataStorePendingActionStore.decode("bookmark:"))
        assertNull(DataStorePendingActionStore.decode("trip:x"))
        assertEquals(PendingAction.SaveBookmark(CardId("b01")), DataStorePendingActionStore.decode("bookmark:b01"))
    }
}
