package app.signkorean.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.locale.CurrentLocale
import app.signkorean.domain.locale.ResolveAppLanguage
import app.signkorean.domain.onboarding.OnboardingStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 첫 실행 안내를 보여야 하는지. 판단 전(null)에는 아무것도 그리지 않는다(깜빡임 방지). */
class AppGateViewModel(private val onboarding: OnboardingStore) : ViewModel() {
    private val _needsFirstRun = MutableStateFlow<Boolean?>(null)
    val needsFirstRun: StateFlow<Boolean?> = _needsFirstRun.asStateFlow()

    init {
        viewModelScope.launch { _needsFirstRun.value = runCatching { !onboarding.firstRunDone() }.getOrDefault(false) }
    }

    fun finished() {
        _needsFirstRun.value = false
    }
}

data class FirstRunUiState(
    val step: Int = 0,
    val steps: Int = 3,
    /** 고를 수 있는 언어. 하나뿐이면 언어 고르기를 그리지 않는다. */
    val languages: List<LocaleTag> = emptyList(),
    val current: LocaleTag? = null,
    val finished: Boolean = false,
)

sealed interface FirstRunAction {
    data object Next : FirstRunAction
    data object Back : FirstRunAction
    data object Finish : FirstRunAction
    data class SelectLanguage(val tag: LocaleTag) : FirstRunAction
}

/**
 * SC01 첫 실행 안내 3단계. 첫 단계에서 앱 언어를 확인한다(기기 언어가 기본, 설계서 §8.2).
 * 고를 수 있는 언어는 콘텐츠 팩의 언어에서 온다. 출시 빌드는 검수 완료 언어만, 디버그는 초안도 보인다.
 */
class FirstRunViewModel(
    private val onboarding: OnboardingStore,
    private val content: ContentRepository,
    private val currentLocale: CurrentLocale,
    private val includeDraftLanguages: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(FirstRunUiState())
    val state: StateFlow<FirstRunUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val languages = content.load().selectableLocales(includeDraftLanguages)
                _state.update { it.copy(languages = languages, current = ResolveAppLanguage(currentLocale)(languages)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 언어 목록 없이도 안내는 이어 간다.
            }
        }
    }

    fun onAction(action: FirstRunAction) {
        when (action) {
            FirstRunAction.Next -> _state.update { it.copy(step = (it.step + 1).coerceAtMost(it.steps - 1)) }
            FirstRunAction.Back -> _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
            FirstRunAction.Finish -> viewModelScope.launch {
                runCatching { onboarding.markFirstRunDone() }
                _state.update { it.copy(finished = true) }
            }
            is FirstRunAction.SelectLanguage -> {
                // 앱 언어 저장은 AppCompat/OS 가 한다. 화면은 언어가 바뀌면 다시 그려진다.
                currentLocale.select(action.tag)
                _state.update { it.copy(current = action.tag) }
            }
        }
    }
}
