package kr.dot.wherepark.family

import android.content.Context
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.ApiState
import kr.dot.wherepark.data.GroupStore

/** UI-free join: contracts section 3 steps 1-4 live in [Api.join]; this adds the push start and the step 5 prompt data. */
object JoinFlow {
    /** Background token upload after create/join; tests replace it. */
    @Volatile var startPush: (Context) -> Unit = { c -> Thread { Push.start(c.applicationContext) }.start() }

    /** Group name and head count only: member names are never fetched before joining. */
    fun peek(code: String): Result<Pair<String, Int>> = Api.peek(code)

    /** [name] only matters for a phone with no device yet (the server ignores it otherwise). */
    fun join(ctx: Context, code: String, name: String): Result<String> =
        Api.join(ctx, code, name.trim().ifEmpty { null }).onSuccess { if (name.isNotBlank()) kr.dot.wherepark.ui.AppPrefs.setMyName(ctx, name.trim()); startPush(ctx) }

}
