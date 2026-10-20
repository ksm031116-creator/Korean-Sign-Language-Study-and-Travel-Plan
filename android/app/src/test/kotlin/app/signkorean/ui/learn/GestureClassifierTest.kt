package app.signkorean.ui.learn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 카드 제스처 판정 (PRD UX01, 설계서 §9.3 G-01~10, G-P1). 카드 400 × 500. */
class GestureClassifierTest {

    private val w = 400f
    private val h = 500f

    private fun classify(dx: Float, dy: Float, hasPlaces: Boolean = true, fromControl: Boolean = false) =
        GestureClassifier.classify(Drag(dx, dy), CardSize(w, h), hasPlaces, fromControl)

    @Test
    fun `G-01 가로 30 퍼센트 오른쪽은 알아요`() = assertEquals(Gesture.Know, classify(0.30f * w, 0.05f * h))

    @Test
    fun `G-02 가로 30 퍼센트 왼쪽은 몰라요`() = assertEquals(Gesture.DontKnow, classify(-0.30f * w, 0f))

    @Test
    fun `G-03 가로 24 퍼센트는 원위치`() = assertEquals(Gesture.None, classify(0.24f * w, 0f))

    @Test
    fun `G-04 대각선(비율 1_2)은 원위치`() = assertEquals(Gesture.None, classify(120f, 100f))

    @Test
    fun `G-05 아래 25 퍼센트, 장소 있음은 장소 목록`() = assertEquals(Gesture.Places, classify(0f, 0.25f * h))

    @Test
    fun `G-06 아래 25 퍼센트, Basics 는 원위치`() = assertEquals(Gesture.None, classify(0f, 0.25f * h, hasPlaces = false))

    @Test
    fun `G-07 위로 40 퍼센트는 원위치`() = assertEquals(Gesture.None, classify(0f, -0.40f * h))

    @Test
    fun `G-08 7px 이동은 탭이라 뒤집기`() = assertEquals(Gesture.Flip, classify(5f, 5f))

    @Test
    fun `G-09 북마크·음성 버튼에서 시작한 드래그는 무시`() {
        assertEquals(Gesture.None, classify(0.5f * w, 0f, fromControl = true))
        assertEquals(Gesture.None, classify(1f, 1f, fromControl = true))
    }

    @Test
    fun `G-10 경계값은 PRD 정의대로 (25 퍼센트·1_3·20 퍼센트는 판정, 8px 은 탭 아님)`() {
        assertEquals(Gesture.Know, classify(0.25f * w, 0f))
        assertEquals(Gesture.Know, classify(130f, 100f))
        assertEquals(Gesture.Places, classify(0f, 0.20f * h))
        assertEquals(Gesture.Places, classify(76.9f, 100f))
        assertEquals("8px 은 탭이 아니고 임계에도 못 미친다", Gesture.None, classify(8f, 0f))
        assertEquals(Gesture.Flip, classify(7.99f, 0f))
    }

    @Test
    fun `G-P1 결과는 항상 5종 중 하나이고 더 멀리 끌어도 결과가 바뀌지 않는다`() {
        val rnd = Random(7)
        repeat(5000) {
            val dx = rnd.nextFloat() * 2 * w - w
            val dy = rnd.nextFloat() * 2 * h - h
            val places = rnd.nextBoolean()
            val g = classify(dx, dy, places)
            assertTrue(g in Gesture.entries)
            if (g != Gesture.None && g != Gesture.Flip) {
                val k = 1f + rnd.nextFloat() * 3
                assertEquals("($dx,$dy)×$k", g, classify(dx * k, dy * k, places))
            }
        }
    }

    @Test
    fun `끄는 동안 도장 진하기는 방향별로 임계에서 1 이 된다`() {
        assertEquals(DragHint(Gesture.Know, 1f), GestureClassifier.hint(Drag(0.25f * w, 0f), CardSize(w, h), true))
        assertEquals(DragHint(Gesture.DontKnow, 0.5f), GestureClassifier.hint(Drag(-0.125f * w, 0f), CardSize(w, h), true))
        assertEquals(DragHint(Gesture.Places, 1f), GestureClassifier.hint(Drag(0f, 0.4f * h), CardSize(w, h), true))
        assertEquals("장소 없는 카드는 아래 도장이 없다", DragHint(Gesture.None, 0f), GestureClassifier.hint(Drag(0f, 0.4f * h), CardSize(w, h), false))
        assertEquals(DragHint(Gesture.None, 0f), GestureClassifier.hint(Drag(0f, -100f), CardSize(w, h), true))
    }
}
