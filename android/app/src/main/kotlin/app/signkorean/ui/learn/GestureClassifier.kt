package app.signkorean.ui.learn

import kotlin.math.abs
import kotlin.math.hypot

/** 카드 위 제스처 결과. 늘 이 다섯 중 하나다 (G-P1). */
enum class Gesture { Know, DontKnow, Places, Flip, None }

/** 손가락이 카드를 끈 거리(px). 아래가 +y. */
data class Drag(val dx: Float, val dy: Float)

data class CardSize(val width: Float, val height: Float)

/** 끄는 동안의 안내: 어느 방향 도장을, 얼마나 진하게(0~1) 보일지 (UX11 ②). */
data class DragHint(val direction: Gesture, val strength: Float)

/**
 * 제스처 판정 (PRD UX01). 순수 함수라 JVM 에서 시험한다.
 * - 가로 ≥ 너비 25% 그리고 가로 ≥ 세로 × 1.3 → 오른쪽 알아요, 왼쪽 몰라요
 * - 아래 ≥ 높이 20% 그리고 세로 ≥ 가로 × 1.3 → 장소 목록 (장소 없는 카드는 원위치)
 * - 이동 8px 미만 → 탭(뒤집기). 위로는 동작 없음
 * - 버튼(북마크·음성)에서 시작한 터치는 무시
 */
object GestureClassifier {

    const val HORIZONTAL = 0.25f
    const val DOWN = 0.20f
    const val RATIO = 1.3f
    const val TAP_SLOP_PX = 8f

    fun classify(drag: Drag, size: CardSize, hasPlaces: Boolean, startedOnControl: Boolean): Gesture {
        if (startedOnControl) return Gesture.None
        val ax = abs(drag.dx)
        val ay = abs(drag.dy)
        if (hypot(drag.dx, drag.dy) < TAP_SLOP_PX) return Gesture.Flip
        if (ax >= size.width * HORIZONTAL && ax >= ay * RATIO) {
            return if (drag.dx > 0) Gesture.Know else Gesture.DontKnow
        }
        if (drag.dy > 0 && drag.dy >= size.height * DOWN && drag.dy >= ax * RATIO) {
            return if (hasPlaces) Gesture.Places else Gesture.None
        }
        return Gesture.None
    }

    /** 지금 끄는 방향과 임계까지의 비율. 우세한 축이 비율 조건을 못 넘으면 도장을 보이지 않는다. */
    fun hint(drag: Drag, size: CardSize, hasPlaces: Boolean): DragHint {
        val ax = abs(drag.dx)
        val ay = abs(drag.dy)
        return when {
            ax >= ay * RATIO && ax > 0f ->
                DragHint(if (drag.dx > 0) Gesture.Know else Gesture.DontKnow, (ax / (size.width * HORIZONTAL)).coerceAtMost(1f))
            drag.dy > 0f && drag.dy >= ax * RATIO && hasPlaces ->
                DragHint(Gesture.Places, (drag.dy / (size.height * DOWN)).coerceAtMost(1f))
            else -> DragHint(Gesture.None, 0f)
        }
    }
}
