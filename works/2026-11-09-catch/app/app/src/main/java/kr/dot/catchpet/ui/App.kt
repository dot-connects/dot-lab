package kr.dot.catchpet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Screen { CATCH, DEX, DETAIL, PET }

/** The whole app: one screen at a time, switched by a single state. Back goes to the catch screen. */
@Composable
fun App() {
    var screen by rememberSaveable { mutableStateOf(Screen.CATCH) }
    var entryId by rememberSaveable { mutableStateOf<String?>(null) }
    val sink = remember { CountingSink() }
    BackHandler(enabled = screen != Screen.CATCH) {
        screen = if (screen == Screen.DETAIL) Screen.DEX else Screen.CATCH
    }
    when (screen) {
        Screen.CATCH -> CatchScreen(sink, NoCritters, sink.count) { screen = Screen.DEX }
        Screen.DEX -> Placeholder("도감", "펫 설정" to { screen = Screen.PET }) { screen = Screen.CATCH }
        Screen.DETAIL -> Placeholder("녀석 ${entryId ?: ""}", null) { screen = Screen.DEX }
        Screen.PET -> Placeholder("펫", null) { screen = Screen.CATCH }
    }
}

/** Stand-in until the Dex / Detail / Pet screens land. */
@Composable
private fun Placeholder(title: String, extra: Pair<String, () -> Unit>?, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF101114)).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = 24.sp)
        Text("준비 중이에요", color = Color(0xFF9AA0AB), fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        extra?.let { (label, go) -> Button(onClick = go) { Text(label) } }
        Button(onClick = onBack) { Text("돌아가기") }
    }
}
