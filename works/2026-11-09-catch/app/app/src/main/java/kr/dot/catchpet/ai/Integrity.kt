package kr.dot.catchpet.ai

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kr.dot.catchpet.BuildConfig

/**
 * Play Integrity (standard requests) for every non-dev AI call (plan-release Task 7). The Worker checks the verdict:
 * the Play-installed app, our signing cert, a real device, and this request's hash. null = no token (the Worker
 * answers 403 "integrity"); CLOUD_PROJECT 0 = not linked yet (wizard menu 7).
 */
object Integrity {
    private val lock = Mutex()
    private var provider: StandardIntegrityTokenProvider? = null

    private suspend fun provider(ctx: Context): StandardIntegrityTokenProvider = lock.withLock {
        provider ?: IntegrityManagerFactory.createStandard(ctx.applicationContext)
            .prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(BuildConfig.CLOUD_PROJECT).build())
            .await().also { provider = it }
    }

    suspend fun token(ctx: Context, hash: String): String? {
        if (BuildConfig.CLOUD_PROJECT == 0L) return null
        repeat(2) { attempt ->   // a stale provider fails once: prepare again, then give up
            try {
                val t: StandardIntegrityToken = provider(ctx).request(StandardIntegrityTokenRequest.builder().setRequestHash(hash).build()).await()
                return t.token()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Integrity", "token failed (try ${attempt + 1}): ${e.message}")
                lock.withLock { provider = null }
            }
        }
        return null
    }
}
