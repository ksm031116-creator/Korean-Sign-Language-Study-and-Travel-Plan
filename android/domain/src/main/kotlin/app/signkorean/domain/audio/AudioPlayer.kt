package app.signkorean.domain.audio

import app.signkorean.domain.content.AudioClip
import kotlinx.coroutines.flow.StateFlow

/** 재생 상태. 화면은 버튼 아이콘·레이블로 보인다(UX10: 재생 상태를 아이콘으로 표시). */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Playing(val clip: AudioClip) : PlaybackState

    /**
     * 파일이 없거나(검수 전 포함) 재생에 실패했다. 표기는 그대로 보이고 기기 TTS 로 대신 읽지 않는다(설계서 §7.4).
     * [clip] 은 실패한 음성. 재생 도중 늦게 난 오류도 어느 카드의 실패인지 알 수 있다.
     */
    data class Unavailable(val clip: AudioClip?) : PlaybackState
}

/**
 * 발음 재생 포트 (FR29). 재생은 학습 상태·세션에 영향을 주지 않는다.
 * - 재생 중 같은 음성을 다시 누르면 처음부터 다시 재생한다.
 * - 무음·볼륨 0 은 오류가 아니다.
 */
interface AudioPlayer {
    val state: StateFlow<PlaybackState>

    /** [clip] 이 null 이면 음성이 없는 카드다. 아무것도 재생하지 않고 Unavailable 이 된다. */
    fun play(clip: AudioClip?)

    /** 카드가 바뀌거나 화면을 떠날 때. */
    fun stop()
}
