package app.signkorean.adapter.audio

import app.signkorean.domain.audio.PlaybackState
import app.signkorean.domain.content.AudioClip
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** U-12: 음성이 없거나 파일이 없으면 Unavailable 하나로 처리하고 앱은 멈추지 않는다. */
@RunWith(RobolectricTestRunner::class)
class MediaPlayerAudioPlayerTest {

    private val player = MediaPlayerAudioPlayer(RuntimeEnvironment.getApplication())

    @Test
    fun `음성이 없는 카드는 Unavailable`() {
        player.play(null)
        assertEquals(PlaybackState.Unavailable, player.state.value)
    }

    @Test
    fun `파일이 없으면 Unavailable, 멈추면 Idle`() {
        player.play(AudioClip("audio/nope.m4a", "x", 1, 1000))
        assertEquals(PlaybackState.Unavailable, player.state.value)
        player.stop()
        assertEquals(PlaybackState.Idle, player.state.value)
    }
}
