package kr.dot.sb.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.compose.ui.graphics.asImageBitmap
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.ui.state.Photo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pure rules of [PhotoIndex] (plan-r15 B1), tested without a device. */
object PhotoRules {
    // 20231109_153012, IMG_20231109_153012, PXL_20231109_153012345, KakaoTalk_20231109_..., IMG-20231109-WA0001
    private val DATED = Regex("""(?<!\d)((?:19|20)\d{2})(\d{2})(\d{2})(?:[_-](\d{2})(\d{2})(\d{2})|(?!\d))""")

    /** The camera-style date in a file name; screenshots and names without one = null (the caller uses LAST_MODIFIED). */
    fun nameDate(name: String): LocalDateTime? {
        if (name.startsWith("Screenshot", ignoreCase = true)) return null
        val g = DATED.find(name)?.groupValues ?: return null
        fun n(i: Int) = g[i].ifEmpty { "0" }.toInt()
        return runCatching { LocalDateTime.of(n(1), n(2), n(3), n(4), n(5), n(6)) }.getOrNull()
    }

    /** Sub-folders the index never enters (the picked folder itself is always read). */
    fun skipDir(name: String) = name.startsWith(".") || name.equals("Screenshots", true) || name == "스크린샷"

    enum class Grant { KEEP, DROP, RETRY }

    /**
     * A picked folder is dropped only when its grant is gone from `persistedUriPermissions` (restored from a backup,
     * revoked); a failed query (provider error, SD card out) keeps it and asks to pick again until a read succeeds.
     */
    fun grant(persisted: Boolean, readOk: Boolean): Grant = when {
        !persisted -> Grant.DROP
        !readOk -> Grant.RETRY
        else -> Grant.KEEP
    }
}

/** A picked folder for settings: [count] null = not read yet, [broken] = the last read failed ("다시 골라 주세요"). */
data class PhotoFolder(val uri: String, val name: String, val count: Int?, val broken: Boolean)

/**
 * plan-r15 B1: the photos of the picked SAF folders, one app-wide index (StandBy, dream, settings). Built on IO, once
 * per folder list and again after [MAX_AGE] (the listener keeps the process alive for days). Each folder = its own
 * files + one sub-folder level. One pass gives the slideshow list (newest [PER_FOLDER] per folder) and a month-day map
 * over every row for 추억. Blocking: call from IO.
 */
object PhotoIndex {
    class Item(val folder: String, val docId: String, val ms: Long)

    class Snap(
        /** The folder list asked for, and what is left of it after stale grants were dropped. */
        val asked: List<String>,
        val folders: List<String>,
        val builtAt: Long,
        val slides: Map<String, List<Item>>,
        val byDay: Map<MonthDay, List<Item>>,
        val names: Map<String, String>,
        val counts: Map<String, Int>,
        val failed: Set<String>,
    ) {
        fun list(): List<PhotoFolder> = folders.map { PhotoFolder(it, names[it] ?: fallbackName(it), counts[it], it in failed) }
    }

