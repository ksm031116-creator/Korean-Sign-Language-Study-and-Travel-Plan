package app.signkorean.testing

import app.signkorean.domain.audio.AudioPlayer
import app.signkorean.domain.audio.PlaybackState
import app.signkorean.domain.content.AudioClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 재생 기록을 남기는 가짜. [failing] 에 든 경로는 재생에 실패한다. */
class FakeAudioPlayer(private val failing: Set<String> = emptySet()) : AudioPlayer {
    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state

    val played = mutableListOf<AudioClip>()
    var stops = 0
        private set

    override fun play(clip: AudioClip?) {
        if (clip == null || clip.path in failing) {
            _state.value = PlaybackState.Unavailable
            return
        }
        played += clip
        _state.value = PlaybackState.Playing(clip)
    }

    override fun stop() {
        stops++
        _state.value = PlaybackState.Idle
    }

    /** 재생이 끝난 것처럼. */
    fun complete() {
        _state.value = PlaybackState.Idle
    }
}
