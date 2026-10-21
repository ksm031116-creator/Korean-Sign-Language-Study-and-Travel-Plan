package app.signkorean.ui.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.bookmark.BookmarkPolicy
import app.signkorean.domain.bookmark.BookmarkStore
import app.signkorean.domain.common.AppScope
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.domain.entitlement.FailReason
import app.signkorean.domain.entitlement.PurchaseFlowState
import app.signkorean.domain.entitlement.RestoreResult
import app.signkorean.domain.upgrade.PendingActionStore
import app.signkorean.domain.upgrade.ResumePendingAction
import app.signkorean.ui.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** SC21 을 연 진입점. 맨 위 안내 문구가 달라진다 (PRD §14.1). 화면 인자로 [arg] 를 쓴다. */
enum class UpgradeFeature(val arg: String) {
    /** 6번째 북마크에서 막혔다. "You've saved 5 of 5 signs." 를 보인다. */
    BookmarkLimit("bookmark-limit"),

    /** Saved 탭의 잠금 배지. 아직 한도 전일 수 있어 저장 수를 보이지 않는다(리뷰 UPG-14). */
    Bookmarks("bookmarks"),
    Plan("plan"),
    Route("route"),
    ;

    companion object {
        fun parse(arg: String?): UpgradeFeature = entries.firstOrNull { it.arg == arg } ?: Plan
    }
}

data class UpgradeUiState(
    val feature: UpgradeFeature,
    /** 북마크 한도에서 왔을 때만. "You've saved 5 of 5 signs." */
    val savedCount: Int? = null,
    val limit: Int = BookmarkPolicy.FREE_LIMIT,
    /** 스토어가 현지화한 가격 문자열. 그대로 보인다 (L10N-11). */
    val price: String? = null,
    val priceChecked: Boolean = false,
    /** 결제·복원 중. 버튼과 Back 을 막는다 (E-08, 리뷰 UPG-02). */
    val busy: Boolean = false,
    /** 방금 결제가 실패했다. Unlock 버튼이 Try again 이 된다 (E-06). 다음 시도가 시작되면 풀린다. */
    val failed: Boolean = false,
    val message: UiText? = null,
)

sealed interface UpgradeAction {
    data object Unlock : UpgradeAction
    data object Restore : UpgradeAction
    data object NotNow : UpgradeAction
}

sealed interface UpgradeEffect {
    /** 해제됐다. 토스트를 보이고 원래 화면으로 돌아간다. 보류 동작은 이미 수행했다. */
    data class Unlocked(val message: UiText) : UpgradeEffect

    /** 아무 안내 없이 원래 화면으로 (취소·Not now). */
    data object Close : UpgradeEffect
}

/**
 * SC21 업그레이드 안내 (PRD §14.1, FR23~27). 결제 상태는 [Entitlements] 하나가 갖고, 이 화면은 그것을 그린다.
 *
 * - 결제는 앱 범위([AppScope])에서 돈다. 화면이 닫혀도 결제 결과를 놓치지 않는다.
 * - 결제에 성공하면 보류 동작(6번째 북마크 등)을 먼저 수행한 뒤 닫는다.
 * - 결제 없이 떠나면 보류 동작을 버린다. 단, 대기 결제(편의점 결제 등)가 있으면 남긴다. 그 결제는 사용자가
 *   이 화면을 떠난 뒤에 끝나기 때문이다(리뷰 UPG-01).
 * - 취소·실패·오프라인 안내는 이 화면이 시작한 결제에만 반응한다.
 */
