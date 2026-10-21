package app.signkorean.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.bookmark.ClearBookmarks
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.domain.entitlement.RestoreResult
import app.signkorean.domain.learning.ResetLearning
import app.signkorean.domain.locale.CurrentLocale
import app.signkorean.domain.locale.ResolveAppLanguage
import app.signkorean.domain.onboarding.OnboardingStore
import app.signkorean.ui.plan.DebugRelock
import app.signkorean.ui.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 지우기 전에 묻는 확인 (FR16). */
enum class SettingsConfirm { ResetLearning, ClearBookmarks }

data class SettingsUiState(
    val entitlement: Entitlement? = null,
    /** 고를 수 있는 앱 언어. 출시 빌드는 검수 완료 언어만(설계서 §8.8). */
    val languages: List<LocaleTag> = emptyList(),
    val current: LocaleTag? = null,
    val confirm: SettingsConfirm? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
    /** 디버그 빌드의 모의 결제에만 있다. 릴리스는 null. */
    @StringRes val relockLabel: Int? = null,
)

sealed interface SettingsAction {
    data object AskResetLearning : SettingsAction
    data object AskClearBookmarks : SettingsAction
    data object Confirm : SettingsAction
    data object Cancel : SettingsAction
    data object Restore : SettingsAction
    data class SelectLanguage(val tag: LocaleTag) : SettingsAction
    data object ResetCoach : SettingsAction
    data object Relock : SettingsAction
    data object Refresh : SettingsAction
}

/**
 * SC10 설정 (구현 계획 Phase 20 D1). 학습 기록 초기화는 학습 상태·세션만(E-10), 북마크 초기화는 북마크만(B-09) 지운다.
 * 어느 것도 구매 권한과 여행을 지우지 않는다. 앱 언어를 바꿔도 학습 상태·세션은 그대로다(L10N-02).
 */
class SettingsViewModel(
    private val resetLearning: ResetLearning,
    private val clearBookmarks: ClearBookmarks,
    private val entitlements: Entitlements,
    private val onboarding: OnboardingStore,
    private val content: ContentRepository,
    private val currentLocale: CurrentLocale,
    private val includeDraftLanguages: Boolean,
    private val relock: DebugRelock?,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(relockLabel = relock?.label))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val languages = content.load().selectableLocales(includeDraftLanguages)
                _state.update { it.copy(languages = languages, current = ResolveAppLanguage(currentLocale)(languages)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 언어 목록이 없어도 다른 설정은 쓸 수 있다.
            }
        }
        refreshEntitlement()
    }

    private fun refreshEntitlement() {
        viewModelScope.launch {
            val e = try {
                entitlements.current()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _state.update { it.copy(entitlement = e) }
        }
    }

    fun onAction(action: SettingsAction) {
        val s = _state.value
        when (action) {
            SettingsAction.Refresh -> refreshEntitlement()
            SettingsAction.AskResetLearning -> if (!s.busy) _state.update { it.copy(confirm = SettingsConfirm.ResetLearning, message = null) }
            SettingsAction.AskClearBookmarks -> if (!s.busy) _state.update { it.copy(confirm = SettingsConfirm.ClearBookmarks, message = null) }
            SettingsAction.Cancel -> _state.update { it.copy(confirm = null) }
            SettingsAction.Confirm -> s.confirm?.let { which ->
                if (s.busy) return
                _state.update { it.copy(confirm = null, busy = true) }
                viewModelScope.launch {
                    val ok = when (which) {
                        SettingsConfirm.ResetLearning -> resetLearning()
                        SettingsConfirm.ClearBookmarks -> clearBookmarks()
                    }
                    val done = if (which == SettingsConfirm.ResetLearning) R.string.settings_reset_done else R.string.settings_bookmarks_cleared
                    _state.update { it.copy(busy = false, message = UiText.Res(if (ok) done else R.string.common_save_failed)) }
                }
            }
            SettingsAction.Restore -> {
                if (s.busy) return
                _state.update { it.copy(busy = true, message = null) }
                viewModelScope.launch {
                    val result = try {
                        entitlements.restore()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        RestoreResult.Failed
                    }
                    val text = when (result) {
                        RestoreResult.Restored -> R.string.upgrade_restored
                        RestoreResult.NothingToRestore -> R.string.upgrade_restore_none
                        RestoreResult.Failed -> R.string.upgrade_restore_failed
                    }
                    _state.update { it.copy(busy = false, message = UiText.Res(text)) }
                    refreshEntitlement()
                }
            }
            // 앱 언어 저장은 AppCompat/OS 가 한다. 화면은 재시작 없이 다시 그려진다(L10N-02).
            is SettingsAction.SelectLanguage -> if (action.tag in s.languages) {
                currentLocale.select(action.tag)
                _state.update { it.copy(current = action.tag) }
            }
            SettingsAction.ResetCoach -> viewModelScope.launch {
                val ok = try {
                    onboarding.resetCoach()
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                _state.update { it.copy(message = UiText.Res(if (ok) R.string.settings_coach_reset_done else R.string.common_save_failed)) }
            }
            SettingsAction.Relock -> relock?.let { r ->
                viewModelScope.launch {
                    val ok = try {
                        r.relock()
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                    if (!ok) _state.update { it.copy(message = UiText.Res(R.string.common_save_failed)) }
                    refreshEntitlement()
                }
            }
        }
    }
}
