package app.signkorean.domain.learning

import app.signkorean.domain.content.CardId
import app.signkorean.testing.ContentFixtures.basic
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random

/** L-P1: 임의 판정·Undo 열에서 세션 불변식이 깨지지 않는다. 씨앗을 고정해 실패를 재현할 수 있게 한다. */
class SessionPropertyTest {

    private val now = Instant.parse("2026-10-21T09:00:00Z")

    @Test
    fun `L-P1 임의 판정 열 1000개에서 불변식이 지켜진다`() {
        repeat(1000) { seed ->
            val rnd = Random(seed)
            val n = rnd.nextInt(1, 13)
            val mode = if (rnd.nextBoolean()) Mode.All else Mode.Review
            val cards = (1..n).map { basic("b%02d".format(it), "단어$it") }
            val states = cards.associate { it.id to CardStatus.entries[rnd.nextInt(3)] }.toMutableMap()
            val started = SessionFactory.start(SessionId("s$seed"), SessionSource.Basics, mode, cards, states)
            if (started !is StartResult.Started) return@repeat
            var s = started.session
            if (mode == Mode.Review) assertTrue(s.queue.all { states[it.card] == CardStatus.Learning }, "seed=$seed Review 큐에 learning 아닌 카드")

            val steps = rnd.nextInt(100, 1000)
            var previous: CardId? = null
            for (i in 0 until steps) {
                if (s.finished && rnd.nextInt(4) != 0) break
                if (rnd.nextInt(6) == 0) {
                    val u = SessionReducer.undo(s) ?: continue
                    states[u.restoredState.card] = u.restoredState.status
                    s = u.session
                    previous = null
                    continue
                }
                val shown = s.current ?: break
                val alternatives = s.queue.map { it.card }.toSet() - shown
                if (previous != null && shown == previous) {
                    assertTrue(alternatives.isEmpty(), "seed=$seed 다른 카드가 있는데 같은 카드가 연속으로 나왔다")
                }
                val verdict = if (rnd.nextBoolean()) Verdict.Know else Verdict.DontKnow
                val t = SessionReducer.judge(s, verdict, now) ?: break
                states[t.cardState.card] = t.cardState.status
                previous = shown
                s = t.session

                assertTrue(s.decisions.size <= SessionRules.MAX_DECISIONS, "seed=$seed 판정이 10회를 넘었다")
                if (verdict == Verdict.Know) {
                    assertTrue(s.queue.none { it.card == shown }, "seed=$seed 안다고 한 카드가 다시 대기열에 있다")
                }
                assertTrue(s.queue.map { it.card }.toSet().size == s.queue.size, "seed=$seed 대기열에 같은 카드가 두 번 있다")
            }
        }
    }
}
