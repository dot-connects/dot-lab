package kr.dot.wherepark.family

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object InviteShare {
    fun message(link: String, group: String? = null) = "어디댔어 " + (if (group == null) "" else "'$group' ") + "그룹에 초대해요. 링크를 누르면 돼요.\n$link"

    fun sendIntent(link: String, text: String = message(link)): Intent =
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)

    /** KakaoTalk first; no KakaoTalk (or it refuses) falls back to the chooser. [text] = the join request message too. */
    fun sendKakao(ctx: Context, link: String, text: String = message(link)) {
        try {
            ctx.startActivity(sendIntent(link, text).setPackage("com.kakao.talk").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            sendOther(ctx, link, text)
        }
    }

    fun sendOther(ctx: Context, link: String, text: String = message(link)) =
        ctx.startActivity(Intent.createChooser(sendIntent(link, text), "초대 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun copyCode(ctx: Context, code: String) =
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("invite", code))

    fun clipboardCode(ctx: Context): String? =
        JoinCode.fromText(ctx.getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString())

    fun qr(link: String, px: Int = 512): Bitmap {
        val m = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1))
        val pix = IntArray(px * px) { if (m.get(it % px, it / px)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pix, px, px, Bitmap.Config.ARGB_8888)
    }
}
