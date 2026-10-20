package app.signkorean.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.common.LocalizedText
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PictogramId
import app.signkorean.domain.content.SignCard

/**
 * 디자인 시스템 부품 모음. 디버그 빌드에만 있다. 터치 영역 검사(U-10)와 스크린샷 테스트가 이 화면을 쓴다.
 * 출시 빌드에 들어가지 않으므로 견본 문구를 리소스로 빼지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSystemCatalog() {
    val en = LocaleTag("en")
    fun card(id: String, word: String, color: String, pictogram: String?) = SignCard(
        id = CardId(id), kind = CardKind.Basic("restroom"), koText = word, romanization = "r",
        reading = LocalizedText.EMPTY, meaning = LocalizedText(mapOf(en to "m")), note = LocalizedText(mapOf(en to "n")),
        pictogram = pictogram?.let(::PictogramId), plateColor = color, audio = null, reviewed = true,
    )
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.background(t.paper).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SignPlate(card("b01", "화장실", "#2f6fb0", "restroom"), PlateFace.Front)
            SignPlate(card("b01", "화장실", "#2f6fb0", "restroom"), PlateFace.Back)
            SignPlate(card("b60", "금연", "#c0392b", "noSmoking"), PlateFace.Back)
            SignPlate(card("b40", "비상구", "#2e7d4f", "exit"), PlateFace.Back)
            SignPlate(card("b03", "장애인 화장실", "#2f6fb0", null), PlateFace.Front)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Start", onClick = {})
            SecondaryButton("Later", onClick = {})
            QuietButton("Skip", onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge("Known", StatusTone.Known)
            StatusBadge("Learn again", StatusTone.Learning)
            StatusBadge("New", StatusTone.Neutral)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip("All cards", selected = true, onClick = {})
            AppChip("Review", selected = false, onClick = {})
        }
        LockBadge(feature = "Trip planner", onClick = {})
        LoadingSkeleton()
        EmptyState(title = "No cards yet", body = "This area has no signs to learn.") { SecondaryButton("Back to Explore", onClick = {}) }
        ErrorState(message = "Couldn't save changes. Try again.", onRetry = {})
        OfflineBanner()
        UndoBar(message = "Saved.", onUndo = {})
    }
}
