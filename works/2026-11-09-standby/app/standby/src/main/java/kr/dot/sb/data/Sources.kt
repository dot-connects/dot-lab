package kr.dot.sb.data

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.CalendarContract
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import kr.dot.sb.platform.GlowScheduler
import kr.dot.sb.platform.Listener
import kr.dot.sb.ui.state.AgendaItem
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.Permissions
import kr.dot.sb.ui.state.Photo
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
        if (key != artKey) {
            artKey = key
            val bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
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

/** Calendar, alarm clock, photos: each returns null when its permission is missing. Blocking: call from IO. */
object DeviceSources {
    fun has(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    fun permissions(ctx: Context) = Permissions(
        notifications = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName),
        calendar = has(ctx, Manifest.permission.READ_CALENDAR),
        photos = has(ctx, Manifest.permission.READ_MEDIA_IMAGES),
    )

    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    fun agenda(ctx: Context, now: LocalDateTime): List<AgendaItem>? {
        if (!has(ctx, Manifest.permission.READ_CALENDAR)) return null
        val zone = ZoneId.systemDefault()
        val start = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val end = now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, start); ContentUris.appendId(it, end) }.build()
        val cols = arrayOf(
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.CALENDAR_COLOR, CalendarContract.Instances.ALL_DAY,
        )
        return try {
            buildList {
                ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                    while (c.moveToNext() && size < 5) {
                        val allDay = c.getInt(4) == 1
                        if (!allDay && c.getLong(1) < nowMs) continue // already over
                        val t = if (allDay) "종일" else Instant.ofEpochMilli(c.getLong(0)).atZone(zone).format(HM)
                        add(AgendaItem(t, c.getString(2)?.ifBlank { null } ?: "(제목 없음)", c.getInt(3).toLong() and 0xFFFFFFFFL))
                    }
                }
            }
        } catch (e: SecurityException) { null }
    }

    fun nextAlarm(ctx: Context): LocalDateTime? =
        GlowScheduler.nextAlarmMs(ctx)?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }

    private val DATE = DateTimeFormatter.ofPattern("yyyy.M.d")
    private val IMAGES: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private fun query(ctx: Context, selection: String?, args: Array<String>?, limit: Int) = ctx.contentResolver.query(
        IMAGES,
        arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
        Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putStringArray(android.content.ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_TAKEN))
            putInt(android.content.ContentResolver.QUERY_ARG_SORT_DIRECTION, android.content.ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
        },
        null,
    )

    private data class Row(val id: Long, val takenMs: Long, val bucket: String)

    private fun rows(ctx: Context, selection: String?, args: Array<String>?, limit: Int): List<Row> = try {
        buildList {
            query(ctx, selection, args, limit)?.use { c ->
                while (c.moveToNext()) add(Row(c.getLong(0), c.getLong(1), c.getString(2) ?: ""))
            }
        }
    } catch (e: SecurityException) { emptyList() }

    /** Up to [n] random photos from favourites + the 200 most recent, decoded to <= 1280 px. null = no permission. */
    fun photos(ctx: Context, n: Int): List<Photo>? {
        if (!has(ctx, Manifest.permission.READ_MEDIA_IMAGES)) return null
        val pool = (rows(ctx, "${MediaStore.Images.Media.IS_FAVORITE}=1", null, 100) +
            rows(ctx, "${MediaStore.Images.Media.DATE_TAKEN}>0", null, 200)).distinctBy { it.id }
        val out = ArrayList<Photo>(n)
        for (r in pool.shuffled()) {
            if (out.size >= n) break
            decode(ctx, r, null)?.let { out += it }
        }
        return out
    }

    /** A photo taken on today's day-of-year 1..15 years ago, or null. */
    fun memory(ctx: Context, today: LocalDate): Photo? {
        if (!has(ctx, Manifest.permission.READ_MEDIA_IMAGES)) return null
        val zone = ZoneId.systemDefault()
        val sel = StringBuilder(); val args = ArrayList<String>()
        for (y in 1..15) {
            val d = today.minusYears(y.toLong())
            if (sel.isNotEmpty()) sel.append(" OR ")
            sel.append("(${MediaStore.Images.Media.DATE_TAKEN}>=? AND ${MediaStore.Images.Media.DATE_TAKEN}<?)")
            args += d.atStartOfDay(zone).toInstant().toEpochMilli().toString()
            args += d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli().toString()
        }
        val r = rows(ctx, sel.toString(), args.toTypedArray(), 30).randomOrNull() ?: return null
        val years = today.year - Instant.ofEpochMilli(r.takenMs).atZone(zone).year
        return decode(ctx, r, years)
    }

    private fun decode(ctx: Context, r: Row, yearsAgo: Int?): Photo? = runCatching {
        val uri = ContentUris.withAppendedId(IMAGES, r.id)
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
            var s = 1
            while (maxOf(info.size.width, info.size.height) / (s * 2) >= 1280) s *= 2 // inSampleSize: no full-size bitmap in memory
            dec.setTargetSampleSize(s)
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val taken = Instant.ofEpochMilli(r.takenMs).atZone(ZoneId.systemDefault()).toLocalDate()
        // no reverse geocoding: the album name stands in for the place, and the camera roll has none worth showing
        val place = if (r.bucket.equals("Camera", true)) "" else r.bucket
        Photo(MediaSource.scaleDown(bmp, 1280).asImageBitmap(), place, taken.format(DATE), yearsAgo)
    }.getOrNull()
}
