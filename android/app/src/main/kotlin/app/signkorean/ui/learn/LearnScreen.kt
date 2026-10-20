package app.signkorean.ui.learn

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.learning.Face
import app.signkorean.domain.learning.Verdict
import app.signkorean.domain.locale.LanguagePolicy
import app.signkorean.ui.designsystem.EmptyState
import app.signkorean.ui.designsystem.ErrorState
import app.signkorean.ui.designsystem.MinTouch
import app.signkorean.ui.designsystem.PlateFace
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.designsystem.SignPlate
import app.signkorean.ui.designsystem.StatusBadge
import app.signkorean.ui.designsystem.StatusTone
import app.signkorean.ui.text.KoreanText
import app.signkorean.ui.text.asString
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.koin.androidx.compose.koinViewModel

object LearnTags {
    const val CARD = "learn-card"
    const val STAMP = "learn-stamp"
    const val BUTTON_KNOW = "learn-know"
    const val BUTTON_DONT_KNOW = "learn-dont-know"
    const val BUTTON_PLACES = "learn-places"
    const val LEGEND_PLACES = "legend-places"
}

/** SC04 Learn 화면. ViewModel 을 받아 상태와 효과를 화면 이동으로 잇는다. */
@Composable
fun LearnScreen(
    onClose: () -> Unit,
    onOpenPlaces: (CardId) -> Unit,
    onFinished: () -> Unit,
    viewModel: LearnViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                is LearnEffect.OpenPlaces -> onOpenPlaces(effect.card)
                LearnEffect.Finished -> onFinished()
            }
        }
    }
    LearnContent(state, viewModel::onAction, onClose, reduceMotion = rememberReduceMotion())
}

/** 기기 설정의 "애니메이션 없음"(애니메이터 길이 0). 켜져 있으면 카드를 날리지 않고 바로 처리한다 (U-05). */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** 상태만 받아 그리는 Learn 화면. 테스트는 이 함수를 직접 쓴다. */
@Composable
fun LearnContent(state: LearnUiState, onAction: (LearnAction) -> Unit, onClose: () -> Unit, reduceMotion: Boolean) {
    val t = SignKoreanTheme.tokens
    // 날아가는 카드가 화면 밖으로 나가도 가로 스크롤이 생기지 않게 화면에서 잘라 낸다 (U-06, PRE-01).
    Column(Modifier.fillMaxSize().background(t.paper).clipToBounds()) {
        LearnHeader(state, onAction, onClose)
        when {
            state.loading -> Unit
            state.noSession -> EmptyState(
                title = stringResource(R.string.learn_no_session_title),
                body = stringResource(R.string.learn_no_session_body),
            ) { SecondaryButton(stringResource(R.string.learn_back_to_explore), onClick = onClose) }
            state.card != null -> CardArea(state, onAction, reduceMotion)
        }
    }
}

@Composable
private fun LearnHeader(state: LearnUiState, onAction: (LearnAction) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.learn_close))
        }
        Text(
            stringResource(R.string.learn_progress, state.decisions, state.maxDecisions),
            style = MaterialTheme.typography.labelMedium,
            color = SignKoreanTheme.tokens.muted,
            modifier = Modifier.weight(1f),
        )
        if (state.canUndo) {
            androidx.compose.material3.TextButton(
                onClick = { onAction(LearnAction.Undo) },
                enabled = !state.saving,
                modifier = Modifier.height(MinTouch),
            ) { Text(stringResource(R.string.common_undo)) }
        }
    }
}

