package app.signkorean.l10n

import app.signkorean.ui.format.AppFormats
import app.signkorean.ui.format.FormatPatterns
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** 날짜·시간·소요 시간·거리 형식 (설계서 §8.5). L10N-05. 실제 리소스 문구로 만든다. */
class AppFormatsTest {

    private fun formats(qualifier: String, locale: Locale): AppFormats {
        val base = ResourceStrings.load(File(ResourceStrings.MAIN_RES, "values"))
        val lang = if (qualifier == "") base else ResourceStrings.load(File(ResourceStrings.MAIN_RES, "values-$qualifier"))
        fun s(name: String) = lang.strings[name] ?: base[name]
        return AppFormats(
            locale,
            FormatPatterns(
                date = s("format_date_short"),
                durationHoursMinutes = s("format_duration_hm"),
                durationHours = s("format_duration_h"),
                durationMinutes = s("format_duration_m"),
                distanceMeters = s("format_distance_m"),
                distanceKilometers = s("format_distance_km"),
            ),
        )
    }

    private val en = formats("", Locale.ENGLISH)
    private val ja = formats("ja", Locale.JAPANESE)
    private val oct7 = LocalDate.of(2026, 10, 7)

    @Test
    fun `날짜`() {
        assertEquals("Wed, Oct 7", en.date(oct7))
        assertEquals("10月7日(水)", ja.date(oct7))
    }

    @Test
    fun `시간은 두 언어 모두 24시간제`() {
        assertEquals("09:00", en.time(LocalTime.of(9, 0)))
        assertEquals("21:05", ja.time(LocalTime.of(21, 5)))
    }

    @Test
    fun `소요 시간`() {
        assertEquals("1h 20min", en.duration(80))
        assertEquals("1時間20分", ja.duration(80))
        assertEquals("2h", en.duration(120))
        assertEquals("2時間", ja.duration(120))
        assertEquals("45min", en.duration(45))
        assertEquals("45分", ja.duration(45))
        assertEquals("0min", en.duration(0))
    }

    @Test
    fun `거리는 1km 미만 m, 이상 km 소수 한 자리`() {
        assertEquals("850 m", en.distance(850))
        assertEquals("1.2 km", en.distance(1234))
        assertEquals("1.0 km", en.distance(1000))
        assertEquals("850 m", ja.distance(850))
        assertEquals("1.2 km", ja.distance(1249))
    }

    @Test
    fun `독일어처럼 소수점이 쉼표인 언어는 그 표기를 따른다`() {
        val de = AppFormats(Locale.GERMAN, FormatPatterns("d. MMM", "%1\$d h %2\$d min", "%1\$d h", "%1\$d min", "%1\$d m", "%1\$s km"))
        assertEquals("1,2 km", de.distance(1200))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `음수 소요 시간은 거부한다`() {
        en.duration(-1)
    }
}
