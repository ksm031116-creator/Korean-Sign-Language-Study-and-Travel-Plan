package app.signkorean.adapter.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreOnboardingStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun open(file: File, scope: CoroutineScope) =
        DataStoreOnboardingStore(PreferenceDataStoreFactory.create(scope = scope) { file })

    @Test
    fun `처음에는 아무것도 보지 않았고, 기록은 다시 열어도 남는다`() = runBlocking {
        val file = File(tmp.root, "onboarding.preferences_pb")
        val scope1 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = open(file, scope1)
        assertFalse(store.firstRunDone())
        assertFalse(store.coachSeen())
        store.markFirstRunDone()
        store.markCoachSeen()
        scope1.coroutineContext[Job]!!.cancelAndJoin() // 같은 파일의 DataStore 는 앞의 것이 완전히 끝나야 다시 연다

        val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val reopened = open(file, scope2)
        assertTrue(reopened.firstRunDone())
        assertTrue(reopened.coachSeen())
        reopened.resetCoach()
        assertFalse(reopened.coachSeen())
        assertTrue("첫 실행 기록은 시연 다시 보기와 따로다", reopened.firstRunDone())
        scope2.coroutineContext[Job]!!.cancelAndJoin()
    }
}
