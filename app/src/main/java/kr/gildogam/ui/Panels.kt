package kr.gildogam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.gildogam.core.CarEvent
import kr.gildogam.core.DemoTrip
import kr.gildogam.core.DemoTrips
import kr.gildogam.core.Gear
import kr.gildogam.dex.Ask
import kr.gildogam.dex.DemoClock
import kr.gildogam.dex.Scenario
import kr.gildogam.dex.UiState

/** Shown while the microphone is open. The text box answers when speech recognition is missing. */
@Composable
fun AskPanel(ask: Ask, heard: String, onTyped: (String) -> Unit, onCancel: () -> Unit) {
    var typed by remember(ask) { mutableStateOf("") }
    val (title, example) = when (ask) {
        is Ask.Riders -> "함께 탄 사람의 이름을 말씀해 주세요" to "예) 엄마 아빠 지우"
        is Ask.Note -> "'${ask.cardName}'에 남길 한마디를 말씀해 주세요" to "예) 터널에서 숨 참기 성공"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Road.Shoulder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 40.dp, vertical = 22.dp),
    ) {
        Text("듣고 있어요", color = Road.CenterLine, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Road.Paint, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(heard.ifBlank { example }, color = if (heard.isBlank()) Road.Faded else Road.Paint, fontSize = 22.sp)
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("말 대신 입력할 수도 있어요", fontSize = 18.sp) },
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { if (typed.isNotBlank()) onTyped(typed) }) { Text("남기기", fontSize = 20.sp) }
            TextButton(onClick = onCancel) { Text("취소", color = Road.Faded, fontSize = 20.sp) }
        }
    }
}

@Composable
fun NoticeBar(text: String, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Road.Lane)
            .padding(horizontal = 40.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = Road.Paint, fontSize = 20.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("닫기", color = Road.CenterLine, fontSize = 18.sp) }
        }
    }
}

/**
 * Recreates every situation without a real car, for the emulator, the demo video and judges.
 * The slim bar plays a whole situation in one tap; "더보기" opens the single controls.
 * Every control sends the same event the vehicle, navigation and location adapters send.
 */
@Composable
fun DemoConsole(
    ui: UiState,
    onScenario: (Scenario) -> Unit,
    onEvent: (CarEvent) -> Unit,
    onPlay: (DemoTrip) -> Unit,
    onStop: () -> Unit,
    onDemoMode: (Boolean) -> Unit,
    onClock: (DemoClock) -> Unit,
    onReset: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Road.Shoulder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(horizontal = 40.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("시연", color = Road.Faded, fontSize = 18.sp, modifier = Modifier.width(56.dp))
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Scenario.entries.forEach { sc ->
                    val active = ui.playing == sc.label
                    TextButton(
                        onClick = { onScenario(sc) },
                        modifier = Modifier.background(if (active) Road.CenterLine else Road.Lane, RoundedCornerShape(14.dp)),
                    ) {
                        Column {
                            Text(sc.label, color = if (active) Road.Asphalt else Road.Paint, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(sc.hint, color = if (active) Road.Asphalt else Road.Faded, fontSize = 14.sp)
                        }
                    }
                }
                if (ui.playing != null) Chip("멈춤") { onStop() }
            }
            TextButton(onClick = { more = !more }) {
                Text(if (more) "접기" else "더보기", color = Road.Faded, fontSize = 17.sp)
            }
        }
        if (more) {
            ConsoleRow("경로") {
                DemoTrips.all.forEach { trip -> Chip(trip.title) { onPlay(trip) } }
            }
            ConsoleRow("차량") {
                Chip("출발 (D)") { onEvent(CarEvent.GearChanged(Gear.DRIVE)) }
                Chip("주차 (P)") { onEvent(CarEvent.GearChanged(Gear.PARK)) }
                Chip("정체 8km/h") { onEvent(CarEvent.Speed(8f)) }
                Chip("주행 90km/h") { onEvent(CarEvent.Speed(90f)) }
                Chip("와이퍼 켬") { onEvent(CarEvent.Wiper(true)) }
                Chip("와이퍼 끔") { onEvent(CarEvent.Wiper(false)) }
                Chip("바깥 -2°C") { onEvent(CarEvent.OutsideTemp(-2f)) }
                Chip("바깥 12°C") { onEvent(CarEvent.OutsideTemp(12f)) }
            }
            ConsoleRow("내비·시간") {
                Chip("회전 안내 구간") { onEvent(CarEvent.NaviQuiet(true)) }
                Chip("안내 구간 끝") { onEvent(CarEvent.NaviQuiet(false)) }
                DemoClock.entries.forEach { c -> Chip(if (ui.clock == c) "✓ ${c.label}" else c.label) { onClock(c) } }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("빠른 시연", color = Road.Faded, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Switch(checked = ui.demoMode, onCheckedChange = onDemoMode)
                Spacer(Modifier.width(16.dp))
                Chip("도감 비우기") { onReset() }
                Spacer(Modifier.width(16.dp))
                val s = ui.sources
                Text(
                    "음성 ${s.tts} · 인식 ${s.stt} · 차량 ${s.vehicle} · 와이퍼 ${s.wiper} · 내비 ${s.navi} · 위치 ${s.location}",
                    color = Road.Faded,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConsoleRow(title: String, content: @Composable () -> Unit) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Road.Faded, fontSize = 17.sp, modifier = Modifier.width(96.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.background(Road.Lane, RoundedCornerShape(12.dp))) {
        Text(label, color = Road.Paint, fontSize = 17.sp)
    }
}
