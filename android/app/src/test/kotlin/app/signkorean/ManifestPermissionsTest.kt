package app.signkorean

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
}
