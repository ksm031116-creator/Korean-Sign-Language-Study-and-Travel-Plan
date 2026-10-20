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
    fun `L-12 두 쓰기 사이에서 실패하면 트랜잭션이 되돌리고, 다시 열어도 마지막 확정 판정까지 남는다 (실제 파일 DB)`() = runBlocking {
        val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
        val (states, sessions, tx) = stores()
        val cards = (1..3).map { basic("b0$it", "단어$it") }
        var s = (StartSession(sessions, states, tx, SequentialIdGenerator("s"))(SessionSource.Basics, Mode.All, cards, false) as StartOutcome.Started).session
        s = (JudgeCard(sessions, states, tx, clock)(s.id, s.decisionSeq, Verdict.Know) as JudgeOutcome.Judged).session

        // 두 번째 판정: 카드 상태는 쓰고 세션을 쓰는 순간 죽는다.
        val dying = object : SessionStore by sessions {
            override suspend fun save(session: LearningSession) = throw IllegalStateException("프로세스 종료")
        }
        assertEquals(JudgeOutcome.SaveFailed, JudgeCard(dying, states, tx, clock)(s.id, s.decisionSeq, Verdict.DontKnow))

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
        // DB 빌더가 조립부로 옮겨 가도 잡도록 모든 모듈의 출시 소스를 본다.
        val offenders = listOf("../app", "../adapter", "../domain").flatMap { module ->
            File(module, "src").walkTopDown().filter { it.extension == "kt" && "/test/" !in it.invariantSeparatorsPath }
                .filter { "fallbackToDestructiveMigration" in it.readText() }.toList()
        }
        assertEquals("fallbackToDestructiveMigration 금지 (학습 기록이 사라진다)", emptyList<File>(), offenders)
    }

    @Test
    fun `알 수 없는 상태 값의 행은 건너뛰고 나머지는 읽는다`() = runBlocking {
        db.cardStateDao().upsert(CardStateEntity("b01", "known", 0, 1))
        db.cardStateDao().upsert(CardStateEntity("b02", "mastered", 0, 1))
        val problems = mutableListOf<String>()
        val states = RoomLearningStateStore(db.cardStateDao()) { what, _ -> problems += what }
        assertEquals(mapOf(CardId("b01") to CardStatus.Known), states.all())
        assertEquals(1, problems.size)
    }

    @Test
    fun `읽지 못하는 세션은 지우고 없는 것으로 본다 (시작·판정이 영원히 막히지 않게)`() = runBlocking {
        db.sessionDao().put(SessionEntity(id = "s1", format = 1, sourceJson = "{broken", mode = "all", cardIdsJson = "[]",
            queueJson = "[]", decisionsJson = "[]", undoJson = null, statusesJson = "{}", decisionSeq = 0, face = "front"))
        db.cardStateDao().upsert(CardStateEntity("b01", "learning", 0, 1))
        val problems = mutableListOf<String>()
        val sessions = RoomSessionStore(db.sessionDao()) { what, _ -> problems += what }
        assertEquals(null, sessions.current())
        assertEquals(null, db.sessionDao().current())
        assertEquals(1, problems.size)
        assertEquals("학습 상태는 그대로", 1, db.cardStateDao().all().size)
        // 버린 뒤에는 새 세션을 시작할 수 있다.
        val (states, _, tx) = stores()
        val started = StartSession(sessions, states, tx, SequentialIdGenerator("n"))(SessionSource.Basics, Mode.All, listOf(basic("b01", "단어")), false)
        assertTrue(started is StartOutcome.Started)
    }

    @Test
    fun `모르는 형식 버전의 세션도 버린다`() = runBlocking {
        val (_, sessions, _) = stores()
        sessions.save(LearningStoreContract.sampleSession())
        val row = db.sessionDao().current()!!
        db.sessionDao().put(row.copy(format = 99))
        assertEquals(null, sessions.current())
    }

    @Test
    fun `세션 표에는 행이 하나뿐이다`() = runBlocking {
        val (_, sessions, _) = stores()
        sessions.save(LearningStoreContract.sampleSession("a"))
        sessions.save(LearningStoreContract.sampleSession("b"))
        val count = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM session").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count)
    }
}
