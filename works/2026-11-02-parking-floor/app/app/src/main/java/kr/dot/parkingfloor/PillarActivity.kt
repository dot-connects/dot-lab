package kr.dot.parkingfloor

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.io.File

/**
 * "기둥 사진" and "위치 입력". The photo goes to a cache file, the code and one pillar colour are read from it,
 * and the file is deleted whatever happens. Only the text and the colour are kept.
 */
class PillarActivity : Activity() {
    companion object {
        const val EXTRA_TYPE = "type"
        private const val REQ_SHOT = 1
    }

    private var park: Park? = null
    private var color = 0
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var swatch: TextView
    private val shot by lazy { File(File(cacheDir, "shot").apply { mkdirs() }, "pillar.jpg") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        park = ParkStore.latest(this)
        val current = park
        if (current == null) {
            Toast.makeText(this, "주차 기록이 없어요", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        build(current)
        if (!intent.getBooleanExtra(EXTRA_TYPE, false) && savedInstanceState == null) takePhoto()
    }

    private fun build(current: Park) {
        val d = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * d).toInt()
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "기둥 번호나 위치"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        status = TextView(this).apply { textSize = 14f; text = "예: C-12, 가-07, 3번 출구 옆" }
        root.addView(status)
        input = EditText(this).apply {
            setText(current.pillar)
            setSelection(text.length)
            textSize = 24f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            id = 2001
        }
        root.addView(input, MATCH_PARENT, WRAP_CONTENT)
        swatch = TextView(this).apply { textSize = 14f; gravity = Gravity.CENTER_VERTICAL; visibility = android.view.View.GONE }
        root.addView(swatch)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply { text = "사진 다시"; isAllCaps = false; setOnClickListener { takePhoto() } },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(Button(this).apply { text = "저장"; isAllCaps = false; setOnClickListener { save() } },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(row, MATCH_PARENT, WRAP_CONTENT)
        setContentView(root)
    }

    private fun takePhoto() {
        try {
            val uri = FileProvider.getUriForFile(this, "kr.dot.parkingfloor.files", shot)
            val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(camera, REQ_SHOT)
        } catch (_: Exception) {
            status.text = "카메라를 열 수 없어요. 직접 입력해 주세요."
        }
    }

    @Deprecated("plain Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_SHOT) return
        if (resultCode != RESULT_OK || !shot.exists()) {
            shot.delete()
            return
        }
        val bitmap = try {
            load(shot)
        } catch (_: Exception) {
            null
        } finally {
            shot.delete()
        }
        if (bitmap == null) {
            status.text = "사진을 못 읽었어요. 직접 입력해 주세요."
            return
        }
        color = PillarColor.dominant(bitmap)
        showColor()
        status.text = "글자를 읽는 중…"
        TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val blocks = text.textBlocks.flatMap { b -> b.lines }.map { l ->
                    val box = l.boundingBox
                    PillarText.Block(l.text, if (box == null) 0 else box.width() * box.height())
                }
                val code = PillarText.best(blocks, park?.floor?.let(FloorMath::labelOf))
                if (code != null) {
                    input.setText(code)
                    input.setSelection(code.length)
                    status.text = "읽은 번호가 맞으면 저장하세요. 사진은 지웠어요."
                } else {
                    status.text = "번호를 못 찾았어요. 직접 입력해 주세요. 사진은 지웠어요."
                }
            }
            .addOnFailureListener { status.text = "글자 인식을 못 했어요. 직접 입력해 주세요." }
    }

    private fun showColor() {
        if (color == 0) {
            swatch.visibility = android.view.View.GONE
            return
        }
        swatch.visibility = android.view.View.VISIBLE
        swatch.text = "  이 층 기둥 색으로 기억"
        val dot = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 6 * resources.displayMetrics.density
            val s = (20 * resources.displayMetrics.density).toInt()
            setSize(s, s)
        }
        swatch.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
    }

    /** Decodes at about 1600 px on the long side and applies the EXIF rotation. */
    private fun load(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val degrees = when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
    }

    private fun save() {
        val current = park ?: return finish()
        ParkStore.setPillar(this, current.id, input.text.toString().trim(), color)
        ParkWidget.refresh(this)
        finish()
    }

    override fun onDestroy() {
        shot.delete()
        super.onDestroy()
    }
}

/** The pillar's paint colour: the most common saturated hue in the middle of the photo, or 0 for grey pillars. */
object PillarColor {
    fun dominant(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 120, 120 * bitmap.height / bitmap.width.coerceAtLeast(1), true)
        val x0 = small.width * 3 / 10
        val y0 = small.height * 3 / 10
        val buckets = IntArray(12)
        val sums = Array(12) { FloatArray(3) }
        var total = 0
        val hsv = FloatArray(3)
        for (y in y0 until small.height - y0) for (x in x0 until small.width - x0) {
            val c = small.getPixel(x, y)
            total++
            Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.35f || hsv[2] < 0.3f) continue
            val b = (hsv[0] / 30f).toInt().coerceIn(0, 11)
            buckets[b]++
            sums[b][0] += hsv[0]; sums[b][1] += hsv[1]; sums[b][2] += hsv[2]
        }
        val top = buckets.indices.maxBy { buckets[it] }
        if (total == 0 || buckets[top] < total * 0.08) return 0
        val n = buckets[top].toFloat()
        return Color.HSVToColor(floatArrayOf(sums[top][0] / n, (sums[top][1] / n).coerceAtLeast(0.55f), (sums[top][2] / n).coerceAtLeast(0.75f)))
    }
}
