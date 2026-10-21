package app.signkorean.ui.designsystem

import androidx.core.content.ContextCompat
import app.signkorean.domain.content.PictogramCatalog
import app.signkorean.domain.content.PictogramId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 표지판 픽토그램 세트 (구현 계획 Phase 20 D3, C-11). 카탈로그의 ID 마다 그림과 스크린리더 이름이 하나씩 있다. */
@RunWith(RobolectricTestRunner::class)
class PictogramSetTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `C-11 카탈로그의 모든 ID 에 그림이 있고, 그림이 있는 ID 는 모두 카탈로그에 있다`() {
        assertEquals(PictogramCatalog.BUNDLED.ids.map { it.value }.toSortedSet(), PictogramArt.drawnIds.toSortedSet())
    }

    @Test
    fun `세트는 30~40개다`() {
        assertTrue(PictogramArt.drawnIds.size.toString(), PictogramArt.drawnIds.size in 30..40)
    }

    @Test
    fun `그림은 모두 벡터로 열리고 이름은 비어 있지 않다`() {
        PictogramArt.drawnIds.forEach { id ->
            val drawable = PictogramArt.drawable(PictogramId(id))!!
            assertNotNull(id, ContextCompat.getDrawable(context, drawable))
            assertTrue(id, context.getString(PictogramArt.label(PictogramId(id))!!).isNotBlank())
        }
    }

    @Test
    fun `ja 스크린리더 이름도 그림마다 있다`() {
        val ja = app.signkorean.l10n.ResourceStrings.language("ja").strings.keys
        val missing = PictogramArt.drawnIds.map { context.resources.getResourceEntryName(PictogramArt.label(PictogramId(it))!!) }.filterNot { it in ja }
        assertEquals(emptyList<String>(), missing)
    }
}
