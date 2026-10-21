package app.signkorean.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signkorean.R
import app.signkorean.domain.content.PictogramId
import app.signkorean.domain.content.SignCard
import app.signkorean.ui.text.KoreanText

enum class PlateFace { Front, Back }

/** Regular: 학습 카드·상세. Mini: 목록 미리보기(좁은 칸에서도 단어를 한 줄로). */
enum class PlateSize { Regular, Mini }

object SignPlateTags {
    const val PLATE = "sign-plate"
}

/**
 * 표지판 안에 실제로 그린 픽토그램 ID. 표지판은 스크린리더에 한 덩어리(설명 하나)로 보이도록 자식 의미를 지우므로,
 * 테스트가 그림 유무를 확인할 수 있게 표지판 노드에 따로 싣는다(U-18).
 */
val DrawnPictogram = SemanticsPropertyKey<String>("DrawnPictogram")
private var SemanticsPropertyReceiver.drawnPictogram by DrawnPictogram

/** 픽토그램 ID → 벡터 그림과 스크린리더 이름. 그림이 아직 없는 ID 는 단어만 그린다(C-11). */
object PictogramArt {
    private val art: Map<String, Pair<Int, Int>> = mapOf(
        "restroom" to (R.drawable.pictogram_restroom to R.string.pictogram_restroom),
        "men" to (R.drawable.pictogram_men to R.string.pictogram_men),
        "women" to (R.drawable.pictogram_women to R.string.pictogram_women),
        "accessible" to (R.drawable.pictogram_accessible to R.string.pictogram_accessible),
        "exit" to (R.drawable.pictogram_exit to R.string.pictogram_exit),
        "elevator" to (R.drawable.pictogram_elevator to R.string.pictogram_elevator),
        "escalator" to (R.drawable.pictogram_escalator to R.string.pictogram_escalator),
        "stairs" to (R.drawable.pictogram_stairs to R.string.pictogram_stairs),
        "subway" to (R.drawable.pictogram_subway to R.string.pictogram_subway),
        "bus" to (R.drawable.pictogram_bus to R.string.pictogram_bus),
        "noSmoking" to (R.drawable.pictogram_no_smoking to R.string.pictogram_no_smoking),
        "noPhoto" to (R.drawable.pictogram_no_photo to R.string.pictogram_no_photo),
        "wifi" to (R.drawable.pictogram_wifi to R.string.pictogram_wifi),
        "pharmacy" to (R.drawable.pictogram_pharmacy to R.string.pictogram_pharmacy),
        "nursing" to (R.drawable.pictogram_nursing to R.string.pictogram_nursing),
        "babyChange" to (R.drawable.pictogram_baby_change to R.string.pictogram_baby_change),
        "info" to (R.drawable.pictogram_info to R.string.pictogram_info),
        "atm" to (R.drawable.pictogram_atm to R.string.pictogram_atm),
        "taxi" to (R.drawable.pictogram_taxi to R.string.pictogram_taxi),
        "parking" to (R.drawable.pictogram_parking to R.string.pictogram_parking),
        "airport" to (R.drawable.pictogram_airport to R.string.pictogram_airport),
        "lockers" to (R.drawable.pictogram_lockers to R.string.pictogram_lockers),
        "wasteBin" to (R.drawable.pictogram_waste_bin to R.string.pictogram_waste_bin),
        "drinkingWater" to (R.drawable.pictogram_drinking_water to R.string.pictogram_drinking_water),
        "noEntry" to (R.drawable.pictogram_no_entry to R.string.pictogram_no_entry),
        "caution" to (R.drawable.pictogram_caution to R.string.pictogram_caution),
        "fireExtinguisher" to (R.drawable.pictogram_fire_extinguisher to R.string.pictogram_fire_extinguisher),
        "phoneCharging" to (R.drawable.pictogram_phone_charging to R.string.pictogram_phone_charging),
        "smokingRoom" to (R.drawable.pictogram_smoking_room to R.string.pictogram_smoking_room),
        "noPets" to (R.drawable.pictogram_no_pets to R.string.pictogram_no_pets),
        "noFood" to (R.drawable.pictogram_no_food to R.string.pictogram_no_food),
        "recycling" to (R.drawable.pictogram_recycling to R.string.pictogram_recycling),
        "crossing" to (R.drawable.pictogram_crossing to R.string.pictogram_crossing),
        "police" to (R.drawable.pictogram_police to R.string.pictogram_police),
        "transfer" to (R.drawable.pictogram_transfer to R.string.pictogram_transfer),
    )