class UpgradeViewModel(
    private val feature: UpgradeFeature,
    private val entitlements: Entitlements,
    private val bookmarks: BookmarkStore,
    private val pending: PendingActionStore,
    private val resume: ResumePendingAction,
    private val app: AppScope,
) : ViewModel() {

    private val _state = MutableStateFlow(UpgradeUiState(feature))
    val state: StateFlow<UpgradeUiState> = _state.asStateFlow()

    private val _effects = Channel<UpgradeEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<UpgradeEffect> = _effects

    /** 복원 중에는 흐름의 Succeeded 를 복원이 직접 처리한다. 복원이 끝날 때 놓친 성공이 있으면 그때 처리한다. */
    private var restoring = false

    /** 이 화면에서 Unlock 을 눌렀다. 취소·실패·오프라인은 이때만 이 화면의 일이다. */
    private var requested = false
    private var finished = false

    init {
        // 지난번에 연 SC21 의 결과(실패·취소)가 새 화면에 남지 않게 한다. 결제 중이거나 대기 중이면 그대로 둔다.
        if (entitlements.purchaseFlow.value != PurchaseFlowState.Pending) entitlements.dismiss()
        viewModelScope.launch {
            if (entitlements.current() == Entitlement.Paid) {
                resume()
                finish(UpgradeEffect.Close)
                return@launch
            }
            if (feature == UpgradeFeature.BookmarkLimit) {
                val count = try {
                    bookmarks.count()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                _state.update { it.copy(savedCount = count) }
            }
            loadPrice()
        }
        viewModelScope.launch { entitlements.purchaseFlow.collect(::onFlow) }
    }

    fun onAction(action: UpgradeAction) {
        when (action) {
            UpgradeAction.Unlock -> {
                if (_state.value.busy || finished) return
                requested = true
                // 흐름이 InProgress 가 되기 전에도 바로 막는다. 그 사이 Restore·Back 이 끼어들지 않게(리뷰 UPG-04).
                _state.update { it.copy(busy = true, failed = false, message = null) }
                if (_state.value.price == null) viewModelScope.launch { loadPrice() }
                app.scope.launch { entitlements.unlock() }
            }
            UpgradeAction.Restore -> {
                if (_state.value.busy || finished) return
                restore()
            }
            // 결제·복원 중에는 떠나지 않는다. 결제 시트가 뜨기 전 잠깐이 있다(리뷰 UPG-02).
            UpgradeAction.NotNow -> if (!_state.value.busy) viewModelScope.launch {
                dropPending()
                // 대기 결제 표시는 남긴다. 다음에 연 SC21 과 보류 동작 정리가 대기 중임을 알아야 한다.
                if (entitlements.purchaseFlow.value != PurchaseFlowState.Pending) entitlements.dismiss()
                finish(UpgradeEffect.Close)
            }
        }
    }

    private suspend fun loadPrice() {
        val price = entitlements.localizedPrice()
        _state.update { it.copy(price = price ?: it.price, priceChecked = true) }
    }

    private fun restore() {
        restoring = true
        _state.update { it.copy(busy = true, failed = false, message = null) }
        viewModelScope.launch {
            try {
                when (entitlements.restore()) {
                    RestoreResult.Restored -> succeed(UiText.Res(R.string.upgrade_restored))
                    RestoreResult.NothingToRestore ->
                        _state.update { it.copy(message = UiText.Res(R.string.upgrade_restore_none)) }
                    RestoreResult.Failed ->
                        _state.update { it.copy(message = UiText.Res(R.string.upgrade_restore_failed)) }
                }
            } finally {
                restoring = false
                _state.update { it.copy(busy = entitlements.purchaseFlow.value == PurchaseFlowState.InProgress) }
                // 복원하는 동안 결제가 성공했으면(흐름은 같은 값을 다시 내지 않는다) 여기서 처리한다.
                if (entitlements.purchaseFlow.value == PurchaseFlowState.Succeeded) succeed(UiText.Res(R.string.upgrade_unlocked))
            }
        }
    }

    private fun onFlow(flow: PurchaseFlowState) {
        if (finished) return
        when (flow) {
            PurchaseFlowState.Idle -> Unit
            PurchaseFlowState.InProgress -> _state.update { it.copy(busy = true, failed = false, message = null) }
            PurchaseFlowState.Pending ->
                _state.update { it.copy(busy = false, failed = false, message = UiText.Res(R.string.upgrade_pending)) }
            PurchaseFlowState.Succeeded -> if (!restoring) viewModelScope.launch { succeed(UiText.Res(R.string.upgrade_unlocked)) }
            PurchaseFlowState.Cancelled -> if (requested) viewModelScope.launch {
                requested = false
                dropPending()
                entitlements.dismiss()
                finish(UpgradeEffect.Close)
            }
            is PurchaseFlowState.Failed -> if (requested) {
                requested = false
                _state.update { it.copy(busy = false, failed = true, message = UiText.Res(failText(flow.reason))) }
            }
            PurchaseFlowState.Offline -> if (requested) {
                requested = false
                _state.update { it.copy(busy = false, failed = false, message = UiText.Res(R.string.upgrade_offline)) }
            }
        }
    }

    private suspend fun succeed(message: UiText) {
        if (finished) return
        _state.update { it.copy(busy = false) }
        resume()
        entitlements.dismiss()
        finish(UpgradeEffect.Unlocked(message))
    }

    private fun failText(reason: FailReason): Int = when (reason) {
        FailReason.StoreUnavailable -> R.string.upgrade_failed_store
        FailReason.ProductUnavailable -> R.string.upgrade_failed_product
        FailReason.Unknown -> R.string.upgrade_failed
    }

    /**
     * 결제하지 않고 떠나면 보류 동작을 버린다. 나중에 다른 진입점에서 결제했을 때 뜻밖에 저장되지 않게.
     * 대기 결제가 있으면 남긴다. 결제가 끝나 Paid 가 되면 앱이 이어서 수행한다(리뷰 UPG-01).
     */
    private suspend fun dropPending() {
        if (entitlements.current() == Entitlement.Paid) return
        if (entitlements.purchaseFlow.value == PurchaseFlowState.Pending) return
        try {
            pending.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun finish(effect: UpgradeEffect) {
        if (finished) return
        finished = true
        _effects.send(effect)
    }
}
