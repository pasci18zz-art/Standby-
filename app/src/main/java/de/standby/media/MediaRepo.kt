package de.standby.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow

/** Leerer Dienst: Android erlaubt den Zugriff auf laufende Musik nur Apps mit aktivem Benachrichtigungszugriff. */
class MediaListener : NotificationListenerService()

data class QueueEntry(val id: Long, val title: String, val subtitle: String, val icon: Bitmap?, val iconUri: String? = null)

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
    val queue: List<QueueEntry> = emptyList(),
    val activeQueueId: Long = -1,
) {
    /** Die Titel nach dem aktuellen (sofern der Player seine Warteschlange teilt). */
    fun upcoming(): List<QueueEntry> {
        val idx = queue.indexOfFirst { it.id == activeQueueId }
        return if (idx >= 0) queue.drop(idx + 1) else queue
    }

    fun positionNow(): Long {
        val p = if (playing) position + ((SystemClock.elapsedRealtime() - stamp) * speed).toLong() else position
        return if (duration > 0) p.coerceIn(0, duration) else p.coerceAtLeast(0)
    }

    /** Anzeigen, solange Musik läuft oder bis zur eingestellten Zeit nach einer Pause (0 = immer). */
    fun visible(): Boolean {
        val min = de.standby.Prefs.musicHoldMin
        return playing || min == 0 || SystemClock.elapsedRealtime() - lastPlayingAt < min * 60_000L
    }
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
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = publish()
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
            queue = c.queue.orEmpty().take(60).map {
                QueueEntry(
                    id = it.queueId,
                    title = it.description.title?.toString() ?: "",
                    subtitle = it.description.subtitle?.toString() ?: "",
                    icon = it.description.iconBitmap,
                    iconUri = it.description.iconUri?.toString(),
                )
            },
            activeQueueId = st?.activeQueueItemId ?: -1,
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
    // ---------- Cover der Warteschlange ----------

    private val artCache = android.util.LruCache<String, Bitmap>(40)
    private val artFailed = HashSet<String>()

    /**
     * Lädt ein Cover über die Bild-Adresse, die der Player mitliefert (Internet oder Inhalts-Anbieter).
     * Gibt null zurück, wenn der Player keines herausgibt.
     */
    fun loadArt(uri: String): Bitmap? {
        artCache.get(uri)?.let { return it }
        if (uri in artFailed) return null
        val ctx = app ?: return null
        val bmp = try {
            val stream = when {
                uri.startsWith("http://") || uri.startsWith("https://") ->
                    (java.net.URL(uri).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }).getInputStream()
                uri.startsWith("content://") || uri.startsWith("android.resource://") || uri.startsWith("file://") ->
                    ctx.contentResolver.openInputStream(android.net.Uri.parse(uri))
                else -> null
            }
            stream?.use { android.graphics.BitmapFactory.decodeStream(it) }
                ?.let { b -> if (b.width > 160) Bitmap.createScaledBitmap(b, 160, 160 * b.height / b.width, true) else b }
        } catch (_: Throwable) {
            null
        }
        if (bmp != null) artCache.put(uri, bmp) else synchronized(artFailed) { artFailed.add(uri) }
        return bmp
    }

    fun playQueueItem(id: Long) = controller?.transportControls?.skipToQueueItem(id) ?: Unit
    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms) ?: Unit
}
