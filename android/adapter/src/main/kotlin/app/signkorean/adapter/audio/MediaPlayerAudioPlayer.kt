package app.signkorean.adapter.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import app.signkorean.domain.audio.AudioPlayer
import app.signkorean.domain.audio.PlaybackState
import app.signkorean.domain.content.AudioClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱에 내장된 발음 음성 재생 (설계서 §7.4). 파일은 assets/content/ 아래에 있다.
 * 1~2초짜리 단어라 MediaPlayer 로 충분하다(Media3 를 들이지 않는다).
 * 짧은 일시 오디오 포커스를 요청해 다른 앱 소리는 잠깐 줄어들게 한다. 무음·볼륨 0 이어도 오류로 보이지 않는다.
 */
class MediaPlayerAudioPlayer(context: Context) : AudioPlayer {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null

    override fun play(clip: AudioClip?) {
        release()
        if (clip == null) {
            _state.value = PlaybackState.Unavailable
            return
        }
        // 재생 중 다시 누르면 처음부터: 이전 재생을 버리고 새로 시작한다(FR29).
        try {
            val fd = appContext.assets.openFd(ASSET_ROOT + clip.path)
            val mp = MediaPlayer()
            fd.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setAudioAttributes(attributes)
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ ->
                release()
                _state.value = PlaybackState.Unavailable
                true
            }
            mp.prepare()
            audioManager?.requestAudioFocus(focus)
            mp.start()
            player = mp
            _state.value = PlaybackState.Playing(clip)
        } catch (e: Exception) {
            // 파일 없음·디코딩 실패. 기기 TTS 로 대신 읽지 않는다(검수 안 된 발음이 나간다).
            release()
            _state.value = PlaybackState.Unavailable
        }
    }

    override fun stop() {
        release()
        _state.value = PlaybackState.Idle
    }

    private fun finish() {
        release()
        _state.value = PlaybackState.Idle
    }

    private fun release() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        if (player != null) audioManager?.abandonAudioFocusRequest(focus)
        player = null
    }

    private companion object {
        const val ASSET_ROOT = "content/"
    }
}
