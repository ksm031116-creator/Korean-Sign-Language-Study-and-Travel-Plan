package app.signkorean.adapter.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import app.signkorean.domain.bookmark.Bookmark
import app.signkorean.domain.content.CardId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RoomBookmarkStoreTest {

    private val context = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val store = RoomBookmarkStore(db.bookmarkDao())

    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @After
    fun close() = db.close()

    @Test
    fun `저장·해제·최신순·중복 없음·초기화`() = runBlocking {
        val t = Instant.parse("2026-10-21T09:00:00Z")
        store.add(Bookmark(CardId("b01"), t))
        store.add(Bookmark(CardId("b02"), t.plusSeconds(5)))
        store.add(Bookmark(CardId("b01"), t.plusSeconds(10)))
        assertEquals(listOf("b02", "b01"), store.all().map { it.card.value })
        assertEquals("처음 저장한 시각을 지킨다", t, store.all().last().createdAt)
        assertEquals(2, store.count())
        assertTrue(store.contains(CardId("b01")))
        store.remove(CardId("b01"))
        assertEquals(1, store.count())
        store.clear()
        assertEquals(0, store.count())
    }

    @Test
    fun `v1 에서 v2 로 올리면 학습 기록이 남고 북마크 표가 생긴다`() {
        migrations.createDatabase("migrate.db", 1).use { v1 ->
            v1.execSQL("INSERT INTO card_state (card_id, state, last_judged_at, judgement_count) VALUES ('b01', 'known', 1, 1)")
        }
        migrations.runMigrationsAndValidate("migrate.db", 2, true).use { v2 ->
            v2.query("SELECT state FROM card_state WHERE card_id = 'b01'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("known", c.getString(0))
            }
            v2.query("SELECT COUNT(*) FROM bookmark").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }
}
