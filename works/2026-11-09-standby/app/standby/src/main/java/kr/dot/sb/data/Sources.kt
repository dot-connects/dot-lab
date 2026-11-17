package kr.dot.sb.data

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.CalendarContract
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import kr.dot.sb.platform.GlowScheduler
import kr.dot.sb.platform.Listener
import kr.dot.sb.ui.state.AgendaItem
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.Permissions
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Active media session -> [MediaInfo] + transport. Needs notification access (the session lookup is bound to the listener). */
class MediaSource(private val ctx: Context, private val onChange: (MediaInfo?) -> Unit) {
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)
    private val comp = ComponentName(ctx, Listener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var artKey = ""
    private var artSrc: android.graphics.Bitmap? = null
    private var art: androidx.compose.ui.graphics.ImageBitmap? = null

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = pick(runCatching { msm.getActiveSessions(comp) }.getOrNull())
    }

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessions, comp, handler)
            pick(msm.getActiveSessions(comp))
        } catch (e: SecurityException) {
            onChange(null) // no notification access yet
        }
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessions) }
        controller?.unregisterCallback(cb)
        controller = null
    }

    private fun pick(list: List<MediaController>?) {
        val next = list.orEmpty().firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.orEmpty().firstOrNull { it.metadata != null }
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(cb)
            controller = next
            next?.registerCallback(cb, handler)
        }
        publish()
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }
    val packageName get() = controller?.packageName

    /** Re-emit with a fresh position (the playback state only carries the position of its last update). */
    fun publish() {
        val c = controller
        val st = c?.playbackState
        val meta = c?.metadata
        if (c == null || st == null || meta == null || st.state == PlaybackState.STATE_NONE || st.state == PlaybackState.STATE_STOPPED) {
            onChange(null); return
        }
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val key = "${c.packageName}|$title|$artist"
        // Players (YouTube Music) often publish the title first and the cover a moment later for the same track: re-read the
        // bitmap on every metadata update and rescale only when it is a different bitmap, else the cover never appears.
        val bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (key != artKey || bmp !== artSrc) {
            if (key != artKey) kr.dot.sb.platform.Measure.log(ctx, "미디어 · ${c.packageName} · 그림 ${if (bmp != null) "${bmp.width}x${bmp.height}" else "없음"}" +
                " · uri ${listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).count { meta.getString(it) != null }}")
            artKey = key
            artSrc = bmp
            art = bmp?.let { scaleDown(it, 512).asImageBitmap() }
        }
        val playing = st.state == PlaybackState.STATE_PLAYING
        val pos = if (playing) st.position + ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong() else st.position
        val dur = meta.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0)
        val pm = ctx.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault(c.packageName)
        onChange(
            MediaInfo(
                label, c.packageName, MediaKinds.kind(c.packageName), title, artist, art, playing,
                if (dur > 0) pos.coerceIn(0, dur) else pos.coerceAtLeast(0), dur,
                canPrev = st.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
                canNext = st.actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            ),
        )
    }

    companion object {
        fun scaleDown(b: Bitmap, maxSide: Int): Bitmap {
            val m = maxOf(b.width, b.height)
            if (m <= maxSide) return b
            val f = maxSide.toFloat() / m
            return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
        }
    }
}

/** One calendar of the provider; [key] = "accountType|accountName|name", the stored form (ids get reused). */
data class CalendarInfo(val id: Long, val accountType: String, val accountName: String, val name: String, val color: Long) {
    val key: String get() = "$accountType|$accountName|$name"
}

/** Calendar and alarm clock: each returns null when its permission is missing. Blocking: call from IO. */
object DeviceSources {
    fun has(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    fun permissions(ctx: Context) = Permissions(
        notifications = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName),
        calendar = has(ctx, Manifest.permission.READ_CALENDAR),
        exactAlarm = ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
    )

    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Every calendar in the provider, visible or not, sorted by account (the picker groups them). Null = permission
     * missing. [CalendarInfo.key] is what `AppConfig.excludedCalendars` stores.
     */
    fun calendars(ctx: Context): List<CalendarInfo>? {
        if (!has(ctx, Manifest.permission.READ_CALENDAR)) return null
        val cols = arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.CALENDAR_COLOR,
        )
        return try {
            buildList {
                ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        add(CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getInt(4).toLong() and 0xFFFFFFFFL))
                    }
                }
            }.sortedWith(compareBy({ it.accountName }, { it.accountType }, { it.name }))
        } catch (e: SecurityException) { null }
    }

    /** Events from today through the next 14 days that are not over yet, in start order, at most 8; [excluded] calendar keys left out. */
    fun upcoming(ctx: Context, now: LocalDateTime, excluded: Set<String> = emptySet()): List<AgendaItem>? {
        if (!has(ctx, Manifest.permission.READ_CALENDAR)) return null
        // keys, not ids, are stored (ids get reused): map them to today's ids; no clause at all when nothing is excluded
        val ids = if (excluded.isEmpty()) emptyList() else calendars(ctx).orEmpty().filter { it.key in excluded }.map { it.id }
        val where = if (ids.isEmpty()) null else "${CalendarContract.Instances.CALENDAR_ID} NOT IN (${ids.joinToString(",")})"
        val zone = ZoneId.systemDefault()
        val start = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = now.toLocalDate().plusDays(15).atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
        val cols = arrayOf(
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_COLOR, CalendarContract.Instances.ALL_DAY,
        )
        return try {
            buildList {
                ctx.contentResolver.query(uri, cols, where, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    while (c.moveToNext() && size < 8) {
                        val allDay = c.getInt(4) == 1
                        if (!allDay && c.getLong(1) < nowMs) continue // already over
                        // all-day instances are stored at UTC midnight
                        val at = Instant.ofEpochMilli(c.getLong(0)).atZone(if (allDay) ZoneId.of("UTC") else zone)
                        val t = if (allDay) "종일" else at.format(HM)
                        val end = if (allDay) null else Instant.ofEpochMilli(c.getLong(1)).atZone(zone).toLocalDateTime()
                        add(AgendaItem(t, c.getString(2)?.ifBlank { null } ?: "(제목 없음)", c.getInt(3).toLong() and 0xFFFFFFFFL, at.toLocalDate(), end))
                    }
                }
            }
        } catch (e: SecurityException) { null }
    }

    fun nextAlarm(ctx: Context): LocalDateTime? =
        GlowScheduler.nextAlarmMs(ctx)?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
}
