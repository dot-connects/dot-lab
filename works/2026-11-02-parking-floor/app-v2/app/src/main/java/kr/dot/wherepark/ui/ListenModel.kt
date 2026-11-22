package kr.dot.wherepark.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kr.dot.wherepark.core.Ask
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.CarResolver
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Palette
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParseResult
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Shape
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.core.SpotParser
import kr.dot.wherepark.core.Style
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.GroupStore
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.data.WidgetRefresh

/** Why the manual-entry sheet is up; decides its title and the extra action (settings link, language pack download). */
enum class ManualWhy { FAILED, EDIT, NO_MIC, NO_PACK, NO_RECOGNIZER }

sealed interface Sheet {
    /** [heard] = finished recognizer sessions joined, [partial] = the one still running (grey tail). */
    data class Listening(val partial: String = "", val rms: Float = -2f, val heard: String = "") : Sheet
    data class AskMic(val denied: Int) : Sheet
    /** [ask] = one gentle question about the saved default; [options] = its one-tap answers. */
    data class Saved(val park: Park, val sent: String?, val photo: Boolean = false, val ask: Ask = Ask.NONE, val options: List<Spot> = emptyList()) : Sheet
    data class Manual(val why: ManualWhy, val prefill: Spot?, val floors: List<Floor>, val editId: Long? = null, val downloading: Boolean = false) : Sheet
    data class Practice(val raw: String, val result: ParseResult) : Sheet
}

/**
 * The listen sheet's state machine (plan-v2 task 6). The Activity feeds recognizer events in and runs [relisten] / [close];
 * everything that touches the stores lives here so it runs under Robolectric without a recognizer.
 */
