package app.signkorean.l10n

import app.signkorean.adapter.content.ContentPackParser
import app.signkorean.domain.common.LocaleTag
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 출시 빌드의 UI 언어와 콘텐츠 언어는 같은 기준으로 열린다 (설계서 §8.8, 리뷰 MEDIUM-2).
 * 원어민 검수 전 언어의 UI 문구가 출시 빌드에 들어가면, 그 언어 사용자에게 검수 안 된 UI 와 영어 콘텐츠가 섞여 보인다.
 */
class ReleaseLanguagesTest {

    private val pack = ContentPackParser().parse(File("../adapter/src/main/assets/content/content-pack.json").readText())

    @Test
    fun `출시 리소스의 언어는 콘텐츠 팩에서 검수 완료된 언어와 같다`() {
        val releaseUi = ResourceStrings.all(ResourceStrings.MAIN_RES).map { if (it.qualifier == "default") "en" else it.qualifier }.toSet()
        val releasable = pack.selectableLocales(includeDrafts = false).map(LocaleTag::value).toSet()
        assertEquals(releasable, releaseUi)
    }

    @Test
    fun `검수 전 언어는 디버그 리소스에만 있다`() {
        val draft = pack.locales.keys.map(LocaleTag::value).toSet() - pack.selectableLocales(includeDrafts = false).map(LocaleTag::value).toSet()
        val debugUi = ResourceStrings.all(ResourceStrings.DEBUG_RES).map { it.qualifier }.toSet()
        assertEquals(draft, debugUi)
    }
}
