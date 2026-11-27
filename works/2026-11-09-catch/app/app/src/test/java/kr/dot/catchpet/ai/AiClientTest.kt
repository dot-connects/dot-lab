package kr.dot.catchpet.ai

import android.content.Context
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AiClientTest {
    @Test fun bodyCarriesThePictureAsOneBase64Line() {
        val png = ByteArray(3000) { (it * 31).toByte() }    // long enough that a wrapping encoder would break lines
        val body = AiClient.body(png, mapOf("kind" to "dog", "device" to "dev-abc12345"))
        val img = JSONObject(body).getString("image")
        assertFalse(img.contains('\n'))
        assertTrue(Regex("^[A-Za-z0-9+/=]+$").matches(img))   // the Worker's goodImage
        assertArrayEquals(png, Base64.decode(img, Base64.DEFAULT))
        assertEquals("dog", JSONObject(body).getString("kind"))
    }

    @Test fun deviceIdIsMadeOnceAndFitsTheWorker() {
        val ctx = RuntimeEnvironment.getApplication()
        val id = AiClient.deviceId(ctx)
        assertTrue(id, Regex("^dev-[a-z0-9]{24}$").matches(id))
        assertEquals(id, AiClient.deviceId(ctx))
    }

    // a reinstall or "clear data" wipes the prefs but not ANDROID_ID: the same id, so the Worker's free counts hold
    @Test fun deviceIdSurvivesClearDataWhenAndroidIdIsThere() {
        val ctx = RuntimeEnvironment.getApplication()
        Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ANDROID_ID, "a1b2c3d4e5f60718")
        val id = AiClient.deviceId(ctx)
        assertTrue(id, Regex("^dev-[a-z0-9]{24}$").matches(id))
        assertFalse(id.contains("a1b2c3d4e5f60718"))                  // hashed, never the raw id
        ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(id, AiClient.deviceId(ctx))
        Settings.Secure.putString(ctx.contentResolver, Settings.Secure.ANDROID_ID, "ffff000011112222")
        assertTrue(id != AiClient.deviceId(ctx))
    }
}
