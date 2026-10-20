package app.signkorean.adapter.db

import androidx.room.withTransaction
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

class RoomLearningStateStore(private val dao: CardStateDao) : LearningStateStore {

    override suspend fun all(): Map<CardId, CardStatus> =
        dao.all().associate { CardId(it.cardId) to CardStatus.valueOf(it.state.toEnumName()) }

    override suspend fun save(state: CardState) {
        val previous = dao.get(state.card.value)
        if (state.status == CardStatus.Unseen) {
            dao.delete(state.card.value)
            return
        }
        dao.upsert(
            CardStateEntity(
                cardId = state.card.value,
                state = state.status.name.lowercase(),
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
                state = restore.status.name.lowercase(),
                lastJudgedAt = previous?.lastJudgedAt ?: 0L,
                judgementCount = ((previous?.judgementCount ?: 1) - 1).coerceAtLeast(0),
            ),
        )
    }

    override suspend fun clear() = dao.clear()

    private fun String.toEnumName() = replaceFirstChar { it.uppercase() }
}

class RoomSessionStore(private val dao: SessionDao) : SessionStore {

    override suspend fun current(): LearningSession? = dao.current()?.let(SessionCodec::decode)

    override suspend fun save(session: LearningSession) = dao.replace(SessionCodec.encode(session))

    override suspend fun clear() = dao.clear()
}

/** 판정의 카드 상태·세션 저장을 한 SQLite 트랜잭션으로 묶는다. */
class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

/** 세션 ↔ 행. JSON 형식은 저장소 안쪽의 약속이라 도메인 모델에 직렬화 표시를 붙이지 않는다. */
internal object SessionCodec {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class SourceDto(val type: String, val id: String? = null, val day: Int? = null)

    @Serializable private data class QueueDto(val card: String, val readyAt: Int)

    @Serializable private data class DecisionDto(val card: String, val verdict: String)

    @Serializable
    private data class UndoDto(
        val card: String,
        val previousStatus: String,
        val queue: List<QueueDto>,
        val decisions: List<DecisionDto>,
        val face: String,
    )

    fun encode(s: LearningSession) = SessionEntity(
        id = s.id.value,
        sourceJson = json.encodeToString(s.source.toDto()),
        mode = s.mode.name,
        queueJson = json.encodeToString(s.queue.map { it.toDto() }),
        decisionsJson = json.encodeToString(s.decisions.map { it.toDto() }),
        undoJson = s.undo?.let { u ->
            json.encodeToString(UndoDto(u.card.value, u.previousStatus.name, u.queue.map { it.toDto() }, u.decisions.map { it.toDto() }, u.face.name))
        },
        statusesJson = json.encodeToString(s.statuses.entries.associate { it.key.value to it.value.name }),
        decisionSeq = s.decisionSeq,
        face = s.face.name,
    )

    fun decode(e: SessionEntity) = LearningSession(
        id = SessionId(e.id),
        source = json.decodeFromString<SourceDto>(e.sourceJson).toSource(),
        mode = Mode.valueOf(e.mode),
        queue = json.decodeFromString<List<QueueDto>>(e.queueJson).map { it.toEntry() },
        decisions = json.decodeFromString<List<DecisionDto>>(e.decisionsJson).map { it.toDecision() },
        decisionSeq = e.decisionSeq,
        face = Face.valueOf(e.face),
        undo = e.undoJson?.let { raw ->
            val u = json.decodeFromString<UndoDto>(raw)
            UndoSnapshot(CardId(u.card), CardStatus.valueOf(u.previousStatus), u.queue.map { it.toEntry() }, u.decisions.map { it.toDecision() }, Face.valueOf(u.face))
        },
        statuses = json.decodeFromString<Map<String, String>>(e.statusesJson).entries.associate { CardId(it.key) to CardStatus.valueOf(it.value) },
    )

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
        else -> error("알 수 없는 세션 출처: $type")
    }

    private fun QueueEntry.toDto() = QueueDto(card.value, readyAt)
    private fun QueueDto.toEntry() = QueueEntry(CardId(card), readyAt)
    private fun Decision.toDto() = DecisionDto(card.value, verdict.name)
    private fun DecisionDto.toDecision() = Decision(CardId(card), Verdict.valueOf(verdict))
}

/**
 * 앱 조립부가 Room 타입을 몰라도 되도록 포트만 내보낸다. 데이터베이스는 앱 하나에 하나다.
 */
class RoomLearningStorage private constructor(db: AppDatabase) {
    val states: LearningStateStore = RoomLearningStateStore(db.cardStateDao())
    val sessions: SessionStore = RoomSessionStore(db.sessionDao())
    val transactions: TransactionRunner = RoomTransactionRunner(db)

    companion object {
        fun open(context: android.content.Context) = RoomLearningStorage(AppDatabase.build(context))
    }
}
