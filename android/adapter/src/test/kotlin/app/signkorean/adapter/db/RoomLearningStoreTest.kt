package app.signkorean.adapter.db

import androidx.room.Room
import app.signkorean.domain.content.CardId
import app.signkorean.domain.learning.CardState
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.JudgeCard
import app.signkorean.domain.learning.JudgeOutcome
import app.signkorean.domain.learning.LearningSession
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.ResumeSession
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.SessionStore
import app.signkorean.domain.learning.StartOutcome
import app.signkorean.domain.learning.StartSession
import app.signkorean.domain.learning.Verdict
import app.signkorean.testing.ContentFixtures.basic
import app.signkorean.testing.FixedClock
import app.signkorean.testing.LearningStoreContract
import app.signkorean.testing.SequentialIdGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RoomLearningStoreTest {

    private val context = RuntimeEnvironment.getApplication()
    private val dbFile: File = context.getDatabasePath("learning-test.db")
    private var db = open()

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
        .allowMainThreadQueries()
        .build()

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbFile.name)
    }

    private fun stores(database: AppDatabase = db) =
        Triple(RoomLearningStateStore(database.cardStateDao()), RoomSessionStore(database.sessionDao()), RoomTransactionRunner(database))

    @Test
    fun `Room 저장소는 포트 계약을 지킨다 (가짜와 같은 검사)`() = runBlocking {
        val (states, sessions, tx) = stores()
        LearningStoreContract(states, sessions, tx).verifyAll()
    }

    @Test
    fun `판정 횟수와 마지막 판정 시각을 남긴다`() = runBlocking {
        val (states, _, _) = stores()
        val t = Instant.parse("2026-10-21T09:00:00Z")
        states.save(CardState(CardId("b01"), CardStatus.Learning, t))
        states.save(CardState(CardId("b01"), CardStatus.Known, t.plusSeconds(60)))
        val row = db.cardStateDao().get("b01")!!
        assertEquals(2, row.judgementCount)
        assertEquals(t.plusSeconds(60).toEpochMilli(), row.lastJudgedAt)
    }

    @Test
    fun `L-12 판정 저장 중 죽어도 다시 열면 마지막 확정 판정까지 남는다 (실제 파일 DB)`() = runBlocking {
        val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
        val (states, sessions, tx) = stores()
        val cards = (1..3).map { basic("b0$it", "단어$it") }
        var s = (StartSession(sessions, states, tx, SequentialIdGenerator("s"))(SessionSource.Basics, Mode.All, cards, false) as StartOutcome.Started).session
        s = (JudgeCard(sessions, states, tx, clock)(s.decisionSeq, Verdict.Know) as JudgeOutcome.Judged).session

        // 두 번째 판정: 카드 상태는 쓰고 세션을 쓰는 순간 죽는다.
        val dying = object : SessionStore by sessions {
            override suspend fun save(session: LearningSession) = throw IllegalStateException("프로세스 종료")
        }
        assertEquals(JudgeOutcome.SaveFailed, JudgeCard(dying, states, tx, clock)(s.decisionSeq, Verdict.DontKnow))

        db.close()
        db = open() // 재시작
        val (states2, sessions2, _) = stores()
        assertEquals(s, ResumeSession(sessions2)())
        assertEquals(mapOf(CardId("b01") to CardStatus.Known), states2.all())
    }

    @Test
    fun `스키마는 파괴적 마이그레이션 없이 내보내진다`() {
        val schema = File("schemas/app.signkorean.adapter.db.AppDatabase/1.json")
        assertTrue("Room 스키마 파일이 없다: ${schema.absolutePath}", schema.isFile)
        val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }
        assertTrue("fallbackToDestructiveMigration 금지 (학습 기록이 사라진다)", sources.none { "fallbackToDestructiveMigration" in it })
    }
}