@Composable
private fun CardArea(state: LearnUiState, onAction: (LearnAction) -> Unit, reduceMotion: Boolean) {
    val card = state.card ?: return
    val scope = rememberCoroutineScope()
    val offset = remember(card.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val focus = remember { FocusRequester() }

    /** 스와이프·버튼·키보드가 모두 같은 길로 간다: 날아가는 움직임 뒤 판정 (UX12). */
    fun commit(gesture: Gesture) {
        if (state.saving) return
        when (gesture) {
            Gesture.Know, Gesture.DontKnow -> scope.launch {
                val dir = if (gesture == Gesture.Know) 1f else -1f
                if (!reduceMotion) offset.animateTo(Offset(dir * size.width * 1.5f, 0f), tween(220)) else offset.snapTo(Offset.Zero)
                onAction(LearnAction.Judge(if (gesture == Gesture.Know) Verdict.Know else Verdict.DontKnow))
            }
            Gesture.Places -> scope.launch {
                if (!reduceMotion) offset.animateTo(Offset.Zero, tween(160)) else offset.snapTo(Offset.Zero)
                onAction(LearnAction.OpenPlaces)
            }
            Gesture.Flip -> onAction(LearnAction.Flip)
            Gesture.None -> scope.launch {
                if (!reduceMotion) offset.animateTo(Offset.Zero, tween(200)) else offset.snapTo(Offset.Zero)
            }
        }
    }

    LaunchedEffect(card.id) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Legend(state.hasPlaces)
        if (state.isTryAgain) StatusBadge(stringResource(R.string.learn_try_again), StatusTone.Learning)
        state.error?.let { ErrorState(it.asString(), onRetry = { onAction(LearnAction.Retry) }) }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val drag = Drag(offset.value.x, offset.value.y)
            val cardSize = CardSize(size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
            val hint = GestureClassifier.hint(drag, cardSize, state.hasPlaces)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SignKoreanTheme.tokens.surface,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size = it }
                    .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                    .rotate(if (size.width > 0) offset.value.x / size.width * 12f else 0f)
                    .testTag(LearnTags.CARD)
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionRight -> commit(Gesture.Know)
                            Key.DirectionLeft -> commit(Gesture.DontKnow)
                            Key.DirectionDown -> if (state.hasPlaces) commit(Gesture.Places)
                            Key.Enter, Key.Spacebar -> commit(Gesture.Flip)
                            else -> return@onKeyEvent false
                        }
                        true
                    }
                    .pointerInput(card.id, state.hasPlaces, state.saving) {
                        detectDragGestures(
                            onDragEnd = {
                                // 카드 위 버튼(북마크·음성, Phase 10·11)은 스스로 터치를 소비해 여기까지 오지 않는다.
                                val g = GestureClassifier.classify(Drag(offset.value.x, offset.value.y), cardSize, state.hasPlaces, startedOnControl = false)
                                commit(if (g == Gesture.Flip) Gesture.None else g)
                            },
                            onDragCancel = { scope.launch { offset.snapTo(Offset.Zero) } },
                        ) { change, amount ->
                            if (state.saving) return@detectDragGestures
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + amount) }
                        }
                    }
                    .clickable(enabled = !state.saving, onClickLabel = null) { commit(Gesture.Flip) },
            ) {
                CardFace(card, state.face)
            }
            Stamp(hint)
        }

        JudgeButtons(state, ::commit)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CardFace(card: SignCard, face: Face) {
    val chain = LanguagePolicy.fallbackChain(LocaleTag(stringResource(R.string.format_locale)))
    // 앞면은 스크롤하지 않는다: 스크롤이 세로 끌기를 가져가 아래(장소) 스와이프가 먹히지 않는다.
    // 뒷면 본문은 길 수 있어(200% 글자) 스크롤하며, PRD UX01 도 본문에서 시작한 끌기는 판정에서 뺀다.
    val scroll = if (face == Face.Back) Modifier.verticalScroll(rememberScrollState()) else Modifier
    Column(
        Modifier.fillMaxSize().then(scroll).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        if (face == Face.Front) {
            SignPlate(card, PlateFace.Front)
            Text(stringResource(R.string.learn_tap_hint), style = MaterialTheme.typography.labelMedium, color = SignKoreanTheme.tokens.muted)
        } else {
            SignPlate(card, PlateFace.Back)
            Text(card.romanization, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = SignKoreanTheme.tokens.muted)
            card.meaning.resolve(chain)?.let {
                Text(it, style = MaterialTheme.typography.headlineSmall, color = SignKoreanTheme.tokens.primary, textAlign = TextAlign.Center)
            }
            card.note.resolve(chain)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Text(stringResource(R.string.learn_tap_back), style = MaterialTheme.typography.labelMedium, color = SignKoreanTheme.tokens.muted)
        }
    }
}

