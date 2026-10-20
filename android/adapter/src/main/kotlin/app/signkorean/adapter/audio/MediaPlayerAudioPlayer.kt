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

    // 앱 콘텐츠 재생이므로 미디어 소리로 낸다(접근성 소리 줄로 가면 미디어 볼륨 키가 듣지 않는다).
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        // 통화 등으로 포커스를 잃으면 멈춘다.
        .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop() }
        .build()

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var hasFocus = false

    override fun play(clip: AudioClip?) {
        releasePlayer()
        if (clip == null) {
            abandonFocus()
            _state.value = PlaybackState.Unavailable(null)
            return
        }
        // 재생 중 다시 누르면 처음부터: 이전 재생을 버리고 새로 시작한다(FR29). 포커스는 이어서 쥔다.
        val mp = MediaPlayer()
        player = mp // 실패해도 이 플레이어를 반드시 해제하도록 먼저 붙잡는다.
        try {
            appContext.assets.openFd(ASSET_ROOT + clip.path).use { fd -> mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
            mp.setAudioAttributes(attributes)
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                fail(clip)
                true
            }
            mp.prepare()
            if (!requestFocus()) {
                // 통화 중 등 포커스를 받지 못하면 재생하지 않는다. 오류가 아니라 대기 상태로 둔다.
                releasePlayer()
                _state.value = PlaybackState.Idle
                return
            }
            mp.start()
            _state.value = PlaybackState.Playing(clip)
        } catch (e: Exception) {
            // 파일 없음·디코딩 실패. 기기 TTS 로 대신 읽지 않는다(검수 안 된 발음이 나간다).
            fail(clip)
        }
    }

    override fun stop() {
        releasePlayer()
        abandonFocus()
        _state.value = PlaybackState.Idle
    }

    private fun fail(clip: AudioClip) {
        releasePlayer()
        abandonFocus()
        _state.value = PlaybackState.Unavailable(clip)
    }

    private fun requestFocus(): Boolean {
        if (hasFocus) return true
        hasFocus = audioManager?.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    private fun abandonFocus() {
        if (hasFocus) audioManager?.abandonAudioFocusRequest(focus)
        hasFocus = false
    }

    private fun releasePlayer() {
        player?.let {
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }

    private companion object {
        const val ASSET_ROOT = "content/"
    }
}
