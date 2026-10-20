package app.signkorean.adapter.db

import androidx.room.withTransaction
import app.signkorean.domain.bookmark.Bookmark
import app.signkorean.domain.bookmark.BookmarkStore
import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.CardId
import app.signkorean.domain.learning.CardState
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.CardStatusRestore
import app.signkorean.domain.learning.Decision
import app.signkorean.domain.learning.Face
import app.signkorean.domain.learning.LearningSession
import app.signkorean.domain.learning.LearningStateStore
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.QueueEntry
import app.signkorean.domain.learning.SessionId
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.SessionStore
import app.signkorean.domain.learning.TransactionRunner
import app.signkorean.domain.learning.UndoSnapshot
import app.signkorean.domain.learning.Verdict
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/** 저장된 값을 읽지 못했다. 앱은 그 값을 버리고 계속 동작한다. 원인은 기록만 한다. */
fun interface StorageProblemReporter {
    fun report(what: String, cause: Throwable?)
}

class RoomLearningStateStore(
    private val dao: CardStateDao,
    private val reporter: StorageProblemReporter = StorageProblemReporter { _, _ -> },
) : LearningStateStore {

    /** 모르는 상태 값(앞으로 생길 값, 손상)은 건너뛴다. 한 행 때문에 학습 전체가 막히면 안 된다. */
    override suspend fun all(): Map<CardId, CardStatus> = dao.all().mapNotNull { row ->
        val status = WireStatus.decode(row.state)
        if (status == null) reporter.report("card_state ${row.cardId}: 알 수 없는 상태 '${row.state}'", null)
        status?.let { CardId(row.cardId) to it }
    }.toMap()

    override suspend fun save(state: CardState) {
        val previous = dao.get(state.card.value)
        if (state.status == CardStatus.Unseen) {
            dao.delete(state.card.value)
            return
        }
        dao.upsert(
            CardStateEntity(
                cardId = state.card.value,
                state = WireStatus.encode(state.status),
                lastJudgedAt = state.updatedAt.toEpochMilli(),
                judgementCount = (previous?.judgementCount ?: 0) + 1,
            ),
        )
    }

    override suspend fun restore(restore: CardStatusRestore) {
        if (restore.status == CardStatus.Unseen) {
            dao.delete(restore.card.value)
            return
        }
        val previous = dao.get(restore.card.value)
        // Undo 는 판정을 취소하는 것이므로 판정 횟수를 하나 되돌린다. 시각은 남아 있는 값을 둔다.
        dao.upsert(
            CardStateEntity(
                cardId = restore.card.value,
                state = WireStatus.encode(restore.status),
                lastJudgedAt = previous?.lastJudgedAt ?: 0L,
                judgementCount = ((previous?.judgementCount ?: 1) - 1).coerceAtLeast(0),
            ),
        )
    }

    override suspend fun clear() = dao.clear()
}

class RoomSessionStore(
    private val dao: SessionDao,
    private val reporter: StorageProblemReporter = StorageProblemReporter { _, _ -> },
) : SessionStore {

    /**
     * 읽지 못하는 세션(앱 내림·손상·모르는 형식)은 지우고 없는 것으로 본다. 그대로 두면 시작·판정이 영원히 실패한다.
     * 카드별 학습 상태는 별도 표라 그대로 남는다.
     */
    override suspend fun current(): LearningSession? {
        val row = dao.current() ?: return null
        return try {
            SessionCodec.decode(row)
        } catch (e: Exception) {
            reporter.report("session ${row.id}: 읽을 수 없어 지운다", e)
            dao.clear()
            null
        }
    }

    override suspend fun save(session: LearningSession) = dao.put(SessionCodec.encode(session))

    override suspend fun clear() = dao.clear()
}

/**
 * 저장 형식의 값. 도메인 enum 이름과 분리해 두어 enum 이름을 바꿔도 저장된 기록이 깨지지 않는다.
 */
internal object WireStatus {
    fun encode(status: CardStatus) = when (status) {
        CardStatus.Unseen -> "unseen"
        CardStatus.Learning -> "learning"
        CardStatus.Known -> "known"
    }

    fun decode(value: String): CardStatus? = when (value) {
        "unseen" -> CardStatus.Unseen
        "learning" -> CardStatus.Learning
        "known" -> CardStatus.Known
        else -> null
    }
}

/** 판정의 카드 상태·세션 저장을 한 SQLite 트랜잭션으로 묶는다. */
class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

/** 세션 ↔ 행. JSON 형식은 저장소 안쪽의 약속이라 도메인 모델에 직렬화 표시를 붙이지 않는다. */
internal object SessionCodec {

    const val FORMAT = 1

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class SourceDto(val type: String, val id: String? = null, val day: Int? = null)

    @Serializable private data class QueueDto(val card: String, val readyAt: Int)

    @Serializable private data class DecisionDto(val card: String, val verdict: String)

    @Serializable
    private data class UndoDto(val card: String, val previousStatus: String, val queue: List<QueueDto>, val decisions: List<DecisionDto>)

