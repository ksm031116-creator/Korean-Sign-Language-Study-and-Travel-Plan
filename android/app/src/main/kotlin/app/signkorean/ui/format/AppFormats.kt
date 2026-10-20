package app.signkorean.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.signkorean.R
import java.math.BigDecimal
import java.math.RoundingMode
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

    /** 음수(시계 오차 등)는 화면을 멈추지 않도록 0 으로 보인다. 값의 검증은 계산하는 쪽의 몫이다. */
    fun duration(minutes: Int): String {
        val total = minutes.coerceAtLeast(0)
        val h = total / 60
        val m = total % 60
        return when {
            h > 0 && m > 0 -> patterns.durationHoursMinutes.format(locale, h, m)
            h > 0 -> patterns.durationHours.format(locale, h)
            else -> patterns.durationMinutes.format(locale, m)
        }
    }

    /**
     * 1km 미만은 m(정수), 이상은 km 소수 한 자리. 반올림은 십진수로 해서(0.05 는 올림) JVM 과 기기 결과가 같다.
     * 소수점 기호는 언어를 따른다.
     */
    fun distance(meters: Int): String {
        val m = meters.coerceAtLeast(0)
        if (m < 1000) return patterns.distanceMeters.format(locale, m)
        val km = BigDecimal.valueOf(m.toLong(), 3).setScale(1, RoundingMode.HALF_UP)
        val text = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(km)
        return patterns.distanceKilometers.format(locale, text)
    }
}

/**
 * 지금 화면 문구와 같은 언어의 형식. 형식 언어는 기기 언어가 아니라 문구를 가져온 리소스 폴더의 format_locale 이다.
 * 기기가 fr 이면 Android 는 기본(영어) 문구를 쓰는데, 날짜·숫자만 프랑스식이 되면 한 화면에 두 언어가 섞인다.
 */
@Composable
fun rememberAppFormats(): AppFormats {
    val locale = Locale.forLanguageTag(stringResource(R.string.format_locale))
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
