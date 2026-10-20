package app.signkorean.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SequentialIdGeneratorConcurrencyTest {

    @Test
    fun `여러 스레드에서 동시에 불러도 같은 ID를 두 번 주지 않는다`() {
        val ids = SequentialIdGenerator()
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<String>())
        repeat(8) {
            pool.execute {
                start.await()
                repeat(2_000) { results += ids.next() }
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(10, TimeUnit.SECONDS)
        assertEquals(16_000, results.size)
        assertEquals(16_000, results.toSet().size)
    }
}
