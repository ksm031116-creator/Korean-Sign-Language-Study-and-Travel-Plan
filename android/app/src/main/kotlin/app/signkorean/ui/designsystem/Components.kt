package app.signkorean.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.signkorean.R

/** 터치 영역 최소값 (PRD UX06, U-10). */
val MinTouch = 48.dp

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = MinTouch, minHeight = MinTouch),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = MinTouch, minHeight = MinTouch),
        border = BorderStroke(1.dp, SignKoreanTheme.tokens.outlineStrong),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 학습 상태 배지. 색만으로 구별하지 않도록 글자를 항상 함께 쓴다 (NFR04). */
enum class StatusTone { Known, Learning, Neutral }

@Composable
fun StatusBadge(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val t = SignKoreanTheme.tokens
    val (bg, fg) = when (tone) {
        StatusTone.Known -> t.knowSoft to t.knowInk
        StatusTone.Learning -> t.accentSoft to t.accentInk
        StatusTone.Neutral -> t.primarySoft to t.primary
    }
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
fun AppChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SignKoreanTheme.tokens.primarySoft,
            selectedLabelColor = SignKoreanTheme.tokens.primary,
        ),
        // 칩의 그림 높이는 32dp 이고, 누르는 영역은 Material 이 48dp 로 넓힌다(U-10 은 누르는 영역으로 검사).
        modifier = modifier,
    )
}

object LockBadgeTags {
    const val LOCK_ICON = "lock-icon"
}

/**
 * 유료 잠금 표현 (PRD UX09, FR23). 비활성이 아니라 자물쇠 + 기능명으로 누를 수 있게 그리고, 누르면 SC21 로 간다.
 * Basics 의 미연결 기능(그리지 않음)과 같은 시각 언어를 쓰지 않는다.
 */
@Composable
fun LockBadge(feature: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = SignKoreanTheme.tokens
    val label = stringResource(R.string.lock_badge_description, feature)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = MinTouch).semantics { contentDescription = label },
        border = BorderStroke(1.dp, t.primary),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = t.primarySoft, contentColor = t.primary),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp).testTag(LockBadgeTags.LOCK_ICON))
        Spacer(Modifier.width(8.dp))
        Text(feature, style = MaterialTheme.typography.labelLarge)
    }
}

/** 외부 응답 대기 (UX03). 내용 모양의 회색 막대. 스크린리더에는 "불러오는 중" 하나로 알린다. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, rows: Int = 3) {
    val t = SignKoreanTheme.tokens
    val label = stringResource(R.string.common_loading)
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) { i ->
            Box(
                Modifier.fillMaxWidth(if (i % 2 == 0) 1f else 0.7f).height(18.dp)
                    .clip(RoundedCornerShape(6.dp)).background(t.line),
            )
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = SignKoreanTheme.tokens.muted)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/** 실패 시 같은 화면에서 다시 시도 (UX02·03). 스크린리더가 바로 읽도록 알림 영역으로 둔다. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val t = SignKoreanTheme.tokens
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(t.dangerSoft).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, color = t.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        SecondaryButton(stringResource(R.string.common_retry), onClick = onRetry)
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val t = SignKoreanTheme.tokens
    Surface(color = t.ink, contentColor = t.surface, modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.common_offline),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** SC20 공통 확인 모달: 세션 교체·폼 이탈·삭제·위치 권한. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val t = SignKoreanTheme.tokens
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)) {
                Text(confirm, color = if (destructive) t.danger else t.primary, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)) {
                Text(dismiss, style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** 되돌리기 버튼이 있는 알림 줄 (토스트 대신, 버튼까지 누를 수 있게). */
@Composable
fun UndoBar(message: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val t = SignKoreanTheme.tokens
    Surface(color = t.ink, contentColor = t.surface, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = onUndo, modifier = Modifier.defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)) {
                Text(stringResource(R.string.common_undo), color = t.primarySoft, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * 공통 하단 시트 (SC15·SC16·SC17). 시트 바깥을 누르면 Back 과 같다(PRD UX07): 닫기만 하고 아무것도 저장하지 않는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SignKoreanTheme.tokens.surface) {
        // 내용이 길거나 키보드가 올라와도 마지막 버튼까지 닿게 한다.
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}
