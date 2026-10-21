package app.signkorean.domain.trip

import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.location.Coordinate
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeTripStore
import app.signkorean.testing.FixedClock
import app.signkorean.testing.InMemoryContentRepository
import app.signkorean.testing.SequentialIdGenerator
import app.signkorean.testing.TripStoreContract
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 여행 규칙과 편집 (FR17~22, 설계서 §6.5). T-08~12·17·18, 포트 계약 */
class TripEditorTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val pack = ContentFixtures.pack(
        cards = listOf(ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market")), ContentFixtures.basic("b01")),
        places = (1..12).map { ContentFixtures.place("p$it", areaId = "myeongdong") },
    )
    private val store = FakeTripStore()
    private var entitlement = Entitlement.Paid
    private val editor = TripEditor(store, SequentialIdGenerator("id"), clock, InMemoryContentRepository(pack), { entitlement })
    private val d1 = LocalDate.of(2026, 11, 3)
    private fun d(n: Long) = d1.plusDays(n)

    private suspend fun trip(days: Long = 3): Trip =
        (editor.create("  Seoul in autumn  ", d1, d(days - 1)) as TripResult.Ok).trip

    private suspend fun TripResult.ok() = (this as TripResult.Ok).trip

    @Test
    fun `만들면 제목을 다듬고 날짜마다 빈 하루, 기본 출발 09시`() = runTest {
        val t = trip(3)
        assertEquals("Seoul in autumn", t.title)
        assertEquals(listOf(d1, d(1), d(2)), t.days.map { it.date })
        assertTrue(t.days.all { it.startTime == LocalTime.of(9, 0) && it.stops.isEmpty() && it.origin == null })
        assertEquals(t, store.get(t.id))
    }

    @Test
    fun `빈 제목은 거부`() = runTest {
        assertEquals(TripResult.Rejected(TripProblem.TitleBlank), editor.create("   ", d1, d1))
    }

    @Test
    fun `T-10 종료일이 시작일보다 앞이거나 15일이면 저장 거부`() = runTest {
        assertEquals(TripResult.Rejected(TripProblem.EndBeforeStart), editor.create("A", d1, d1.minusDays(1)))
        assertEquals(TripResult.Rejected(TripProblem.TooLong), editor.create("A", d1, d(14)))
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `T-18 경계값 - 14일·480분 허용, 15일·481분·음수·소수 거부`() = runTest {
        assertTrue(editor.create("A", d1, d(13)) is TripResult.Ok)
        assertEquals(480, TripRules.parseStay("480"))
        assertNull(TripRules.parseStay("481"))
        assertNull(TripRules.parseStay("-1"))
        assertNull(TripRules.parseStay("1.5"))
        assertNull(TripRules.parseStay(""))
        assertEquals(0, TripRules.parseStay(" 0 "))
        val t = trip(1)
        val withStop = editor.addStop(t.id, d1, "p1").ok()
        val stop = withStop.day(d1).stops.single().id
        assertTrue(editor.setStay(t.id, d1, stop, 480) is TripResult.Ok)
        assertEquals(TripResult.Rejected(TripProblem.InvalidStay), editor.setStay(t.id, d1, stop, 481))
        assertEquals(TripResult.Rejected(TripProblem.InvalidStay), editor.setStay(t.id, d1, stop, -1))
    }

    @Test
    fun `T-08 같은 날 같은 장소는 거부, 다른 날에는 된다`() = runTest {
        val t = trip(2)
        editor.addStop(t.id, d1, "p1").ok()
        assertEquals(TripResult.Rejected(TripProblem.Duplicate), editor.addStop(t.id, d1, "p1"))
        assertTrue(editor.addStop(t.id, d(1), "p1") is TripResult.Ok)
    }

    @Test
    fun `T-09 하루 10곳이면 추가도, 그날로 옮기기도 거부`() = runTest {
        val t = trip(2)
        (1..10).forEach { editor.addStop(t.id, d1, "p$it").ok() }
        assertEquals(TripResult.Rejected(TripProblem.DayFull), editor.addStop(t.id, d1, "p11"))
        val other = editor.addStop(t.id, d(1), "p11").ok().day(d(1)).stops.single().id
        assertEquals(TripResult.Rejected(TripProblem.DayFull), editor.moveStop(t.id, d(1), other, d1))
    }

    @Test
    fun `옮길 날에 같은 장소가 있으면 거부`() = runTest {
        val t = trip(2)
        editor.addStop(t.id, d1, "p1").ok()
        val s = editor.addStop(t.id, d(1), "p1").ok().day(d(1)).stops.single().id
        assertEquals(TripResult.Rejected(TripProblem.Duplicate), editor.moveStop(t.id, d(1), s, d1))
    }

    @Test
    fun `T-12 Basics 카드·지역 묶음·없는 장소는 목적지가 아니다`() = runTest {
        val t = trip(1)
        assertEquals(TripResult.Rejected(TripProblem.NotADestination), editor.addStop(t.id, d1, "b01"))
        assertEquals(TripResult.Rejected(TripProblem.NotADestination), editor.addStop(t.id, d1, "myeongdong"))
        assertEquals(TripResult.Rejected(TripProblem.NotADestination), editor.addStop(t.id, d1, "nope"))
    }

    @Test
    fun `T-11 기간을 줄여 일정이 범위 밖이 되면 저장 차단, 일정은 그대로`() = runTest {
        val t = trip(3)
        editor.addStop(t.id, d(2), "p1").ok()
        val r = editor.update(t.id, "Seoul", d1, d(1))
        assertEquals(TripResult.Rejected(TripProblem.StopsOutsideDates, listOf(d(2))), r)
        assertEquals(1, store.get(t.id)!!.day(d(2)).stops.size)
        assertEquals(d(2), store.get(t.id)!!.end)
    }

    @Test
    fun `기간을 늘리거나 옮기면 빈 날은 새로, 일정이 있는 날은 그대로`() = runTest {
        val t = trip(2)
        editor.addStop(t.id, d(1), "p1").ok()
        val longer = editor.update(t.id, "Seoul", d1, d(3)).ok()
        assertEquals(4, longer.days.size)
        assertEquals(1, longer.day(d(1)).stops.size)
        val shifted = editor.update(t.id, "Seoul", d(1), d(3)).ok()
        assertEquals(listOf(d(1), d(2), d(3)), shifted.days.map { it.date })
    }

    @Test
    fun `순서 바꾸기·체류·삭제·출발 설정은 그날 revision 을 올리고 경로 다시 계산 표시`() = runTest {
        val t = trip(1)
        editor.addStop(t.id, d1, "p1")
        val day = editor.addStop(t.id, d1, "p2").ok().day(d1)
        assertEquals(2, day.revision)
        val second = day.stops[1].id
        val reordered = editor.reorder(t.id, d1, second, 0).ok().day(d1)
        assertEquals(listOf(PlaceId("p2"), PlaceId("p1")), reordered.stops.map { it.place })
        assertEquals(3, reordered.revision)
        assertTrue(reordered.needsRecalculation)
        val started = editor.setStart(t.id, d1, LocalTime.of(10, 30), Origin.AtPlace(PlaceId("p3"))).ok().day(d1)
        assertEquals(LocalTime.of(10, 30), started.startTime)
        assertEquals(Origin.AtPlace(PlaceId("p3")), started.origin)
        val removed = editor.removeStop(t.id, d1, second).ok().day(d1)
        assertEquals(listOf(PlaceId("p1")), removed.stops.map { it.place })
        assertEquals(5, removed.revision)
    }

    @Test
    fun `순서 범위 밖 위치는 끝으로 맞춘다`() = runTest {
        val t = trip(1)
        editor.addStop(t.id, d1, "p1")
        val first = editor.addStop(t.id, d1, "p2").ok().day(d1).stops[0].id
        assertEquals(listOf(PlaceId("p2"), PlaceId("p1")), editor.reorder(t.id, d1, first, 99).ok().day(d1).stops.map { it.place })
    }

    @Test
    fun `T-17 여행 삭제는 그 여행만 지운다(학습·북마크는 다른 저장소)`() = runTest {
        val a = trip(1)
        val b = trip(1)
        assertTrue(editor.delete(a.id))
        assertNull(store.get(a.id))
        assertEquals(listOf(b.id), store.all().map { it.id })
        val params = TripEditor::class.java.constructors.single().parameterTypes.map { it.simpleName }.toSet()
        assertEquals(setOf("TripStore", "IdGenerator", "Clock", "ContentRepository", "EntitlementSource"), params)
    }

    @Test
    fun `없는 여행과 저장 실패`() = runTest {
        assertEquals(TripResult.Rejected(TripProblem.NotFound), editor.addStop(TripId("nope"), d1, "p1"))
        val t = trip(1)
        store.failNextSave = true
        assertEquals(TripResult.SaveFailed, editor.addStop(t.id, d1, "p1"))
        assertTrue(store.get(t.id)!!.day(d1).stops.isEmpty())
    }

    @Test
    fun `여행 밖 날짜에는 넣지 않는다`() = runTest {
        val t = trip(1)
        assertEquals(TripResult.Rejected(TripProblem.DateOutsideTrip), editor.addStop(t.id, d(5), "p1"))
    }

    @Test
    fun `포트 계약 - 가짜 저장소`() = runTest {
        TripStoreContract(FakeTripStore()).verifyAll()
    }

    // ---------- Phase 16 리뷰 수정 ----------

    @Test
    fun `TRP-01 저장된 여행에 날짜 하나가 빠져 있어도 그날로 옮긴 장소가 사라지지 않는다`() = runTest {
        val t = trip(2)
        val s = editor.addStop(t.id, d1, "p1").ok().day(d1).stops.single().id
        store.save(store.get(t.id)!!.let { it.copy(days = it.days.filter { d -> d.date == d1 }) }) // 둘째 날 행이 없다
        val moved = editor.moveStop(t.id, d1, s, d(1)).ok()
        assertEquals(listOf(PlaceId("p1")), moved.day(d(1)).stops.map { it.place })
        assertEquals(listOf(d1, d(1)), store.get(t.id)!!.days.map { it.date })
        assertEquals(1, store.get(t.id)!!.placeCount)
    }

    @Test
    fun `옮기면 두 날 모두 revision 이 오르고 장소 ID 는 그대로`() = runTest {
        val t = trip(2)
        val s = editor.addStop(t.id, d1, "p1").ok().day(d1).stops.single().id
        val moved = editor.moveStop(t.id, d1, s, d(1)).ok()
        assertEquals(2, moved.day(d1).revision)
        assertEquals(1, moved.day(d(1)).revision)
        assertEquals(s, moved.day(d(1)).stops.single().id)
    }

    @Test
    fun `TRP-04 콘텐츠를 못 읽어도 예외가 아니라 결과로 끝난다`() = runTest {
        val broken = object : app.signkorean.domain.content.ContentRepository {
            override suspend fun load() = throw java.io.IOException("asset")
        }
        val e = TripEditor(store, SequentialIdGenerator("x"), clock, broken, { Entitlement.Paid })
        val t = (e.create("A", d1, d1) as TripResult.Ok).trip
        assertEquals(TripResult.SaveFailed, e.addStop(t.id, d1, "p1"))
    }

    @Test
    fun `TRP-05 시작 시각은 분 단위로 저장한다`() = runTest {
        val t = trip(1)
        val day = editor.setStart(t.id, d1, LocalTime.of(10, 15, 42), null).ok().day(d1)
        assertEquals(LocalTime.of(10, 15), day.startTime)
    }

    @Test
    fun `E-12 무료가 되면 여행은 그대로 남고 읽기만 된다`() = runTest {
        val a = trip(1)
        editor.addStop(a.id, d1, "p1")
        trip(1)
        entitlement = Entitlement.Free
        assertEquals(2, store.all().size, "지우지 않는다")
        assertEquals(TripResult.Rejected(TripProblem.PaidOnly), editor.addStop(a.id, d1, "p2"))
        assertEquals(TripResult.Rejected(TripProblem.PaidOnly), editor.create("B", d1, d1))
        assertEquals(TripResult.Rejected(TripProblem.PaidOnly), editor.update(a.id, "C", d1, d1))
        assertEquals(1, store.get(a.id)!!.placeCount)
        assertTrue(editor.delete(a.id), "사용자가 지우는 것은 된다(자기 데이터)")
    }

    @Test
    fun `TRP-L1 바뀐 게 없으면 revision 도 저장도 그대로`() = runTest {
        val t = trip(1)
        val first = editor.addStop(t.id, d1, "p1").ok().day(d1)
        val same = editor.reorder(t.id, d1, first.stops.single().id, 0).ok().day(d1)
        assertEquals(first.revision, same.revision)
        val again = editor.setStart(t.id, d1, first.startTime, first.origin).ok().day(d1)
        assertEquals(first.revision, again.revision)
    }

    @Test
    fun `TRP-L3 출발지 장소도 등록 장소만`() = runTest {
        val t = trip(1)
        assertEquals(TripResult.Rejected(TripProblem.NotADestination), editor.setStart(t.id, d1, LocalTime.of(9, 0), Origin.AtPlace(PlaceId("myeongdong"))))
        assertTrue(editor.setStart(t.id, d1, LocalTime.of(9, 0), Origin.AtCoordinate(Coordinate(37.55, 126.97), "Seoul Station")) is TripResult.Ok)
    }
}
