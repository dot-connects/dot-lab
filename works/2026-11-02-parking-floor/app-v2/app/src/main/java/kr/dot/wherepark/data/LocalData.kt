package kr.dot.wherepark.data

import android.content.Context
import androidx.work.WorkManager
import java.io.File
import kr.dot.wherepark.car.CarLink
import kr.dot.wherepark.core.Park

/** Phone-local files and the full wipe behind 설정 > 내 데이터 지우기. */
object LocalData {
    fun photoFile(ctx: Context, carId: String, parkId: Long): File = File(ctx.filesDir, "photos/${carId}_$parkId.jpg")

    /**
     * The park's photo: the local file, else (server says photo=true) a download cached under the same name.
     * Older cached photos of that car go, except the previous record's (undo). Background thread only.
     */
    fun photo(ctx: Context, p: Park): File? {
        val f = photoFile(ctx, p.carId, p.id)
        if (f.exists() && f.length() > 0) return f
        if (!p.photo) return null
        val bytes = Api.photo(ctx, p.carId, p.id) ?: return null
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".part")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { tmp.delete(); return null }
        val keep = setOfNotNull(p.id, ParkStore.prev(ctx, p.carId)?.id).map { "${p.carId}_$it.jpg" }.toSet()
        f.parentFile?.listFiles { x -> x.name.startsWith("${p.carId}_") && x.name !in keep }?.forEach { it.delete() }
        return f
    }

    /**
     * Wipe this phone: stop sync work, drop every Bluetooth association we hold, then clear all prefs and the queue under the
     * queue lock. Clearing the queue bumps its generation, so a pull already in flight is discarded by Merge.state.
     */
    fun wipe(ctx: Context) {
        try {
            WorkManager.getInstance(ctx).apply { cancelUniqueWork("wp-sync"); cancelUniqueWork(kr.dot.wherepark.family.Push.PULL_WORK) }
        } catch (_: IllegalStateException) {
            // WorkManager not initialised (plain unit tests)
        }
        CarLinkStore.carIds(ctx).forEach { CarLink.unlink(ctx, it) }
        synchronized(SyncQueue) {
            Prefs.sp(ctx).edit().clear().commit()
            SyncQueue.clear(ctx)
        }
        File(ctx.filesDir, "photos").deleteRecursively()
    }
}
