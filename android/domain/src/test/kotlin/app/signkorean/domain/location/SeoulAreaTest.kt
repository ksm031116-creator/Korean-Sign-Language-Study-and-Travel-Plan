package app.signkorean.domain.location

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 서울 범위와 추천 출발점 (FR14, PRD §10.4). */
class SeoulAreaTest {

    @Test
    fun `서울 안 - 서울역과 노원`() {
        assertTrue(SeoulArea.contains(Coordinate(37.5547, 126.9707)))
        assertTrue(SeoulArea.contains(Coordinate(37.6542, 127.0568)))
    }

    @Test
    fun `서울 밖 - 도쿄, 부산, 뉴욕`() {
        assertFalse(SeoulArea.contains(Coordinate(35.6812, 139.7671)))
        assertFalse(SeoulArea.contains(Coordinate(35.1151, 129.0415)))
        assertFalse(SeoulArea.contains(Coordinate(40.7128, -74.0060)))
    }

    @Test
    fun `추천 출발점은 서울역과 시청이고 모두 서울 안`() {
        val keys = SeoulArea.suggestedStarts.map { it.key }
        assertTrue("seoul-station" in keys && "city-hall" in keys)
        assertTrue(SeoulArea.suggestedStarts.all { SeoulArea.contains(it.coordinate) })
    }
}
