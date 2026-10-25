package com.nexora.music

import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

private var player: ExoPlayer? = null
private var mediaSession: MediaSession? = null

override fun onCreate() {
    super.onCreate()

    val exoPlayer = ExoPlayer.Builder(this).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )

        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("NexoraPlayback", "Playback error: ${error.errorCodeName}", error)
            }
        })
    }

    player = exoPlayer
    mediaSession = MediaSession.Builder(this, exoPlayer).build()
}

override fun onGetSession(
    controllerInfo: MediaSession.ControllerInfo
): MediaSession? = mediaSession

override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int
): Int {
    super.onStartCommand(intent, flags, startId)

    if (intent?.action != "com.nexora.music.PLAY") {
        return START_NOT_STICKY
    }

    val url = intent.getStringExtra("url")
    if (url.isNullOrBlank()) {
        Log.w("NexoraPlayback", "Play request ignored: empty URL")
        return START_NOT_STICKY
    }

    val title = intent.getStringExtra("title") ?: "Nexora Music"
    val artist = intent.getStringExtra("artist") ?: "Unknown artist"

    val exoPlayer = player ?: run {
        Log.e("NexoraPlayback", "Play request ignored: player unavailable")
        return START_NOT_STICKY
    }

    try {
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build()
            )
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    } catch (error: Exception) {
        Log.e("NexoraPlayback", "Unable to start playback", error)
    }

    return START_NOT_STICKY
}

override fun onDestroy() {
    mediaSession?.release()
    mediaSession = null

    player?.release()
    player = null

    super.onDestroy()
}

}
