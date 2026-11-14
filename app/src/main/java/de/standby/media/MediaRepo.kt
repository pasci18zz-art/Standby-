package de.standby.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow

/** Leerer Dienst: Android erlaubt den Zugriff auf laufende Musik nur Apps mit aktivem Benachrichtigungszugriff. */
class MediaListener : NotificationListenerService()

data class MediaInfo(
    val pkg: String,
    val appName: String,
    val title: String,
    val artist: String,
    val cover: Bitmap?,
    val playing: Boolean,
    val position: Long,
    val duration: Long,
    /** SystemClock.elapsedRealtime() zum Zeitpunkt von [position]. */
    val stamp: Long,
    val speed: Float,
    val lastPlayingAt: Long,
) {
    fun positionNow(): Long {
        val p = if (playing) position + ((SystemClock.elapsedRealtime() - stamp) * speed).toLong() else position
        return if (duration > 0) p.coerceIn(0, duration) else p.coerceAtLeast(0)
    }

    /** Anzeigen, solange Musik läuft oder bis zu 2 Minuten nach Pause. */
    fun visible(): Boolean = playing || SystemClock.elapsedRealtime() - lastPlayingAt < 120_000
}

object MediaRepo {
    val info = MutableStateFlow<MediaInfo?>(null)
    val hasAccess = MutableStateFlow(false)

    private var app: Context? = null
    private var registered = false
    private var controller: MediaController? = null
    private var lastPlayingAt = -1_000_000L
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    private fun component(ctx: Context) = ComponentName(ctx, MediaListener::class.java)

    fun start(ctx: Context) {
        app = ctx.applicationContext
        refresh()
    }

    /** Fragt die aktiven Medien-Sitzungen ab (auch regelmäßig aufrufen, falls ein Player später startet). */
    fun refresh() {
        val ctx = app ?: return
        val msm = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        try {
            if (!registered) {
                msm.addOnActiveSessionsChangedListener(
                    { list -> pick(list ?: emptyList()) },
                    component(ctx),
                    handler,
                )
                registered = true
            }
            pick(msm.getActiveSessions(component(ctx)))
            hasAccess.value = true
        } catch (e: SecurityException) {
            hasAccess.value = false
            registered = false
            info.value = null
        }
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata != null }
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            info.value = null
            return
        }
        val md = c.metadata
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING || st?.state == PlaybackState.STATE_BUFFERING
        if (playing) lastPlayingAt = SystemClock.elapsedRealtime()
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val cover = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        info.value = MediaInfo(
            pkg = c.packageName,
            appName = appLabel(c.packageName),
            title = title,
            artist = artist,
            cover = cover,
            playing = playing,
            position = st?.position ?: 0L,
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            stamp = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            lastPlayingAt = lastPlayingAt,
        )
    }

    private fun appLabel(pkg: String): String {
        val ctx = app
        if (ctx != null) {
            try {
                val pm = ctx.packageManager
                return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().uppercase()
            } catch (_: Exception) {
            }
        }
        val parts = pkg.split('.')
        return (parts.getOrNull(1) ?: pkg).uppercase()
    }

    // ---------- Steuerung ----------

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext() ?: Unit
    fun previous() = controller?.transportControls?.skipToPrevious() ?: Unit
    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms) ?: Unit
}
