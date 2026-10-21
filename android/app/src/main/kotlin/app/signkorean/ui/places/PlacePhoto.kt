package app.signkorean.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.signkorean.R
import app.signkorean.domain.content.PhotoLicense
import app.signkorean.domain.content.PlacePhoto
import app.signkorean.domain.review.ExternalLinks
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.UiText
import app.signkorean.ui.text.asString
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

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

/**
 * 사진을 어떻게 그리는가. 라이선스가 정하며 화면은 이 값만 따른다(Phase 14 리뷰 PLC-06: 순수 함수로 검사한다).
 * - [Original]: 사진 전체를 원본 비율로, 모서리도 깎지 않는다. 공공누리 제3유형(변경 금지)은 자르기·모서리 둥글리기도
 *   변경이다(PRD §11.2, PLC-01). CC BY 는 잘라도 되지만 그러면 변경 사실을 표시해야 하므로 자르지 않는다(PLC-03).
 * - [Cropped]: 16:9 틀에 맞춰 자르고 모서리를 둥글린다(공공누리 제1유형, CC0).
 */
enum class PhotoFit {
    Original,
    Cropped,
    ;

    companion object {
        fun of(license: PhotoLicense): PhotoFit = when (license) {
            PhotoLicense.KoglType3, is PhotoLicense.CcBy -> Original
            PhotoLicense.KoglType1, PhotoLicense.Cc0 -> Cropped
        }
    }
}

private fun parseColor(hex: String) =
    runCatching { Color(android.graphics.Color.parseColor(hex)).copy(alpha = 1f) }.getOrDefault(Color(0xFF2F6F5E))

/**
 * 장소 사진과 출처 (구현 계획 Phase 14 D1, U-11). 사진이 없으면 지역 색 자리 표시, 실패하면 다시 시도 버튼.
 * 출처는 사진 밖 별도 줄에 늘 보이고, 출처 주소가 허용 목록에 있으면 눌러서 원본 페이지를 연다(UX08).
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
    val fit = PhotoFit.of(photo.license)
    var attempt by remember(photo.url) { mutableIntStateOf(0) }
    var failed by remember(photo.url) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val frame = when (fit) {
            // 틀에 clip 을 걸지 않는다. 사진 픽셀을 지우지 않는다.
            PhotoFit.Original -> Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 480.dp).testTag(PhotoTags.UNCROPPED)
            PhotoFit.Cropped -> Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(t.line).testTag(PhotoTags.CROPPED)
        }
        Box(frame, contentAlignment = Alignment.Center) {
            key(attempt) {
                AsyncImage(
                    model = PhotoModel.of(photo.url),
                    // 장소 사진은 장식이다. 장소 이름이 바로 옆에 글자로 있다.
                    contentDescription = null,
                    contentScale = if (fit == PhotoFit.Original) ContentScale.Fit else ContentScale.Crop,
                    modifier = if (fit == PhotoFit.Original) Modifier.fillMaxWidth().heightIn(max = 480.dp) else Modifier.fillMaxSize(),
                    onState = { failed = it is AsyncImagePainter.State.Error },
                )
            }
            if (failed) {
                Column(Modifier.fillMaxWidth().background(t.line).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.photo_failed), style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    QuietButton(stringResource(R.string.common_retry), onClick = {
                        failed = false
                        attempt++
                    })
                }
            }
        }
        val credit = PhotoCredits.of(photo).asString()
        val source = photo.sourceUrl?.takeIf(ExternalLinks::isAllowed)
        val uri = LocalUriHandler.current
        Text(
            credit,
            style = MaterialTheme.typography.labelSmall,
            color = if (source != null) t.primary else t.muted,
            modifier = if (source != null) Modifier.clickable(role = Role.Button) { runCatching { uri.openUri(source) } } else Modifier,
        )
    }
}
