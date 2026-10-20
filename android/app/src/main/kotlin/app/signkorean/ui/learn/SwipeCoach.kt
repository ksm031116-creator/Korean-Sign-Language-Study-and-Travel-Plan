package app.signkorean.ui.learn

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.signkorean.R
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.KoreanText

object CoachTags {
    const val COACH = "swipe-coach"
    const val DEMO = "swipe-coach-demo"
}

private enum class CoachStep { Right, Left, Down, Tap }

/** 애니메이션 검사 도구에만 보이는 이름. 사용자 문구가 아니다. */
private const val ANIMATION_NAME = "swipe-coach"

/**
 * 첫 진입 스와이프 시연 (UX11 ③, U-07). 작은 카드가 오른쪽·왼쪽·아래로 움직이고 탭에 뒤집히며,
 * 그 순간 해당 설명을 강조한다. 움직임 줄이기에서는 멈춘 그림과 설명만 보인다. Got it 또는 Back 으로 닫는다.
 * 시연 카드는 처음부터 설명 상자 안에 있다(PRE-02 회귀: 화면 밖에서 들어오지 않는다).
 */
@Composable
fun SwipeCoach(hasPlaces: Boolean, reduceMotion: Boolean, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val t = SignKoreanTheme.tokens
    val steps = CoachStep.entries.filter { hasPlaces || it != CoachStep.Down }
    val phase = if (reduceMotion) {
        -1f
    } else {
        rememberInfiniteTransition(label = ANIMATION_NAME).animateFloat(
            initialValue = 0f,
            targetValue = steps.size.toFloat(),
            animationSpec = infiniteRepeatable(tween(steps.size * 1600, easing = LinearEasing), RepeatMode.Restart),
            label = ANIMATION_NAME,
        ).value
    }
    val active = if (phase < 0) null else steps[phase.toInt().coerceIn(0, steps.lastIndex)]
    val within = if (phase < 0) 0f else (phase % 1f)
    // 한 단계 안에서 0 → 1 → 0 으로 움직였다 돌아온다.
    val swing = if (within < 0.5f) within * 2 else (1 - within) * 2

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).testTag(CoachTags.COACH)
            // 어두운 배경을 눌러도 아래 카드·버튼으로 터치가 새지 않게 모두 받는다.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .semantics { isTraversalGroup = true },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = t.surface, modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                    Text(stringResource(R.string.coach_title) + " · ", style = MaterialTheme.typography.titleMedium)
                    KoreanText(stringResource(R.string.ko_coach_title), style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.height(96.dp).fillMaxWidth().testTag(CoachTags.DEMO), contentAlignment = Alignment.Center) {
                    val dx = when (active) {
                        CoachStep.Right -> 56f * swing
                        CoachStep.Left -> -56f * swing
                        else -> 0f
                    }
                    val dy = if (active == CoachStep.Down) 28f * swing else 0f
                    val flipped = active == CoachStep.Tap && within in 0.25f..0.75f
                    Box(
                        Modifier.offset(dx.dp, dy.dp).rotate(dx / 8f).size(width = 72.dp, height = 88.dp)
                            .background(if (flipped) t.primarySoft else t.paper, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        KoreanText(stringResource(R.string.ko_coach_sample), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
                steps.forEach { step ->
                    val (arrow, label, ko) = when (step) {
                        CoachStep.Right -> Triple("→", R.string.coach_right, R.string.ko_know)
                        CoachStep.Left -> Triple("←", R.string.coach_left, R.string.ko_dont_know)
                        CoachStep.Down -> Triple("↓", R.string.coach_down, R.string.ko_places)
                        CoachStep.Tap -> Triple("●", R.string.coach_tap, R.string.ko_coach_meaning)
                    }
                    val on = step == active
                    Row(
                        Modifier.fillMaxWidth().background(if (on) t.primarySoft else Color.Transparent, RoundedCornerShape(8.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(arrow, style = MaterialTheme.typography.titleMedium, color = t.primary, modifier = Modifier.width(28.dp))
                        Text(stringResource(label) + " · ", style = MaterialTheme.typography.bodyMedium, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        KoreanText(stringResource(ko), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(stringResource(R.string.coach_note), style = MaterialTheme.typography.labelMedium, color = t.muted)
                PrimaryButton(stringResource(R.string.coach_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
