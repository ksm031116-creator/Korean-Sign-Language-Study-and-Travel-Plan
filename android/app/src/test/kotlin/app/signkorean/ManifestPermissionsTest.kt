package app.signkorean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 위치 권한은 앞에서 쓰는 대략·정확 위치뿐이다. 백그라운드 위치는 선언하지 않는다 (구현 계획 Phase 19 D3, NFR05). */
class ManifestPermissionsTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `대략·정확 위치 권한만 선언한다`() {
        assertTrue(manifest.contains("android.permission.ACCESS_COARSE_LOCATION"))
        assertTrue(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
    }

    @Test
    fun `백그라운드 위치 권한은 없다`() {
        assertFalse(manifest.contains("ACCESS_BACKGROUND_LOCATION"))
    }

    @Test
    fun `평문 http 는 PC 중계 서버의 루프백 주소 하나에만 허용한다`() {
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        val config = File("src/main/res/xml/network_security_config.xml").readText()
        assertTrue(config.contains("<base-config cleartextTrafficPermitted=\"false\""))
        val domains = Regex("<domain[^>]*>([^<]+)</domain>").findAll(config).map { it.groupValues[1].trim() }.toList()
        assertEquals(listOf("127.0.0.1"), domains)
        assertFalse(config.contains("includeSubdomains=\"true\""))
    }
}
