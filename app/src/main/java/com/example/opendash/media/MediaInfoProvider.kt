package com.example.opendash.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import com.example.opendash.util.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MediaInfoProvider(private val context: Context) {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, OpenDashNotificationListener::class.java)
    private var controller: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = bind(null)
    }

    private val sessionsCallback = MediaSessionManager.OnActiveSessionsChangedListener(::bind)

    fun start() {
        if (!isAccessGranted(context)) {
            DebugLog.i(TAG) { "Notification access unavailable; media forwarding is disabled" }
            return
        }
        runCatching {
            sessionManager?.addOnActiveSessionsChangedListener(sessionsCallback, listenerComponent)
            bind(sessionManager?.getActiveSessions(listenerComponent))
        }.onFailure { DebugLog.w(TAG) { "Media session binding failed: ${it.javaClass.simpleName}" } }
    }

    fun stop() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsCallback) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        _nowPlaying.value = null
    }

    /**
     * Transport controls. With notification access we talk to the active player
     * directly; without it we send the same media-button press a headset would, so
     * these still work in the build without notification access.
     */
    fun skipNext(): Boolean = control(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) { it.skipToNext() }
    fun skipPrevious(): Boolean = control(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) { it.skipToPrevious() }
    fun pause(): Boolean = control(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE) { it.pause() }
    fun play(): Boolean = control(android.view.KeyEvent.KEYCODE_MEDIA_PLAY) { it.play() }

    /** Play if paused, pause if playing. Returns true when music is now playing. */
    fun togglePlayPause(): Boolean {
        val c = controller
        if (c == null) {
            mediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            val am = context.getSystemService(android.media.AudioManager::class.java)
            return am?.isMusicActive != true   // it was playing → now paused, and the reverse
        }
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        runCatching { if (playing) c.transportControls.pause() else c.transportControls.play() }
        return !playing
    }

    private fun control(key: Int, direct: (MediaController.TransportControls) -> Unit): Boolean {
        val c = controller
        return if (c != null) runCatching { direct(c.transportControls); true }.getOrDefault(false)
        else mediaKey(key)
    }

    private fun mediaKey(key: Int): Boolean = runCatching {
        val am = context.getSystemService(android.media.AudioManager::class.java) ?: return false
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, key))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, key))
        true
    }.getOrDefault(false)

    /** Nudge the media volume by [steps] (negative = quieter). */
    fun volume(steps: Int) {
        val am = context.getSystemService(android.media.AudioManager::class.java) ?: return
        val dir = if (steps >= 0) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
        repeat(kotlin.math.abs(steps)) {
            runCatching { am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, dir, 0) }
        }
    }

    private fun bind(sessions: List<MediaController>?) {
        val next = sessions?.firstOrNull()
        if (next?.sessionToken == controller?.sessionToken) {
            publish()
            return
        }
        controller?.unregisterCallback(controllerCallback)
        controller = next
        controller?.registerCallback(controllerCallback)
        publish()
    }

    private fun publish() {
        val metadata = controller?.metadata ?: run {
            _nowPlaying.value = null
            return
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        if (title.isBlank() && artist.isBlank()) {
            _nowPlaying.value = null
            return
        }
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        _nowPlaying.value = NowPlaying(title, album, artist, art)
    }

    companion object {
        private const val TAG = "MediaInfoProvider"

        fun isAccessGranted(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            return enabled.split(':').any { it.contains(context.packageName) }
        }

        fun accessSettingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
