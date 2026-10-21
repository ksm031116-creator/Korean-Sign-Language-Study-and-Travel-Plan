package app.signkorean.ui.plan

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.ui.designsystem.LockBadge
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 디버그 빌드의 "다시 잠그기"(프로토타입의 relock). 모의 결제를 쓸 때만 조립된다(variantModules). 릴리스 조립에는
 * 없어 버튼이 그려지지 않는다(구현 계획 Phase 13 D3). 문구 debug_relock 은 main 리소스에 번역 없이 있다 —
 * src/debug/res/values 에 두면 "검수 전 언어만 디버그 리소스에" 규칙(ReleaseLanguagesTest)과 겹치기 때문이다.
 */
interface DebugRelock {
    /** 디버그 리소스의 버튼 문구. */
    @get:StringRes val label: Int
    suspend fun relock()
}

/** 권한이 Paid 로 확인되기 전(캐시를 읽기 전)에는 아무것도 그리지 않는다. 잠금이 잠깐 비쳤다 사라지지 않게. */
class PlanGateViewModel(private val entitlements: Entitlements, private val relock: DebugRelock?) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    val entitlement: StateFlow<Entitlement> = entitlements.entitlement
    @get:StringRes val relockLabel: Int? get() = relock?.label

    init {
        viewModelScope.launch {
            entitlements.current()
            _ready.value = true
        }
    }

    fun relock() {
        val r = relock ?: return
        viewModelScope.launch {
            r.relock()
            entitlements.refresh()
        }
    }
}

/** SC11 Plan 탭 루트. 무료면 잠금 안내(U-09), 유료면 여행 목록(Phase 17 에서 만든다). */
@Composable
fun PlanScreen(
    onUpgrade: () -> Unit,
    paidContent: @Composable () -> Unit,
    /** 무료 상태의 내용. 기본은 잠금 안내. 여행이 있으면 읽기 전용 목록을 보인다(E-12). */
    freeContent: @Composable () -> Unit = { PlanLockedContent(onUpgrade) },
    viewModel: PlanGateViewModel = koinViewModel(),
) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    if (!ready) return
    if (entitlement == Entitlement.Free) {
        freeContent()
    } else {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) { paidContent() }
            viewModel.relockLabel?.let { QuietButton(stringResource(it), onClick = viewModel::relock, modifier = Modifier.padding(16.dp)) }
        }
    }
}

/** 무료 상태의 Plan 탭. 기능을 숨기지 않고 자물쇠 + 기능명으로 보이며, 누르면 SC21 (FR23, UX09, U-09). */
@Composable
fun PlanLockedContent(onUpgrade: () -> Unit) {
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.plan_subtitle), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        Text(stringResource(R.string.plan_locked_body), style = MaterialTheme.typography.bodyLarge)
        LockBadge(stringResource(R.string.lock_trip_planning), onClick = onUpgrade)
    }
}