/** 카드 위 범례: ← 몰라요 · ↓ 장소 · 알아요 → (UX11 ①). 장소 없는 카드는 아래를 뺀다. */
@Composable
private fun Legend(hasPlaces: Boolean) {
    val t = SignKoreanTheme.tokens
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        LegendItem("← " + stringResource(R.string.learn_dont_know), stringResource(R.string.ko_dont_know), t.accentInk)
        if (hasPlaces) {
            Box(Modifier.testTag(LearnTags.LEGEND_PLACES)) {
                LegendItem("↓ " + stringResource(R.string.learn_places), stringResource(R.string.ko_places), t.primary)
            }
        }
        LegendItem(stringResource(R.string.learn_know) + " →", stringResource(R.string.ko_know), t.knowInk)
    }
}

@Composable
private fun LegendItem(label: String, korean: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        KoreanText(korean, style = MaterialTheme.typography.labelMedium.copy(color = color))
    }
}

/** 끄는 동안 거리에 비례해 진해지는 도장 (UX11 ②). */
@Composable
private fun Stamp(hint: DragHint) {
    if (hint.direction == Gesture.None || hint.strength <= 0f) return
    val t = SignKoreanTheme.tokens
    val (label, korean, color) = when (hint.direction) {
        Gesture.Know -> Triple(stringResource(R.string.learn_know), stringResource(R.string.ko_know), t.knowInk)
        Gesture.DontKnow -> Triple(stringResource(R.string.learn_dont_know), stringResource(R.string.ko_dont_know), t.accentInk)
        else -> Triple(stringResource(R.string.learn_places), stringResource(R.string.ko_places), t.primary)
    }
    Column(
        Modifier.alpha(hint.strength).border(3.dp, color, RoundedCornerShape(10.dp)).background(t.surface.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp).testTag(LearnTags.STAMP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        KoreanText(korean, style = MaterialTheme.typography.titleMedium.copy(color = color, fontWeight = FontWeight.Bold))
    }
}

/** 원형 버튼 ✕ ↓ ✓. 스와이프와 같은 길로 판정한다 (U-02). 몰라요는 연한 주황, 알아요는 초록 채움 (설계서 §5.4). */
@Composable
private fun JudgeButtons(state: LearnUiState, commit: (Gesture) -> Unit) {
    val t = SignKoreanTheme.tokens
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
        RoundButton("✕", stringResource(R.string.learn_dont_know), t.accentSoft, t.accentInk, t.accentInk, !state.saving, LearnTags.BUTTON_DONT_KNOW) { commit(Gesture.DontKnow) }
        if (state.hasPlaces) {
            RoundButton("↓", stringResource(R.string.learn_places), t.surface, t.primary, t.primary, !state.saving, LearnTags.BUTTON_PLACES) { commit(Gesture.Places) }
        }
        RoundButton("✓", stringResource(R.string.learn_know), t.know, Color.White, t.knowInk, !state.saving, LearnTags.BUTTON_KNOW) { commit(Gesture.Know) }
    }
}

@Composable
private fun RoundButton(
    symbol: String,
    label: String,
    fill: Color,
    symbolColor: Color,
    labelColor: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button }
            .testTag(tag)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(60.dp).background(fill, CircleShape).border(1.dp, labelColor.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
            Text(symbol, color = symbolColor, fontSize = 22.sp)
        }
        Text(label, color = labelColor, style = MaterialTheme.typography.labelMedium)
    }
}
