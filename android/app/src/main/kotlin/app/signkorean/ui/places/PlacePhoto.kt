package app.signkorean.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.signkorean.R
import app.signkorean.domain.content.PhotoLicense
import app.signkorean.domain.content.PlacePhoto
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.UiText
import app.signkorean.ui.text.asString
import coil.compose.SubcomposeAsyncImage

object PhotoTags {
    const val UNCROPPED = "photo-uncropped"
    const val CROPPED = "photo-cropped"
    const val PLACEHOLDER = "photo-placeholder"
}

/** 사진 출처 표기 (C-10). 라이선스마다 요구하는 표시가 다르다. CC BY 는 저작자와 버전이 반드시 들어간다. */
object PhotoCredits {
    fun of(photo: PlacePhoto): UiText = when (val l = photo.license) {
        PhotoLicense.KoglType1 -> UiText.Res(R.string.photo_credit_kogl1, listOf(photo.source))
        PhotoLicense.KoglType3 -> UiText.Res(R.string.photo_credit_kogl3, listOf(photo.source))
        PhotoLicense.Cc0 -> UiText.Res(R.string.photo_credit_cc0, listOf(photo.source))
        is PhotoLicense.CcBy -> UiText.Res(R.string.photo_credit_ccby, listOf(l.author, l.version, photo.source))
    }
}

/** 콘텐츠 팩의 사진 주소 → 이미지 로더 주소. asset:// 은 팩의 약속이고 로더는 file:///android_asset/ 을 읽는다. */
object PhotoModel {
    private const val ASSET = "asset://"
    fun of(url: String): String = if (url.startsWith(ASSET)) "file:///android_asset/" + url.removePrefix(ASSET) else url
}

private fun parseColor(hex: String) =
    runCatching { Color(android.graphics.Color.parseColor(hex)).copy(alpha = 1f) }.getOrDefault(Color(0xFF2F6F5E))

/**
 * 장소 사진과 출처 (구현 계획 Phase 14 D1, U-11).
 * - 공공누리 제3유형(변경 금지)은 자르지 않고 원본 비율로 보인다(Fit: 사진 전체가 들어간다). 아주 긴 사진은 높이 480dp 안에 여백과 함께 보인다.
 * - 그 밖의 사진은 16:9 틀에 맞춰 자른다(라이선스가 허용한다).
 * - 사진이 없으면 지역 색 자리 표시, 실패하면 다시 시도 버튼. 출처는 사진이 있을 때 늘 보인다.
 */
@Composable
fun PlacePhotoView(photo: PlacePhoto?, fallbackColor: String, modifier: Modifier = Modifier) {
    val t = SignKoreanTheme.tokens
    val shape = RoundedCornerShape(14.dp)
    if (photo == null) {
        Box(
            modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(parseColor(fallbackColor)).testTag(PhotoTags.PLACEHOLDER),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.photo_none), color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    val uncropped = photo.license.noDerivatives
    var attempt by remember(photo.url) { mutableIntStateOf(0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val frame = if (uncropped) {
            Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 480.dp).clip(shape).testTag(PhotoTags.UNCROPPED)
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).testTag(PhotoTags.CROPPED)
        }
        Box(frame.background(t.line)) {
            key(attempt) {
                SubcomposeAsyncImage(
                    model = PhotoModel.of(photo.url),
                    // 장소 사진은 장식이다. 장소 이름이 바로 옆에 글자로 있다.
                    contentDescription = null,
                    contentScale = if (uncropped) ContentScale.Fit else ContentScale.Crop,
                    modifier = if (uncropped) Modifier.fillMaxWidth().heightIn(max = 480.dp) else Modifier.fillMaxSize(),
                    error = {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.photo_failed), style = MaterialTheme.typography.bodyMedium, color = t.muted)
                            QuietButton(stringResource(R.string.common_retry), onClick = { attempt++ })
                        }
                    },
                )
            }
        }
        Text(PhotoCredits.of(photo).asString(), style = MaterialTheme.typography.labelSmall, color = t.muted)
    }
}
