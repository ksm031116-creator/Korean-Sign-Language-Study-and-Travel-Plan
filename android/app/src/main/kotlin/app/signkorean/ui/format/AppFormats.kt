package app.signkorean.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import app.signkorean.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 언어별 형식 문구. 값은 언어별 문자열 리소스에서 온다. 언어를 추가할 때 코드는 바꾸지 않고
 * values-xx/ 에 같은 키를 넣는다 (설계서 §8.5, L10N-08).
 */
data class FormatPatterns(
    /** java.time 패턴. en "EEE, MMM d", ja "M月d日(E)". */
    val date: String,
    val durationHoursMinutes: String,
    val durationHours: String,
    val durationMinutes: String,
    val distanceMeters: String,
    /** 인자는 이미 현지 표기로 만든 숫자 문자열(예: "1.2", "1,2"). */
    val distanceKilometers: String,
)

/** 날짜·시간·소요 시간·거리 형식 (설계서 §8.5). 문자열을 이어 붙이지 않고 자리표시자 하나로 만든다. */
class AppFormats(private val locale: Locale, private val patterns: FormatPatterns) {

    private val dateFormatter = DateTimeFormatter.ofPattern(patterns.date, locale)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)

    fun date(value: LocalDate): String = dateFormatter.format(value)

    /** 24시간제 HH:mm (PRD UX05). 언어와 무관하다. */
    fun time(value: LocalTime): String = timeFormatter.format(value)

    fun duration(minutes: Int): String {
        require(minutes >= 0) { "소요 시간은 음수일 수 없다: $minutes" }
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> patterns.durationHoursMinutes.format(locale, h, m)
            h > 0 -> patterns.durationHours.format(locale, h)
            else -> patterns.durationMinutes.format(locale, m)
        }
    }

    /** 1km 미만은 m(정수), 이상은 km 소수 한 자리. 소수점 기호는 언어를 따른다. */
    fun distance(meters: Int): String {
        require(meters >= 0) { "거리는 음수일 수 없다: $meters" }
        if (meters < 1000) return patterns.distanceMeters.format(locale, meters)
        val km = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(meters / 1000.0)
        return patterns.distanceKilometers.format(locale, km)
    }
}

/** 지금 화면 언어의 형식. 언어가 바뀌면(구성 변경) 새로 만든다. */
@Composable
fun rememberAppFormats(): AppFormats {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.ENGLISH
    val patterns = FormatPatterns(
        date = stringResource(R.string.format_date_short),
        durationHoursMinutes = stringResource(R.string.format_duration_hm),
        durationHours = stringResource(R.string.format_duration_h),
        durationMinutes = stringResource(R.string.format_duration_m),
        distanceMeters = stringResource(R.string.format_distance_m),
        distanceKilometers = stringResource(R.string.format_distance_km),
    )
    return remember(locale, patterns) { AppFormats(locale, patterns) }
}
