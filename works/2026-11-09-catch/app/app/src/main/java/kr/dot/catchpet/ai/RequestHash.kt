package kr.dot.catchpet.ai

import java.security.MessageDigest

/** Binds a Play Integrity token to one request: sha256("path\ndevice\nsha256(body)"), as the Worker's requestHash. */
object RequestHash {
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    fun of(path: String, device: String, body: String): String = sha("$path\n$device\n${sha(body)}")
}