    private const val MAX_AGE = 6 * 3_600_000L
    private const val PER_FOLDER = 5_000
    private val COLS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED)
    private val DATE = DateTimeFormatter.ofPattern("yyyy.M.d")
    private val EXIF = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    @Volatile private var snap: Snap? = null

    /** The index for [folders]; rebuilt when they changed or the last build is older than 6 h. */
    @Synchronized
    fun get(ctx: Context, folders: List<String>): Snap =
        snap?.takeIf { (it.asked == folders || it.folders == folders) && SystemClock.elapsedRealtime() - it.builtAt < MAX_AGE }
            ?: build(ctx, folders).also { snap = it }

    /** What the photos page / 추억 show instead of photos; null = photos are there (or the folders are just empty). */
    fun hint(folders: List<String>, s: Snap): String? = when {
        folders.isEmpty() || s.folders.isEmpty() -> "폴더를 골라 주세요"
        s.failed.isNotEmpty() && s.slides.values.all { it.isEmpty() } -> "다시 골라 주세요"
        else -> null
    }

    /** "primary:DCIM/Camera" -> "Camera" (when the provider gives no display name). */
    fun fallbackName(tree: String): String = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(tree)).substringAfterLast('/').substringAfterLast(':')
    }.getOrNull()?.ifBlank { null } ?: tree

    private fun build(ctx: Context, asked: List<String>): Snap {
        val cr = ctx.contentResolver
        val persisted = cr.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        val zone = ZoneId.systemDefault()
        val kept = ArrayList<String>(); val dropped = ArrayList<String>(); val failed = HashSet<String>()
        val slides = HashMap<String, List<Item>>(); val names = HashMap<String, String>(); val counts = HashMap<String, Int>()
        val byDay = HashMap<MonthDay, MutableList<Item>>()
        for (f in asked.distinct()) {
            val tree = Uri.parse(f)
            val rows = if (f in persisted) runCatching { list(cr, tree, f, zone) }.getOrNull() else null
            when (PhotoRules.grant(f in persisted, rows != null)) {
                PhotoRules.Grant.DROP -> { dropped += f; continue }
                PhotoRules.Grant.RETRY -> { kept += f; failed += f; continue }
                PhotoRules.Grant.KEEP -> kept += f
            }
            names[f] = runCatching { displayName(cr, tree) }.getOrNull() ?: fallbackName(f)
            counts[f] = rows!!.size
            slides[f] = rows.sortedByDescending { it.ms }.take(PER_FOLDER)
            for (r in rows) byDay.getOrPut(MonthDay.from(Instant.ofEpochMilli(r.ms).atZone(zone))) { ArrayList() } += r
        }
        // a dropped grant leaves the config too (the filter with it); the next get() finds this snap by [Snap.folders]
        if (dropped.isNotEmpty()) ConfigStore.update(ctx) { it.withPhotoFolders(it.photoFolders - dropped.toSet()) }
        return Snap(asked, kept, SystemClock.elapsedRealtime(), slides, byDay, names, counts, failed)
    }

    private fun displayName(cr: ContentResolver, tree: Uri): String? =
        cr.query(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** The folder's images + one sub-folder level; throws when a query fails (the caller keeps the folder as RETRY). */
    private fun list(cr: ContentResolver, tree: Uri, f: String, zone: ZoneId): List<Item> {
        val out = ArrayList<Item>()
        fun scan(docId: String, top: Boolean) {
            val dirs = ArrayList<String>()
            val c = cr.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId), COLS, null, null, null) ?: error("no cursor")
            c.use {
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue // .trashed-*, .pending-*, .thumbnails
                    val mime = c.getString(2).orEmpty()
                    if (mime == Document.MIME_TYPE_DIR) { if (top && !PhotoRules.skipDir(name)) dirs += id }
                    else if (mime.startsWith("image/")) {
                        val ms = PhotoRules.nameDate(name)?.atZone(zone)?.toInstant()?.toEpochMilli() ?: c.getLong(3)
                        out += Item(f, id, ms)
                    }
                }
            }
            dirs.forEach { scan(it, false) }
        }
        scan(DocumentsContract.getTreeDocumentId(tree), true)
        return out
    }

    private fun uri(i: Item): Uri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(i.folder), i.docId)

    /** Up to [n] random slideshow photos of [filter] (a picked folder) or of every folder, decoded to <= 1280 px. */
    fun photos(ctx: Context, s: Snap, filter: String?, n: Int): List<Photo> {
        val pool = if (filter != null && filter in s.slides) s.slides.getValue(filter) else s.slides.values.flatten()
        val out = ArrayList<Photo>(n)
        for (i in pool.shuffled()) {
            if (out.size >= n) break
            decode(ctx, s, i, Instant.ofEpochMilli(i.ms).atZone(ZoneId.systemDefault()).toLocalDate(), null)?.let { out += it }
        }
        return out
    }

    /**
     * A photo of today's month-day 1..15 years ago, or null. Only these few candidates read EXIF DateTimeOriginal: a
     * photo whose EXIF names another day is skipped; EXIF gives the year when present.
     */
    fun memory(ctx: Context, s: Snap, today: LocalDate): Photo? {
        val zone = ZoneId.systemDefault()
        val md = MonthDay.from(today)
        fun inRange(d: LocalDate) = MonthDay.from(d) == md && (today.year - d.year) in 1..15
        val cand = s.byDay[md].orEmpty().filter { inRange(Instant.ofEpochMilli(it.ms).atZone(zone).toLocalDate()) }
        for (i in cand.shuffled().take(10)) {
            val day = exifDay(ctx, uri(i)) ?: Instant.ofEpochMilli(i.ms).atZone(zone).toLocalDate()
            if (!inRange(day)) continue
            decode(ctx, s, i, day, today.year - day.year)?.let { return it }
        }
        return null
    }

    private fun exifDay(ctx: Context, u: Uri): LocalDate? = runCatching {
        ctx.contentResolver.openInputStream(u)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) }
            ?.let { LocalDate.parse(it.trim(), EXIF) }
    }.getOrNull()

    private fun decode(ctx: Context, s: Snap, i: Item, taken: LocalDate, yearsAgo: Int?): Photo? = runCatching {
        val u = uri(i)
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, u)) { dec, info, _ ->
            // decode straight to <= 1280 px (one bitmap, no full-size or scale copy); size is post-EXIF, rotation is kept
            val k = 1280f / maxOf(info.size.width, info.size.height)
            if (k < 1f) dec.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        // no reverse geocoding: the folder name stands in for the place, and the camera roll has none worth showing
        val place = (s.names[i.folder] ?: "").takeUnless { it.equals("Camera", true) }.orEmpty()
        Photo(bmp.asImageBitmap(), place, taken.format(DATE), yearsAgo, u.toString())
    }.getOrNull()
}
