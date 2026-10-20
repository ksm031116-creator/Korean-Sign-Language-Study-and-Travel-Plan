package app.signkorean.adapter.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class UuidIdGeneratorTest {

    private val ids = UuidIdGenerator()

    @Test
    fun `UUID 형식의 ID를 만든다`() {
        val id = ids.next()
        assertEquals(id, UUID.fromString(id).toString())
    }

    @Test
    fun `여러 번 만들어도 겹치지 않는다`() {
        val made = List(10_000) { ids.next() }
        assertEquals(made.size, made.toSet().size)
        assertTrue(made.none { it.isBlank() })
    }
}
