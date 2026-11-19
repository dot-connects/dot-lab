package kr.dot.wherepark.family

import android.content.Context
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.ApiState
import kr.dot.wherepark.data.FamilyStore

/** UI-free join: contracts section 3 steps 1-4 live in [Api.join]; this adds the push start and the step 5 prompt data. */
object JoinFlow {
    /** Background token upload after create/join; tests replace it. */
    @Volatile var startPush: (Context) -> Unit = { c -> Thread { Push.start(c.applicationContext) }.start() }

    /** Group name and head count only: member names are never fetched before joining. */
    fun peek(code: String): Result<Pair<String, Int>> = Api.peek(code)

    fun join(ctx: Context, code: String, name: String): Result<ApiState> =
        Api.join(ctx, code, name.trim()).onSuccess { startPush(ctx) }

    /** Cars of a family that was only this phone, to offer to the new one ("차 n대를 새 가족으로 가져갈까요?"). */
    fun carryOver(ctx: Context): List<Car> = FamilyStore.carryOver(ctx)
}