    fun encode(s: LearningSession) = SessionEntity(
        id = s.id.value,
        format = FORMAT,
        sourceJson = json.encodeToString(s.source.toDto()),
        mode = MODES.getValue(s.mode),
        cardIdsJson = json.encodeToString(s.cardIds.map { it.value }),
        queueJson = json.encodeToString(s.queue.map { it.toDto() }),
        decisionsJson = json.encodeToString(s.decisions.map { it.toDto() }),
        undoJson = s.undo?.let { u ->
            json.encodeToString(UndoDto(u.card.value, WireStatus.encode(u.previousStatus), u.queue.map { it.toDto() }, u.decisions.map { it.toDto() }))
        },
        statusesJson = json.encodeToString(s.statuses.entries.associate { it.key.value to WireStatus.encode(it.value) }),
        decisionSeq = s.decisionSeq,
        face = FACES.getValue(s.face),
    )

    /** 읽지 못하면 예외를 던진다. 부르는 쪽(RoomSessionStore)이 그 행을 버린다. */
    fun decode(e: SessionEntity): LearningSession {
        require(e.format == FORMAT) { "모르는 세션 형식 ${e.format}" }
        return LearningSession(
            id = SessionId(e.id),
            source = json.decodeFromString<SourceDto>(e.sourceJson).toSource(),
            mode = MODES.decode(e.mode),
            cardIds = json.decodeFromString<List<String>>(e.cardIdsJson).map(::CardId),
            queue = json.decodeFromString<List<QueueDto>>(e.queueJson).map { it.toEntry() },
            decisions = json.decodeFromString<List<DecisionDto>>(e.decisionsJson).map { it.toDecision() },
            decisionSeq = e.decisionSeq,
            face = FACES.decode(e.face),
            undo = e.undoJson?.let { raw ->
                val u = json.decodeFromString<UndoDto>(raw)
                UndoSnapshot(CardId(u.card), status(u.previousStatus), u.queue.map { it.toEntry() }, u.decisions.map { it.toDecision() })
            },
            statuses = json.decodeFromString<Map<String, String>>(e.statusesJson).entries.associate { CardId(it.key) to status(it.value) },
        )
    }

    private fun status(v: String) = requireNotNull(WireStatus.decode(v)) { "모르는 상태 '$v'" }

    private val MODES = mapOf(Mode.All to "all", Mode.Review to "review")
    private val FACES = mapOf(Face.Front to "front", Face.Back to "back")
    private val VERDICTS = mapOf(Verdict.Know to "know", Verdict.DontKnow to "dont_know")

    private fun <T> Map<T, String>.decode(v: String): T =
        entries.firstOrNull { it.value == v }?.key ?: throw IllegalArgumentException("모르는 값 '$v'")

    private fun SessionSource.toDto() = when (this) {
        SessionSource.Basics -> SourceDto("basics")
        SessionSource.Saved -> SourceDto("saved")
        is SessionSource.Area -> SourceDto("area", area.value)
        is SessionSource.Place -> SourceDto("place", place)
        is SessionSource.TripDay -> SourceDto("trip_day", trip, day)
    }

    private fun SourceDto.toSource(): SessionSource = when (type) {
        "basics" -> SessionSource.Basics
        "saved" -> SessionSource.Saved
        "area" -> SessionSource.Area(AreaId(requireNotNull(id)))
        "place" -> SessionSource.Place(requireNotNull(id))
        "trip_day" -> SessionSource.TripDay(requireNotNull(id), requireNotNull(day))
        else -> throw IllegalArgumentException("알 수 없는 세션 출처: $type")
    }

    private fun QueueEntry.toDto() = QueueDto(card.value, readyAt)
    private fun QueueDto.toEntry() = QueueEntry(CardId(card), readyAt)
    private fun Decision.toDto() = DecisionDto(card.value, VERDICTS.getValue(verdict))
    private fun DecisionDto.toDecision() = Decision(CardId(card), VERDICTS.decode(verdict))
}

class RoomBookmarkStore(private val dao: BookmarkDao) : BookmarkStore {
    override suspend fun all(): List<Bookmark> = dao.all().map { Bookmark(CardId(it.cardId), Instant.ofEpochMilli(it.createdAt)) }
    override suspend fun count(): Int = dao.count()
    override suspend fun contains(card: CardId): Boolean = dao.contains(card.value)
    override suspend fun add(bookmark: Bookmark) = dao.add(BookmarkEntity(bookmark.card.value, bookmark.createdAt.toEpochMilli()))
    override suspend fun remove(card: CardId) = dao.remove(card.value)
    override suspend fun clear() = dao.clear()
}

/**
 * 앱 조립부가 Room 타입을 몰라도 되도록 포트만 내보낸다. 데이터베이스는 앱 하나에 하나다.
 */
class RoomLearningStorage private constructor(db: AppDatabase, reporter: StorageProblemReporter) {
    val states: LearningStateStore = RoomLearningStateStore(db.cardStateDao(), reporter)
    val sessions: SessionStore = RoomSessionStore(db.sessionDao(), reporter)
    val transactions: TransactionRunner = RoomTransactionRunner(db)
    val bookmarks: BookmarkStore = RoomBookmarkStore(db.bookmarkDao())

    companion object {
        fun open(context: android.content.Context, reporter: StorageProblemReporter = LogcatStorageProblemReporter) =
            RoomLearningStorage(AppDatabase.build(context), reporter)
    }
}

object LogcatStorageProblemReporter : StorageProblemReporter {
    override fun report(what: String, cause: Throwable?) {
        android.util.Log.w("SignKoreanStorage", what, cause)
    }
}
