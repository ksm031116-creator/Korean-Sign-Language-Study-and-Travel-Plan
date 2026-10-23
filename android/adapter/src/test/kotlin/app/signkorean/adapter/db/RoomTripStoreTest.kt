package app.signkorean.adapter.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import app.signkorean.domain.bookmark.Bookmark
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.trip.StopId
import app.signkorean.domain.trip.Trip
import app.signkorean.domain.trip.TripDay
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripStop
import app.signkorean.testing.TripStoreContract
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
import java.time.LocalDate

/** 여행 저장 (설계서 §7.2, 구현 계획 Phase 16 D3). 포트 계약, FK·CASCADE·UNIQUE, T-17, v2 → v3 마이그레이션 */
@RunWith(RobolectricTestRunner::class)
class RoomTripStoreTest {

    private val context = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val store = RoomTripStore(db)

    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @After
    fun close() = db.close()

    @Test
    fun `포트 계약 - 가짜와 같은 결과`() = runBlocking {
        TripStoreContract(store).verifyAll()
    }

    @Test
    fun `여행을 지우면 그날들과 장소가 함께 지워진다(CASCADE)`() = runBlocking {
        val d = LocalDate.of(2026, 11, 3)
        store.save(Trip(TripId("t"), "A", d, d, listOf(TripDay(d, stops = listOf(TripStop(StopId("s"), PlaceId("p"))))), Instant.EPOCH))
        store.delete(TripId("t"))
        db.query("SELECT (SELECT COUNT(*) FROM trip_day) + (SELECT COUNT(*) FROM trip_stop)", null).use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun `같은 날 같은 장소 두 줄은 데이터베이스가 막는다(UNIQUE)`() {
        db.openHelper.writableDatabase.apply {
            execSQL("INSERT INTO trip (id, title, start_date, end_date, created_at) VALUES ('t','A','2026-11-03','2026-11-03',0)")
            execSQL("INSERT INTO trip_day (trip_id, date, start_minute, revision, needs_recalculation) VALUES ('t','2026-11-03',540,0,0)")
            execSQL("INSERT INTO trip_stop (id, trip_id, day_date, place_id, stay_minutes, position) VALUES ('s1','t','2026-11-03','p',30,0)")
            val rejected = runCatching {
                execSQL("INSERT INTO trip_stop (id, trip_id, day_date, place_id, stay_minutes, position) VALUES ('s2','t','2026-11-03','p',30,1)")
            }.isFailure
            assertTrue(rejected)
        }
    }

    @Test
    fun `T-17 여행 삭제는 북마크·학습 기록을 지우지 않는다`() = runBlocking {
        val bookmarks = RoomBookmarkStore(db.bookmarkDao())
        bookmarks.add(Bookmark(CardId("b01"), Instant.EPOCH))
        val d = LocalDate.of(2026, 11, 3)
        store.save(Trip(TripId("t"), "A", d, d, listOf(TripDay(d)), Instant.EPOCH))
        store.delete(TripId("t"))
        assertEquals(1, bookmarks.count())
    }

    @Test
    fun `v2 에서 v3 로 올리면 북마크가 남고 여행 표가 생긴다`() {
        migrations.createDatabase("trip-migrate.db", 2).use { v2 ->
            v2.execSQL("INSERT INTO bookmark (card_id, created_at) VALUES ('b01', 1)")
        }
        migrations.runMigrationsAndValidate("trip-migrate.db", 3, true).use { v3 ->
            v3.query("SELECT COUNT(*) FROM bookmark").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
            v3.query("SELECT COUNT(*) FROM trip").use { c ->
                c.moveToFirst()
                assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun `TRP-L7 저장이 제약에 걸리면 통째로 되돌려 이전 여행이 남는다`() = runBlocking {
        val d = LocalDate.of(2026, 11, 3)
        val before = Trip(TripId("t"), "Before", d, d, listOf(TripDay(d, stops = listOf(TripStop(StopId("s1"), PlaceId("p"))))), Instant.EPOCH)
        store.save(before)
        // 같은 날 같은 장소 두 번은 UNIQUE 에 걸린다. 지우고 다시 넣던 중이라도 남는 것은 이전 여행이어야 한다.
        val broken = before.copy(title = "Broken", days = listOf(TripDay(d, stops = listOf(TripStop(StopId("s2"), PlaceId("p")), TripStop(StopId("s3"), PlaceId("p"))))))
        assertTrue(runCatching { store.save(broken) }.isFailure)
        assertEquals(before, store.get(TripId("t")))
    }

    @Test
    fun `B-08 북마크를 해제해도 그 장소가 든 일정은 그대로다`() = runBlocking {
        val bookmarks = RoomBookmarkStore(db.bookmarkDao())
        bookmarks.add(Bookmark(CardId("t-market-1"), Instant.EPOCH))
        val d = LocalDate.of(2026, 11, 3)
        val trip = Trip(TripId("t"), "A", d, d, listOf(TripDay(d, stops = listOf(TripStop(StopId("s"), PlaceId("gwangjang"))))), Instant.EPOCH)
        store.save(trip)
        bookmarks.remove(CardId("t-market-1"))
        assertEquals(listOf(PlaceId("gwangjang")), store.get(TripId("t"))!!.days.single().stops.map { it.place })
    }
}
