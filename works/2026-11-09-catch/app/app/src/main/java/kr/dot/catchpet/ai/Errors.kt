package kr.dot.catchpet.ai

/** Worker error code (or "net") + HTTP status -> what the user reads (plan R8, R18). */
fun aiMessage(code: String?, status: Int): String = when {
    code == "device" -> "오늘 AI 미리보기 5번을 다 썼어요"
    code == "net" -> "인터넷 연결을 확인해 주세요"
    code == "purchase" || status == 402 -> "구매가 확인되지 않았어요"
    code?.startsWith("bad") == true || status == 400 -> "앱을 업데이트해 주세요"
    else -> "AI 그림을 잠시 못 그려요"
}