    @DrawableRes
    fun drawable(id: PictogramId): Int? = art[id.value]?.first

    @StringRes
    fun label(id: PictogramId): Int? = art[id.value]?.second

    val drawnIds: Set<String> get() = art.keys
}

/** 콘텐츠 팩이 #rrggbb 를 보장한다(파서 검사). 그래도 반투명이면 흰 글자 대비가 무너지므로 불투명으로 맞춘다. */
private val FallbackPlate = Color(0xFF2F6FB0)

private fun parsePlate(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)).copy(alpha = 1f) }.getOrDefault(FallbackPlate)

/**
 * 카드를 공공 안내 표지판 틀로 그린다 (PRD §8.3, v2.6, 설계서 §5.4).
 * 앞면은 [PlateFront] 로만 그린다. 앞면 함수는 픽토그램 값을 받지도 않아서 그림을 그릴 경로 자체가 없다(U-18):
 * 그림으로 뜻을 맞히지 않게. 뒷면은 그림이 있는 카드만 표지판 안에 픽토그램을 넣는다.
 */
@Composable
fun SignPlate(card: SignCard, face: PlateFace, modifier: Modifier = Modifier, size: PlateSize = PlateSize.Regular) {
    when (face) {
        PlateFace.Front -> PlateFront(card.koText, card.plateColor, modifier, size)
        PlateFace.Back -> PlateBack(card, modifier)
    }
}

@Composable
private fun PlateFront(koText: String, plateColor: String, modifier: Modifier, size: PlateSize) {
    PlateFrame(plateColor, stringResource(R.string.plate_description, koText), drawnPictogram = null, modifier, size) {
        if (size == PlateSize.Mini) PlateWord(koText, 18.sp, singleLine = true) else PlateWord(koText, 34.sp)
    }
}

@Composable
private fun PlateBack(card: SignCard, modifier: Modifier) {
    val art = card.pictogram?.let { id -> PictogramArt.drawable(id)?.let { id to it } }
    val description = when (art) {
        null -> stringResource(R.string.plate_description, card.koText)
        else -> stringResource(R.string.plate_description_pictogram, card.koText, stringResource(PictogramArt.label(art.first)!!))
    }
    PlateFrame(card.plateColor, description, drawnPictogram = art?.first, modifier) {
        if (art != null) {
            Icon(
                painter = painterResource(art.second),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(width = 104.dp, height = 86.dp),
            )
        }
        PlateWord(card.koText, 28.sp)
    }
}

/** 표지판 판. 스크린리더에는 설명 하나로 보인다. */
@Composable
private fun PlateFrame(
    plateColor: String,
    description: String,
    drawnPictogram: PictogramId?,
    modifier: Modifier,
    size: PlateSize = PlateSize.Regular,
    content: @Composable () -> Unit,
) {
    val plate = remember(plateColor) { parsePlate(plateColor) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .then(if (size == PlateSize.Mini) Modifier else Modifier.widthIn(min = 160.dp, max = 260.dp))
            .shadow(6.dp, shape)
            .clip(shape)
            .background(plate)
            .padding(5.dp)
            .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(9.dp))
            .padding(horizontal = if (size == PlateSize.Mini) 8.dp else 18.dp, vertical = if (size == PlateSize.Mini) 10.dp else 16.dp)
            .testTag(SignPlateTags.PLATE)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
                if (drawnPictogram != null) this.drawnPictogram = drawnPictogram.value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun PlateWord(koText: String, size: TextUnit, singleLine: Boolean = false) {
    KoreanText(
        text = koText,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        style = MaterialTheme.typography.headlineSmall.copy(
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = size,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
