package app.signkorean.adapter.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

/** 카드별 학습 상태 (설계서 §7.2). state 는 unseen 을 저장하지 않는다(행이 없으면 unseen). */
@Entity(tableName = "card_state")
data class CardStateEntity(
    @PrimaryKey @ColumnInfo(name = "card_id") val cardId: String,
    /** learning / known. 값의 검사는 RoomLearningStateStore 가 enum 으로만 쓰고 읽어서 한다. */
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "last_judged_at") val lastJudgedAt: Long,
    @ColumnInfo(name = "judgement_count") val judgementCount: Int,
)

/** 진행 중 세션 (설계서 §7.2, PRD §10.2). 큐·판정·Undo 기록은 JSON 열로 둔다. */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "source_json") val sourceJson: String,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "queue_json") val queueJson: String,
    @ColumnInfo(name = "decisions_json") val decisionsJson: String,
    @ColumnInfo(name = "undo_json") val undoJson: String?,
    @ColumnInfo(name = "statuses_json") val statusesJson: String,
    @ColumnInfo(name = "decision_seq") val decisionSeq: Int,
    @ColumnInfo(name = "face") val face: String,
)

@Dao
interface CardStateDao {
    @Query("SELECT * FROM card_state")
    suspend fun all(): List<CardStateEntity>

    @Query("SELECT * FROM card_state WHERE card_id = :cardId")
    suspend fun get(cardId: String): CardStateEntity?

    @Upsert
    suspend fun upsert(entity: CardStateEntity)

    @Query("DELETE FROM card_state WHERE card_id = :cardId")
    suspend fun delete(cardId: String)

    @Query("DELETE FROM card_state")
    suspend fun clear()
}

@Dao
interface SessionDao {
    /** 세션은 한 번에 하나만 둔다. */
    @Query("SELECT * FROM session LIMIT 1")
    suspend fun current(): SessionEntity?

    @Query("DELETE FROM session")
    suspend fun clear()

    @Upsert
    suspend fun upsert(entity: SessionEntity)

    @Transaction
    suspend fun replace(entity: SessionEntity) {
        clear()
        upsert(entity)
    }
}

/**
 * 앱 데이터베이스. 스키마는 schemas/ 에 내보내고, 바꿀 때는 마이그레이션을 쓴다.
 * 파괴적 마이그레이션은 쓰지 않는다: 학습 기록이 사라진다 (설계서 §7.2).
 */
@Database(entities = [CardStateEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardStateDao(): CardStateDao
    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "sign-korean.db"

        fun build(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
