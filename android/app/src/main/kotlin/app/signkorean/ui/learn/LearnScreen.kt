package app.signkorean.ui.learn

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import androidx.compose.runtime.key
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
    const val ANNOUNCEMENT = "learn-announcement"
    const val AUDIO = "learn-audio"
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
    // 화면을 떠나거나 앱이 뒤로 가면 발음을 멈춘다.
    LifecycleResumeEffect(viewModel) {
        onPauseOrDispose { viewModel.onAction(LearnAction.StopAudio) }
    }
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

/**
 * 기기 설정의 "애니메이션 없음"(애니메이터 길이 0). 켜져 있으면 카드를 날리지 않고 바로 처리한다 (U-05).
 * 화면이 다시 보일 때마다 다시 읽어, 설정을 바꾸고 돌아와도 따른다.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduce by remember(resolver) { mutableStateOf(read()) }
    LifecycleResumeEffect(resolver) {
        reduce = read()
        onPauseOrDispose { }
    }
    return reduce
}

/** 상태만 받아 그리는 Learn 화면. 테스트는 이 함수를 직접 쓴다. */
@Composable
fun LearnContent(state: LearnUiState, onAction: (LearnAction) -> Unit, onClose: () -> Unit, reduceMotion: Boolean) {
    val t = SignKoreanTheme.tokens
    // 날아가는 카드가 화면 밖으로 나가도 가로 스크롤이 생기지 않게 화면에서 잘라 낸다 (U-06, PRE-01).
    Box(Modifier.fillMaxSize()) {
    // 시연이 떠 있는 동안 뒤 화면은 스크린리더에서 숨긴다(모달).
    val behindCoach = if (state.showCoach) Modifier.clearAndSetSemantics { } else Modifier
    Column(Modifier.fillMaxSize().background(t.paper).clipToBounds().then(behindCoach)) {
        LearnHeader(state, onAction, onClose)
        when {
            state.loading -> Unit
            state.noSession -> EmptyState(
                title = stringResource(R.string.learn_no_session_title),
                body = stringResource(R.string.learn_no_session_body),
            ) { SecondaryButton(stringResource(R.string.learn_back_to_explore), onClick = onClose) }
            state.loadFailed -> ErrorState(stringResource(R.string.learn_load_failed), onRetry = { onAction(LearnAction.Retry) }, modifier = Modifier.padding(20.dp))
            state.card != null -> CardArea(state, onAction, reduceMotion)
        }
    }
    // 판정 결과를 스크린리더가 읽도록 알림 영역에 싣는다 (U-03). 화면에는 보이지 않는다.
    state.announcement?.let { text ->
        val spoken = text.asString()
        // 판정마다 새 노드로 만든다. 같은 문구가 이어져도("알아요" 두 번) 다시 읽힌다.
        key(state.seq) {
        Box(
            Modifier.size(1.dp).testTag(LearnTags.ANNOUNCEMENT).semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
        )
        }
    }
    if (state.showCoach && state.card != null) {
        SwipeCoach(hasPlaces = state.hasPlaces, reduceMotion = reduceMotion, onDismiss = { onAction(LearnAction.DismissCoach) })
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
    // 같은 카드가 다시 나와도(재출제·Undo·저장 실패) 순번이 바뀌면 새 위치에서 시작한다. 날아간 채 남지 않게.
    val offset = remember(card.id, state.seq) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val focus = remember { FocusRequester() }
    // 날아가는 동안(판정을 보내기 전)에는 다른 입력을 받지 않는다. 두 번째 입력이 첫 판정을 덮지 않게.
    var committing by remember(card.id, state.seq) { mutableStateOf(false) }

    // 저장이 실패하면 같은 카드에 머문다(FR07). 날아간 카드를 제자리로 돌린다.
    LaunchedEffect(state.error) {
        if (state.error != null) {
            committing = false
            offset.snapTo(Offset.Zero)
        }
    }

    /** 스와이프·버튼·키보드가 모두 같은 길로 간다: 날아가는 움직임 뒤 판정 (UX12). */
    fun commit(gesture: Gesture) {
        if (state.saving || committing) return
        when (gesture) {
            Gesture.Know, Gesture.DontKnow -> {
                committing = true
                scope.launch {
                    val dir = if (gesture == Gesture.Know) 1f else -1f
                    if (!reduceMotion) offset.animateTo(Offset(dir * size.width * 1.5f, 0f), tween(220)) else offset.snapTo(Offset.Zero)
                    onAction(LearnAction.Judge(if (gesture == Gesture.Know) Verdict.Know else Verdict.DontKnow))
                }
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

    // 지역 함수 참조(::commit)는 구성마다 "같은 값"으로 비교돼 갱신되지 않는다. 새 람다로 넘겨 최신 상태를 쓰게 한다.
    val currentCommit by rememberUpdatedState<(Gesture) -> Unit> { g -> commit(g) }
    val cardSize = CardSize(size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
    val currentSize by rememberUpdatedState(cardSize)
    val hasPlaces by rememberUpdatedState(state.hasPlaces)
    val flipLabel = stringResource(R.string.learn_flip_action)
    val faceDescription = stringResource(if (state.face == Face.Front) R.string.learn_face_front else R.string.learn_face_back)
    val cardDescription = stringResource(
        if (state.hasPlaces) R.string.learn_card_description_places else R.string.learn_card_description,
        card.koText,
    )

    // 새 카드가 나오거나 시연을 닫으면 초점을 카드로 돌린다(UX11 ③).
    LaunchedEffect(card.id, state.showCoach) { if (!state.showCoach) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Legend(state.hasPlaces)
        if (state.isTryAgain) StatusBadge(stringResource(R.string.learn_try_again), StatusTone.Learning)
        state.error?.let { ErrorState(it.asString(), onRetry = { onAction(LearnAction.Retry) }) }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // 뒤 카드 겹침 (UX12): 앞 카드를 끌수록 뒤 카드가 앞으로 올라온다.
            val pull = (kotlin.math.abs(offset.value.x) / cardSize.width).coerceIn(0f, 1f)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SignKoreanTheme.tokens.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, SignKoreanTheme.tokens.line),
                modifier = Modifier.fillMaxSize().padding(horizontal = (10 * (1 - pull)).dp).offset(y = (14 * (1 - pull)).dp),
            ) {}
            val hint = GestureClassifier.hint(Drag(offset.value.x, offset.value.y), cardSize, state.hasPlaces)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SignKoreanTheme.tokens.surface,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size = it }
                    // 터치는 움직이기 전 좌표에서 받는다(기울어진 좌표계로 끌기 거리가 비틀리지 않게).
                    .pointerInput(card.id, state.seq) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            var total = Offset.Zero
                            var cancelled = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                // 카드 안 스크롤(뒷면 본문)이 가져간 끌기는 판정하지 않는다(PRD UX01: 본문 제외).
                                if (change.isConsumed) {
                                    cancelled = true
                                    break
                                }
                                total += change.positionChange()
                                if (total.getDistance() >= GestureClassifier.TAP_SLOP_PX && !committing) {
                                    change.consume()
                                    scope.launch { offset.snapTo(total) }
                                }
                            }
                            if (cancelled || committing) {
                                currentCommit(Gesture.None)
                                return@awaitEachGesture
                            }
                            // 탭과 끌기를 같은 거리 기준으로 나눈다: 8px 미만이면 뒤집기 (G-08·10).
                            currentCommit(GestureClassifier.classify(Drag(total.x, total.y), currentSize, hasPlaces, startedOnControl = false))
                        }
                    }
                    .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
                    .rotate(if (size.width > 0) offset.value.x / size.width * 12f else 0f)
                    .testTag(LearnTags.CARD)
                    .semantics {
                        contentDescription = cardDescription
                        stateDescription = faceDescription
                        onClick(label = flipLabel) {
                            currentCommit(Gesture.Flip)
                            true
                        }
                    }
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        // 길게 누른 키의 반복은 무시한다. 한 번 누름 = 판정 한 번.
                        if (e.nativeKeyEvent.repeatCount > 0) return@onKeyEvent true
                        when (e.key) {
                            Key.DirectionRight -> currentCommit(Gesture.Know)
                            Key.DirectionLeft -> currentCommit(Gesture.DontKnow)
                            Key.DirectionDown -> if (hasPlaces) currentCommit(Gesture.Places)
                            Key.Enter, Key.Spacebar -> currentCommit(Gesture.Flip)
                            else -> return@onKeyEvent false
                        }
                        true
                    },
            ) {
                CardFace(card, state.face, state.audio) { onAction(LearnAction.PlayAudio) }
            }
            Stamp(hint)
        }

        JudgeButtons(state.copy(saving = state.saving || committing), ::commit)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CardFace(card: SignCard, face: Face, audio: AudioUi, onPlay: () -> Unit) {
    val language = LocaleTag(stringResource(R.string.format_locale))
    val chain = LanguagePolicy.fallbackChain(language)
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        if (face == Face.Front) {
            SignPlate(card, PlateFace.Front)
            Text(stringResource(R.string.learn_tap_hint), style = MaterialTheme.typography.labelMedium, color = SignKoreanTheme.tokens.muted)
        } else {
            // 표지판 판에서 시작한 끌기는 판정에 쓰고, 긴 본문(200% 글자)만 따로 스크롤한다(PRD UX01: 본문 제외).
            SignPlate(card, PlateFace.Back)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 발음 보조 (설계서 §8.4): en 은 로마자, ja 는 카타카나 + 로마자. 카타카나는 근사 표기라 "目安" 을 붙인다.
                if (language != LanguagePolicy.BASE) {
                    card.reading[language]?.let { reading ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(reading, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.reading_hint), style = MaterialTheme.typography.labelMedium, color = SignKoreanTheme.tokens.muted)
                        }
                    }
                }
                Text(card.romanization, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = SignKoreanTheme.tokens.muted)
                AudioButton(audio, onPlay)
                card.meaning.resolve(chain)?.let {
                    Text(it, style = MaterialTheme.typography.headlineSmall, color = SignKoreanTheme.tokens.primary, textAlign = TextAlign.Center)
                }
                card.note.resolve(chain)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
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
            .testTag(tag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            // 기호(✕ ✓)는 읽지 않고 이름만 읽는다.
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                if (!enabled) disabled()
                onClick(label = label) { if (enabled) onClick(); enabled }
            }
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

/**
 * 발음 버튼 (FR29, UX10). 아이콘과 글자로 상태를 함께 보인다. 음성이 없으면 "Audio unavailable" 로 바뀌고
 * 누를 수 없다(U-12). 카드 위 버튼이라 누름은 버튼이 가져가 카드 스와이프로 번지지 않는다(G-09).
 */
@Composable
private fun AudioButton(audio: AudioUi, onPlay: () -> Unit) {
    val t = SignKoreanTheme.tokens
    val (symbol, label) = when (audio) {
        AudioUi.Ready -> "▶" to stringResource(R.string.audio_listen)
        AudioUi.Playing -> "■" to stringResource(R.string.audio_playing)
        AudioUi.Unavailable -> "–" to stringResource(R.string.audio_unavailable)
    }
    androidx.compose.material3.OutlinedButton(
        onClick = onPlay,
        enabled = audio != AudioUi.Unavailable,
        modifier = Modifier.height(MinTouch).testTag(LearnTags.AUDIO),
        border = androidx.compose.foundation.BorderStroke(1.dp, t.outlineStrong),
    ) {
        Text(symbol, modifier = Modifier.semantics { invisibleToUser() })
        Spacer(Modifier.size(8.dp))
        Text(label)
    }
}
