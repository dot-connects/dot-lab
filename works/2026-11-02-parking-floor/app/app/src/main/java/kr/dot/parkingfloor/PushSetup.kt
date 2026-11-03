package kr.dot.parkingfloor

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit

/**
 * Firebase is set up by hand from build fields (local, gitignored firebase.properties) instead of the
 * google-services plugin, so the app still builds and syncs by pulling when Firebase is not configured.
 */
object PushSetup {
    val configured: Boolean get() = BuildConfig.FB_APP_ID.isNotEmpty()

    fun init(context: Context) {
        if (!configured || FirebaseApp.getApps(context).isNotEmpty()) return
        try {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FB_API_KEY)
                    .setApplicationId(BuildConfig.FB_APP_ID)
                    .setProjectId(BuildConfig.FB_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FB_SENDER_ID)
                    .build(),
            )
            FirebaseMessaging.getInstance().token.addOnSuccessListener { Family.setToken(context, it) }
        } catch (e: Exception) {
            DriveLog.add(context, "firebase init failed: ${e.javaClass.simpleName}")
        }
    }

    /** Blocking token read for a background thread; null when Firebase is off or slow. */
    fun token(context: Context): String? {
        if (!configured) return null
        return try {
            init(context)
            com.google.android.gms.tasks.Tasks.await(FirebaseMessaging.getInstance().token, 10, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        }
    }
}

/** Wakes on a family data message, pulls the park, redraws the widget, tells the user. */
class ParkPushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        try {
            when (message.data["type"]) {
                "park" -> Family.pullNow(this)?.let { Family.notify(this, it) }
                "members" -> Family.pullNow(this)
            }
        } catch (e: Exception) {
            DriveLog.add(this, "family pull failed: ${e.javaClass.simpleName}")
        }
    }

    override fun onNewToken(token: String) = Family.setToken(this, token)
}
