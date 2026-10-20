package app.signkorean.adapter.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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

/**
 * 진행 중 세션 (설계서 §7.2, PRD §10.2). 큐·판정·Undo 기록은 JSON 열로 둔다.
 * 세션은 한 번에 하나라서 기본 키는 고정 칸([slot] = 0)이다. 행이 둘 생길 수 없다.
 */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey @ColumnInfo(name = "slot") val slot: Int = 0,
    @ColumnInfo(name = "id") val id: String,
    /** JSON 열들의 형식 버전. 형식을 바꾸면 올리고 SessionCodec 이 옛 형식을 읽는다. */
    @ColumnInfo(name = "format") val format: Int,
    @ColumnInfo(name = "source_json") val sourceJson: String,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "card_ids_json") val cardIdsJson: String,
    @ColumnInfo(name = "queue_json") val queueJson: String,
    @ColumnInfo(name = "decisions_json") val decisionsJson: String,
    @ColumnInfo(name = "undo_json") val undoJson: String?,
    @ColumnInfo(name = "statuses_json") val statusesJson: String,
    @ColumnInfo(name = "decision_seq") val decisionSeq: Int,
    @ColumnInfo(name = "face") val face: String,
)

/** 카드 북마크 (설계서 §7.2). 카드 하나에 한 줄. */
@Entity(tableName = "bookmark")
data class BookmarkEntity(
    @PrimaryKey @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmark ORDER BY created_at DESC, card_id")
    suspend fun all(): List<BookmarkEntity>

    @Query("SELECT COUNT(*) FROM bookmark")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM bookmark WHERE card_id = :cardId)")
    suspend fun contains(cardId: String): Boolean

    /** 이미 있으면 그대로 둔다(처음 저장한 시각을 지킨다). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entity: BookmarkEntity)

    @Query("DELETE FROM bookmark WHERE card_id = :cardId")
    suspend fun remove(cardId: String)

    @Query("DELETE FROM bookmark")
    suspend fun clear()
}

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
    /** 세션은 고정 칸 하나에만 있다. */
    @Query("SELECT * FROM session WHERE slot = 0")
    suspend fun current(): SessionEntity?

    @Query("DELETE FROM session")
    suspend fun clear()

    /** 고정 칸을 덮어쓴다. 다른 세션은 남지 않는다. */
    @Upsert
    suspend fun put(entity: SessionEntity)
}

/**
 * 앱 데이터베이스. 스키마는 schemas/ 에 내보내고, 바꿀 때는 마이그레이션을 쓴다.
 * 파괴적 마이그레이션은 쓰지 않는다: 학습 기록이 사라진다 (설계서 §7.2).
 */
@Database(
    entities = [CardStateEntity::class, SessionEntity::class, BookmarkEntity::class],
    version = 2,
    exportSchema = true,
    // v2: bookmark 표 추가(Phase 11). 표 추가는 자동 마이그레이션으로 충분하다. 기존 학습 기록은 그대로 남는다.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardStateDao(): CardStateDao
    abstract fun sessionDao(): SessionDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        const val NAME = "sign-korean.db"

        fun build(context: Context): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
