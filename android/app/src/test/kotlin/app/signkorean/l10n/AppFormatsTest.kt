package app.signkorean.l10n

import app.signkorean.ui.format.AppFormats
import app.signkorean.ui.format.FormatPatterns
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 날짜·시간·소요 시간·거리 형식 (설계서 §8.5). L10N-05. 실제 리소스 문구와 리소스의 format_locale 로 만든다. */
class AppFormatsTest {

    /** 앱이 하는 것과 같이 문구와 형식 언어를 모두 같은 언어 폴더에서 가져온다. */
    private fun formats(qualifier: String): AppFormats {
        val base = ResourceStrings.language("default")
        val lang = ResourceStrings.language(qualifier)
        fun s(name: String) = lang.strings[name] ?: base[name]
        return AppFormats(Locale.forLanguageTag(s("format_locale")), patterns(::s))
    }

    private fun patterns(s: (String) -> String) = FormatPatterns(
        date = s("format_date_short"),
        durationHoursMinutes = s("format_duration_hm"),
        durationHours = s("format_duration_h"),
        durationMinutes = s("format_duration_m"),
        distanceMeters = s("format_distance_m"),
        distanceKilometers = s("format_distance_km"),
    )

    private val en = formats("default")
    private val ja = formats("ja")
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
    fun `거리는 1km 미만 m, 이상 km 소수 한 자리 반올림`() {
        assertEquals("850 m", en.distance(850))
        assertEquals("1.0 km", en.distance(1000))
        assertEquals("1.2 km", en.distance(1234))
        assertEquals("850 m", ja.distance(850))
        assertEquals("1.2 km", ja.distance(1249))
    }

    @Test
    fun `km 반올림은 기기와 JVM 에서 같도록 십진수로 한다 (0·05 는 올림)`() {
        assertEquals("1.1 km", en.distance(1050))
        assertEquals("1.2 km", en.distance(1150))
        assertEquals("1.3 km", en.distance(1250))
        assertEquals("1.4 km", en.distance(1350))
        assertEquals("2.1 km", en.distance(2050))
    }

    @Test
    fun `기기 언어가 프랑스어여도 영어 문구에는 영어 형식을 쓴다 (리뷰 HIGH-1 회귀)`() {
        // values-fr 이 없으므로 기기가 fr 이면 Android 는 기본(영어) 문구를 쓴다. 형식 언어도 그 폴더의 format_locale 이다.
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.FRENCH)
        try {
            val f = formats("default")
            assertEquals("Wed, Oct 7", f.date(oct7))
            assertEquals("1.2 km", f.distance(1200))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `모든 언어 폴더의 형식 문구는 실제로 쓸 수 있다 (새 언어의 잘못된 날짜 패턴을 잡는다)`() {
        val base = ResourceStrings.language("default")
        ResourceStrings.all().forEach { lang ->
            fun s(name: String) = lang.strings[name] ?: base[name]
            DateTimeFormatter.ofPattern(s("format_date_short"), Locale.forLanguageTag(s("format_locale")))
            val f = AppFormats(Locale.forLanguageTag(s("format_locale")), patterns(::s))
            f.date(oct7); f.duration(80); f.duration(60); f.duration(5); f.distance(10); f.distance(1500)
        }
    }

    @Test
    fun `각 언어 폴더의 format_locale 은 그 폴더 언어다`() {
        ResourceStrings.all().forEach { lang ->
            val expected = if (lang.qualifier == "default") "en" else lang.qualifier
            assertEquals(lang.qualifier, expected, lang.strings["format_locale"] ?: ResourceStrings.language("default")["format_locale"])
        }
    }

    @Test
    fun `음수는 화면을 멈추지 않고 0 으로 보인다`() {
        assertEquals("0min", en.duration(-5))
        assertEquals("0 m", en.distance(-1))
    }

    @Test
    fun `독일어처럼 소수점이 쉼표인 언어는 그 표기를 따른다`() {
        val de = AppFormats(Locale.GERMAN, FormatPatterns("d. MMM", "%1\$d h %2\$d min", "%1\$d h", "%1\$d min", "%1\$d m", "%1\$s km"))
        assertEquals("1,2 km", de.distance(1200))
    }
}
