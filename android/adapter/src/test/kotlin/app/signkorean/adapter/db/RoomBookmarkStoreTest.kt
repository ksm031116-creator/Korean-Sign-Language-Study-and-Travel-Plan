package app.signkorean.adapter.db

import kotlinx.coroutines.async
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

    @Test
    fun `B-06 실제 DB 트랜잭션에서 동시에 저장해도 무료 한도를 넘지 않고, 다시 열어도 중복 없이 남는다`() = runBlocking {
        val name = "b06-${System.nanoTime()}.db"
        // 단언이 중간에 실패해도 DB 파일을 지운다.
        try {
            b06(name)
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend fun b06(name: String) {
        val file = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val t = Instant.parse("2026-10-21T09:00:00Z")
        val clock = app.signkorean.domain.common.Clock { t }
        val set = app.signkorean.domain.bookmark.SetBookmark(
            RoomBookmarkStore(file.bookmarkDao()), RoomTransactionRunner(file), { app.signkorean.domain.entitlement.Entitlement.Free }, clock,
        )
        (1..3).forEach { set(CardId("old$it"), saved = true) }
        // 남은 자리는 2개. 8개를 동시에 저장하면 2개만 들어가야 한다.
        val outcomes = kotlinx.coroutines.coroutineScope {
            (1..8).map { i -> async(kotlinx.coroutines.Dispatchers.IO) { set(CardId("new$i"), saved = true) } }.map { it.await() }
        }
        assertEquals(2, outcomes.count { it == app.signkorean.domain.bookmark.BookmarkOutcome.Saved })
        // 같은 카드를 여러 번 저장해도 한 줄(프로세스가 저장 도중 끝나 다시 저장하는 경우와 같다).
        set(CardId("old1"), saved = true)
        assertEquals(5, RoomBookmarkStore(file.bookmarkDao()).count())
        val before = RoomBookmarkStore(file.bookmarkDao()).all().map { it.card }
        file.close()
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            val after = RoomBookmarkStore(reopened.bookmarkDao()).all().map { it.card }
            assertEquals(before, after)
            assertEquals(after.size, after.toSet().size)
        } finally {
            reopened.close()
        }
    }
}
