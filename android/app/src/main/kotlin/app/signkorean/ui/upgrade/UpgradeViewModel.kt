package app.signkorean.ui.upgrade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.bookmark.BookmarkPolicy
import app.signkorean.domain.bookmark.BookmarkStore
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
    Bookmark("bookmark"),
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
    /** 결제·복원 중. 버튼을 막는다 (E-08). */
    val busy: Boolean = false,
    /** 결제가 실패했다. Unlock 버튼이 Try again 이 된다 (E-06). */
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
 * 결제에 성공하면 보류 동작(6번째 북마크 등)을 먼저 수행한 뒤 닫는다.
 */
class UpgradeViewModel(
    private val feature: UpgradeFeature,
    private val entitlements: Entitlements,
    private val bookmarks: BookmarkStore,
    private val pending: PendingActionStore,
    private val resume: ResumePendingAction,
) : ViewModel() {

    private val _state = MutableStateFlow(UpgradeUiState(feature))
    val state: StateFlow<UpgradeUiState> = _state.asStateFlow()

    private val _effects = Channel<UpgradeEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<UpgradeEffect> = _effects

    /** 복원 중에는 결제 흐름의 Succeeded 를 이 화면이 직접 처리하므로 흐름 쪽에서 다시 처리하지 않는다. */
    private var restoring = false
    private var finished = false

    init {
        // 지난번에 연 SC21 의 결과(실패·취소)가 새 화면에 남지 않게 한다. 결제 중이면 그대로 둔다.
        entitlements.dismiss()
        viewModelScope.launch {
            if (entitlements.current() == Entitlement.Paid) {
                resume()
                finish(UpgradeEffect.Close)
                return@launch
            }
            if (feature == UpgradeFeature.Bookmark) {
                val count = try {
                    bookmarks.count()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                _state.update { it.copy(savedCount = count) }
            }
            val price = entitlements.localizedPrice()
            _state.update { it.copy(price = price, priceChecked = true) }
        }
        viewModelScope.launch { entitlements.purchaseFlow.collect(::onFlow) }
    }

    fun onAction(action: UpgradeAction) {
        when (action) {
            UpgradeAction.Unlock -> {
                if (_state.value.busy || finished) return
                _state.update { it.copy(message = null) }
                viewModelScope.launch { entitlements.unlock() }
            }
            UpgradeAction.Restore -> {
                if (_state.value.busy || finished) return
                restore()
            }
            UpgradeAction.NotNow -> viewModelScope.launch {
                dropPending()
                entitlements.dismiss()
                finish(UpgradeEffect.Close)
            }
        }
    }

    private fun restore() {
        restoring = true
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                when (entitlements.restore()) {
                    RestoreResult.Restored -> {
                        resume()
                        entitlements.dismiss()
                        finish(UpgradeEffect.Unlocked(UiText.Res(R.string.upgrade_restored)))
                    }
                    RestoreResult.NothingToRestore ->
                        _state.update { it.copy(message = UiText.Res(R.string.upgrade_restore_none)) }
                    RestoreResult.Failed ->
                        _state.update { it.copy(message = UiText.Res(R.string.upgrade_restore_failed)) }
                }
            } finally {
                restoring = false
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun onFlow(flow: PurchaseFlowState) {
        if (finished) return
        when (flow) {
            PurchaseFlowState.Idle -> _state.update { it.copy(busy = restoring) }
            PurchaseFlowState.InProgress -> _state.update { it.copy(busy = true, message = null) }
            PurchaseFlowState.Pending ->
                _state.update { it.copy(busy = false, message = UiText.Res(R.string.upgrade_pending)) }
            PurchaseFlowState.Succeeded -> {
                if (restoring) return
                _state.update { it.copy(busy = false) }
                viewModelScope.launch {
                    resume()
                    entitlements.dismiss()
                    finish(UpgradeEffect.Unlocked(UiText.Res(R.string.upgrade_unlocked)))
                }
            }
            PurchaseFlowState.Cancelled -> viewModelScope.launch {
                dropPending()
                entitlements.dismiss()
                finish(UpgradeEffect.Close)
            }
            is PurchaseFlowState.Failed -> _state.update {
                it.copy(busy = false, failed = true, message = UiText.Res(failText(flow.reason)))
            }
            PurchaseFlowState.Offline ->
                _state.update { it.copy(busy = false, message = UiText.Res(R.string.upgrade_offline)) }
        }
    }

    private fun failText(reason: FailReason): Int = when (reason) {
        FailReason.StoreUnavailable -> R.string.upgrade_failed_store
        FailReason.ProductUnavailable -> R.string.upgrade_failed_product
        FailReason.Unknown -> R.string.upgrade_failed
    }

    /** 결제하지 않고 떠나면 보류 동작을 버린다. 나중에 다른 진입점에서 결제했을 때 뜻밖에 저장되지 않게. */
    private suspend fun dropPending() {
        if (entitlements.current() == Entitlement.Paid) return
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
