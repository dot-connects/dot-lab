package kr.dot.standby

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One CSV for the whole measurement: time,kind,detail. Every component appends here. */
object EventLog {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun file(ctx: Context): File = File(ctx.filesDir, "events.csv")

    @Synchronized
    fun write(ctx: Context, kind: String, detail: String) {
        val f = file(ctx)
        val fresh = !f.exists()
        val clean = detail.replace("\"", "'").replace("\n", " ")
        f.appendText(
            (if (fresh) "time,kind,detail\n" else "") +
                "${LocalDateTime.now().format(stamp)},$kind,\"$clean\"\n",
        )
    }

    fun tail(ctx: Context, lines: Int): List<String> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines().drop(1).takeLast(lines).reversed()
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
