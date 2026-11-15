package kr.dot.catchpet.ai

/** Worker error code (or "net") + HTTP status -> what the user reads (plan R8, R18). */
fun aiMessage(code: String?, status: Int): String = when {
    code == "device" -> "오늘 그려 보기 5번을 다 썼어요. 내일 다시 그릴 수 있어요"
    code == "net" -> "인터넷에 연결되지 않았어요. 와이파이나 데이터를 켜고 다시 눌러 주세요"
    code == "purchase" || status == 402 -> "구매가 확인되지 않았어요. 돈은 나가지 않았어요"
    code == "limit" -> "지금 그리는 사람이 많아요. 몇 분 뒤 다시 눌러 주세요"
    code?.startsWith("bad") == true || status == 400 -> "이 사진은 그릴 수 없어요. 앱을 업데이트하거나 다른 녀석으로 해 보세요"
    else -> "지금은 그림을 못 그려요. 잠시 뒤 다시 눌러 주세요"
}