class ListenModel(
    private val ctx: Context,
    private val carId: String?,
    private val btCarId: String?,
    from: String?,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val SENT = "그룹에 보냈어요"
        const val PENDING = "신호가 잡히면 그룹에 보내요"
        const val UNDO_MS = 8_000L

        /** Task 7 points this at the car's widget Style; null = the ROW default. */
        @Volatile var styleOf: (Context, String) -> Style? = { _, _ -> null }
    }

    val practice = from == "practice"
    var state: Sheet by mutableStateOf(Sheet.Listening())
    var car: Car? by mutableStateOf(resolve(null))
        private set
    var close: () -> Unit = {}
    var relisten: () -> Unit = {}
    /** Stops the recognizer without a result (정지). */
    var halt: () -> Unit = {}

    private var fails = 0
    private var lastName: String? = null
    private var undoOver = false
    /** [고치기] or [사진] keeps the sheet open past the undo window. */
    var held = false
        private set

    val cars: List<Car> get() = CarStore.cars(ctx)
    val style: Style get() = car?.let { styleOf(ctx, it.id) } ?: Palette.default(Shape.ROW)

    private fun resolve(spoken: String?): Car? = CarResolver.resolve(carId, btCarId, spoken, CarStore.cars(ctx), ParkStore.lastUsedCarId(ctx))

    // recognizer events

    /** A recognizer session (re)started: what was heard so far stays. */
    fun listening() { state = (state as? Sheet.Listening)?.copy(partial = "") ?: Sheet.Listening() }
    fun partial(text: String?) { (state as? Sheet.Listening)?.let { state = it.copy(partial = text.orEmpty()) } }
    fun rms(db: Float) { (state as? Sheet.Listening)?.let { state = it.copy(rms = db) } }

    /** The on-device recognizer ends on silence: keep its text and start the next session at once (listen until 정지). */
    fun result(raw: String) {
        val s = state as? Sheet.Listening ?: return
        fails = 0
        state = s.copy(heard = join(s.heard, raw), partial = "")
        relisten()
    }

    /** 정지 (or 60 s): parse everything heard, save at once; the default is saved even when a question follows. */
    fun stop() {
        val s = state as? Sheet.Listening ?: return
        halt()
        finish(join(s.heard, s.partial))
    }

    private fun join(a: String, b: String) = listOf(a, b).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

    private fun finish(raw: String) {
        val r = SpotParser.parse(raw, cars.map { it.name })
        if (practice) { state = Sheet.Practice(raw, r); return }
        val spot = r.spot ?: run { state = Sheet.Manual(ManualWhy.FAILED, null, floors()); return }
        lastName = r.carName
        if (r.carName != null) car = resolve(r.carName)
        save(spot, raw, r.carName)
        (state as? Sheet.Saved)?.let { state = it.copy(ask = r.ask, options = r.options) }
    }

    /** Silence ends a session without text: listen on. Other errors: retry twice, then stop with what was heard. */
    fun fail() {
        if (state !is Sheet.Listening) return
        if (++fails < 3) { listening(); relisten() } else stop()
    }

    fun error(code: Int) = when (code) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> micDenied(2)
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> packMissing()
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (state is Sheet.Listening) { listening(); relisten() } else Unit
        else -> fail()
    }

    fun micDenied(times: Int) {
        state = if (times >= 2) Sheet.Manual(ManualWhy.NO_MIC, null, floors()) else Sheet.AskMic(times)
    }
    fun packMissing() { state = Sheet.Manual(ManualWhy.NO_PACK, null, floors()) }
    fun noRecognizer() { state = Sheet.Manual(ManualWhy.NO_RECOGNIZER, null, floors()) }
    fun downloading() { (state as? Sheet.Manual)?.let { state = it.copy(downloading = true) } }

    // user actions

    fun again() { fails = 0; held = false; undoOver = false; state = Sheet.Listening(); relisten() }

    fun manual() { state = Sheet.Manual(ManualWhy.FAILED, null, floors()) }

    /** One-tap answer on the saved sheet: the same record (park id) gets the answered spot. */
    fun answer(spot: Spot) {
        val s = state as? Sheet.Saved ?: return
        if (ParkStore.latest(ctx, s.park.carId)?.id == s.park.id) commit(s.park.copy(spot = spot), s.photo)
        if (undoOver && !held) close()
    }

    /** [아니요]: the saved default was right. */
    fun keep() {
        val s = state as? Sheet.Saved ?: return
        state = s.copy(ask = Ask.NONE, options = emptyList())
        if (undoOver && !held) close()
    }

    fun saveManual(spot: Spot) {
        if (practice) return close()
        val m = state as? Sheet.Manual ?: return
        if (m.editId != null) {
            val cur = car?.let { ParkStore.latest(ctx, it.id) }?.takeIf { it.id == m.editId } ?: return save(spot, null, null)
            commit(cur.copy(spot = spot))
            if (editOnly) close()
        } else save(spot, null, lastName)
    }

    /** 차 상세 [고치기]: straight to the edit form for [p]; saving closes the sheet (same park id, contracts "fix"). */
    private var editOnly = false
    fun edit(p: Park) {
        car = cars.firstOrNull { it.id == p.carId } ?: car
        held = true; editOnly = true
        state = Sheet.Manual(ManualWhy.EDIT, p.spot, floors(), editId = p.id)
    }

    fun fix() {
        val s = state as? Sheet.Saved ?: return
        held = true
        state = Sheet.Manual(ManualWhy.EDIT, s.park.spot, floors(), editId = s.park.id)
    }

    /** Contracts section 3: ParkStore.undo picks queue replacement or an Undo op by sendStarted. Only while ours is still the latest. */
    fun undo() {
        val s = state as? Sheet.Saved ?: return
        undoPark(s.park)
        close()
    }

    fun switchCar(id: String) {
        val s = state as? Sheet.Saved ?: return
        if (id == s.park.carId) return
        val to = cars.firstOrNull { it.id == id } ?: return
        held = true
        // the photo follows the record: park it aside, undo drops the old op, then re-queue it under the new park id
        val old = photoFile(s.park)
        val moved = if (s.photo && old.exists()) File(old.path + ".move").takeIf { old.renameTo(it) } else null
        undoPark(s.park)
        car = to
        save(s.park.spot, s.park.raw, null, to)
        val n = state as? Sheet.Saved ?: return
        if (moved != null) {
            val nf = photoFile(n.park)
            if (moved.renameTo(nf)) {
                SyncQueue.add(ctx, Op.PutPhoto(n.park.carId, n.park.id, nf.path))
                SyncQueue.kick(ctx)
                state = n.copy(photo = true)
            }
        }
    }

    /** Process death while the camera was open: back to the saved sheet of that park. */
    fun restore(p: Park) {
        held = true
        car = cars.firstOrNull { it.id == p.carId } ?: car
        state = Sheet.Saved(p, sentLine(p))
    }

    fun refreshSent() { (state as? Sheet.Saved)?.let { state = it.copy(sent = sentLine(it.park)) } }

    /** Undo window over: close unless the user asked for more or a question is still open. */
    fun timeout(parkId: Long) {
        val s = state as? Sheet.Saved ?: return
        if (s.park.id != parkId) return
        undoOver = true
        if (!held && s.ask == Ask.NONE) close()
    }

    fun hold() { held = true }

    // saving

    private fun save(spot: Spot, raw: String?, spoken: String?, force: Car? = null) {
        val c = force ?: resolve(spoken) ?: firstCar()
        car = c
        val me = GroupStore.me(ctx)
        val t = now()
        commit(Park(t, c.id, spot, t, me?.id ?: "", me?.name ?: "", raw, false, ParkState.PARKED))
    }

    private fun commit(p: Park, photo: Boolean = false) {
        synchronized(SyncQueue) {
            ParkStore.save(ctx, p)
            SyncQueue.add(ctx, Op.PutPark(p))
        }
        SyncQueue.kick(ctx)
        WidgetRefresh.request(ctx)
        state = Sheet.Saved(p, sentLine(p), photo)
    }

    /** Also drops the undone park's photo op and file, so no orphan upload is left behind. */
    private fun undoPark(p: Park) {
        val undone = synchronized(SyncQueue) {
            if (ParkStore.latest(ctx, p.carId)?.id != p.id) return@synchronized false
            ParkStore.undo(ctx, p.carId)
            SyncQueue.dropKey(ctx, Op.PutPhoto(p.carId, p.id, "").key)
            true
        }
        if (undone) photoFile(p).delete()
        SyncQueue.kick(ctx)
        WidgetRefresh.request(ctx)
    }

    /** No car yet (sheet opened before onboarding finished): one local car so the record is not lost. */
    private fun firstCar(): Car {
        val c = Car(kr.dot.wherepark.core.newCarId(), "내 차", 0xFFF4F1EA.toInt(), null, 0)
        CarStore.upsert(ctx, c)
        SyncQueue.add(ctx, Op.PutCar(c))
        return c
    }

    /** Null = no family to send to (local only), otherwise sent once the park op has left the queue. */
    fun sentLine(p: Park): String? {
        if (!GroupStore.joined(ctx)) return null
        return if (SyncQueue.ops(ctx).any { it is Op.PutPark && it.park.id == p.id }) PENDING else SENT
    }

    private fun floors(): List<Floor> = car?.let { c -> ParkStore.history(ctx, c.id).mapNotNull { it.spot.floor }.distinct().take(4) }.orEmpty()

    // photo

    fun photoFile(p: Park): File = File(ctx.filesDir, "photos/${p.carId}_${p.id}.jpg").also { it.parentFile?.mkdirs() }

    /** After ACTION_IMAGE_CAPTURE: long side 1600, JPEG 80, upright; queue the upload; drop photos older than prev. */
    fun photoTaken(p: Park, f: File) {
        if (!f.exists() || f.length() == 0L) return
        shrinkLocal(f)
        SyncQueue.add(ctx, Op.PutPhoto(p.carId, p.id, f.path))
        SyncQueue.kick(ctx)
        val keep = listOfNotNull(ParkStore.latest(ctx, p.carId)?.id, ParkStore.prev(ctx, p.carId)?.id).map { "${p.carId}_$it.jpg" }.toSet()
        f.parentFile?.listFiles { x -> x.name.startsWith("${p.carId}_") && x.name !in keep }?.forEach { it.delete() }
        (state as? Sheet.Saved)?.takeIf { it.park.id == p.id }?.let { state = it.copy(photo = true) }
    }

    private fun shrinkLocal(f: File) {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, b)
        var sample = 1
        while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= 1600) sample *= 2
        val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return
        val long = maxOf(src.width, src.height)
        val m = Matrix()
        if (long > 1600) m.postScale(1600f / long, 1600f / long)
        val orientation = runCatching { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }
}
