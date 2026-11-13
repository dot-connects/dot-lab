package kr.dot.catchpet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Where a person's photo goes next: kept on this phone, or sent out for an AI picture. */
enum class PersonUse { DEX, AI }

/**
 * A face photo is personal information (operator 11-13): before a caught person goes into the dex, and before a
 * person's photo is sent for an AI picture, the player confirms the person agreed. onDone(true) only when ticked.
 */
@Composable
fun PersonConsent(use: PersonUse, onDone: (Boolean) -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text(if (use == PersonUse.DEX) "사람을 뽑았어요" else "사람 사진으로 AI 그림 그리기") },
        text = {
            Column {
                Text(
                    if (use == PersonUse.DEX)
                        "얼굴이 나온 사진은 개인정보예요. 이 사진은 이 폰의 도감에만 저장돼요. 찍힌 사람에게 허락받은 경우에만 넣어 주세요."
                    else
                        "그림을 그리려면 이 사진이 우리 서버를 거쳐 OpenAI 로 보내져요. 우리 서버는 사진을 저장하지 않고, " +
                            "OpenAI 는 안전 점검을 위해 최대 30일 보관할 수 있어요. 찍힌 사람에게 허락받은 경우에만 그려 주세요.",
                )
                Row(
                    Modifier.padding(top = 12.dp).clickable { agreed = !agreed },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Text("이 사람에게 허락받았어요")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(true) }, enabled = agreed) { Text(if (use == PersonUse.DEX) "도감에 넣기" else "그리기") }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }) { Text(if (use == PersonUse.DEX) "버리기" else "취소") } },
    )
}
