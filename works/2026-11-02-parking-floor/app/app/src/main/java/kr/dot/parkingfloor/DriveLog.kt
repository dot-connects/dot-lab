package kr.dot.parkingfloor

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A short breadcrumb trail of what the background parts did, shown on the home screen for diagnosis. */
object DriveLog {
    private const val FILE = "drive.log"
    private const val KEEP = 60

    @Synchronized
    fun add(context: Context, line: String) {
        try {
            val f = File(context.filesDir, FILE)
            val stamp = SimpleDateFormat("M/d HH:mm:ss", Locale.KOREA).format(Date())
            val lines = (if (f.exists()) f.readLines() else emptyList()) + "$stamp $line"
            f.writeText(lines.takeLast(KEEP).joinToString("\n"))
        } catch (_: Throwable) {
        }
    }

    fun recent(context: Context, n: Int): List<String> = try {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readLines()?.takeLast(n)?.reversed().orEmpty()
    } catch (_: Throwable) {
        emptyList()
    }
}
