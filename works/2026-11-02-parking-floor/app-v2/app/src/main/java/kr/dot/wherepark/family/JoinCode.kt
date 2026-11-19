package kr.dot.wherepark.family

import android.net.Uri

/** Invite code shapes: 12 chars of [A-Z2-9], shown as XXXX-XXXX-XXXX. */
object JoinCode {
    val PATTERN = Regex("[A-Z2-9]{4}-?[A-Z2-9]{4}-?[A-Z2-9]{4}")

    /** First code-shaped string in [text] (clipboard, pasted message), dashed; null when none. */
    fun fromText(text: String?): String? =
        text?.uppercase()?.let { PATTERN.find(it) }?.value?.let(::format)

    /** Link or intent URI: the fragment (`/j#CODE`) or the `c` query (`/j?c=CODE`, the KakaoTalk "open app" path). */
    fun fromUri(uri: Uri?): String? {
        uri ?: return null
        return listOf(uri.fragment, uri.getQueryParameter("c")).firstNotNullOfOrNull { s -> s?.trim()?.uppercase()?.takeIf { PATTERN.matches(it) } }?.let(::format)
    }

    fun format(code: String): String = code.replace("-", "").chunked(4).joinToString("-")
}
