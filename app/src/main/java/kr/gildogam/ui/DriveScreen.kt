package kr.gildogam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.gildogam.core.Korean
import kr.gildogam.dex.UiState

/**
 * The screen while the car is on the road: one glance tells what is being recorded.
 * Nothing here needs the driver; the buttons are for passengers and for parked time.
 */
@Composable
fun DriveScreen(
    ui: UiState,
    onRiders: () -> Unit,
    onOpenDex: () -> Unit,
    onEndJourney: () -> Unit,
    onToggleConsole: () -> Unit,
    consoleOpen: Boolean,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Road.Asphalt),
    ) {
        val wide = maxWidth > maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            TopBar(ui)
            Spacer(Modifier.height(14.dp))
            CenterLine()
            Spacer(Modifier.height(24.dp))
            if (wide) {
                Row(Modifier.weight(1f)) {
                    Column(Modifier.weight(1.5f).fillMaxHeight()) { RoadNow(ui) }
                    Spacer(Modifier.width(36.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) { Page(ui, onRiders, onOpenDex, onEndJourney) }
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Column(Modifier.weight(1f)) { RoadNow(ui) }
                    Spacer(Modifier.height(16.dp))
                    Page(ui, onRiders, onOpenDex, onEndJourney)
                }
            }
            Footer(ui, onToggleConsole, consoleOpen)
        }
        ui.justAcquired?.let { a ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Road.Asphalt.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .padding(24.dp),
            ) {
                CardFace(a, width = 300.dp)
            }
        }
    }
}

@Composable
private fun TopBar(ui: UiState) {
    val car = ui.car
    val journey = car.journey
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("길도감", color = Road.CenterLine, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(28.dp))
        val status = when {
            journey != null && car.driving -> "여정 기록 중"
            journey != null -> "잠시 정차 중"
            else -> "주차 중"
        }
        Text(status, color = if (journey != null) Road.Paint else Road.Faded, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(16.dp))
        Text(
            journey?.title ?: car.destination?.let { "$it 가는 길" } ?: "",
            color = Road.Faded,
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        car.remainMinutes?.takeIf { it > 0 }?.let {
            Text("도착까지 약 ${it}분", color = Road.Paint, fontSize = 22.sp)
        }
    }
}

/** Left: where on the road we are, in a few big words. */
@Composable
private fun ColumnScope.RoadNow(ui: UiState) {
    val car = ui.car
    val preview = car.preview
    when {
        car.inSegment != null -> {
            Text("지금 지나는 길", color = Road.CenterLine, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(car.inSegment!!, color = Road.Paint, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp)
            Text("끝까지 지나면 카드가 남아요", color = Road.Faded, fontSize = 22.sp)
        }
        car.upcoming != null && car.driving -> {
            Text("곧 만날 길", color = Road.CenterLine, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(car.upcoming!!, color = Road.Paint, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp)
        }
        car.journey == null && preview != null && preview.cards.isNotEmpty() -> {
            Text("이번 여정에서 만날 카드", color = Road.CenterLine, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${preview.cards.size}장 · 새 카드 ${preview.newCards}장",
                color = Road.Paint,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                preview.cards.forEach { seg ->
                    val owned = car.dex.has(seg.id)
                    Column(
                        Modifier
                            .background(if (owned) Road.Lane else Road.Shoulder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(seg.kind.mark(), color = Road.Faded, fontSize = 15.sp)
                        Text(seg.name, color = Road.Paint, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (owned) "다른 에디션 도전" else "새 카드", color = if (owned) Road.Faded else Road.CenterLine, fontSize = 15.sp)
                    }
                }
            }
        }
        car.journey != null -> {
            Text("여정 기록 중", color = Road.CenterLine, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("달린 길이 오늘의 페이지에 남고 있어요", color = Road.Paint, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp)
        }
        else -> {
            Text("출발하면 여정이 시작돼요", color = Road.Paint, fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp)
            Spacer(Modifier.height(8.dp))
            Text("내비에 목적지를 넣으면 이번 여정에서 만날 카드를 미리 알려드려요.", color = Road.Faded, fontSize = 22.sp, lineHeight = 30.sp)
        }
    }
    Spacer(Modifier.weight(1f))
    // What the app last said, as a caption for passengers.
    if (car.lastLine.isNotEmpty()) {
        Text(
            car.lastLine,
            color = Road.Faded,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val chips = buildList {
        if (car.raining) add(if ((car.outsideC ?: 99f) <= 1f) "눈 (와이퍼 작동)" else "비 (와이퍼 작동)")
        if (ui.clock.hour != null) add("시연 시간: ${ui.clock.label}")
        if (car.naviQuiet) add("내비 안내 중, 알림 보류")
    }
    if (chips.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            chips.forEach {
                Text(
                    it,
                    color = Road.Paint,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .background(Road.Lane, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Right: today's page and the passenger buttons. */
@Composable
private fun Page(ui: UiState, onRiders: () -> Unit, onOpenDex: () -> Unit, onEndJourney: () -> Unit) {
    val car = ui.car
    val journey = car.journey
    val today = journey?.let { j -> car.dex.acquisitions.filter { it.journeyId == j.id } }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Road.Shoulder, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Text("오늘의 페이지", color = Road.Faded, fontSize = 18.sp)
            Text(
                if (journey == null) "아직 시작 전" else "카드 ${today.size}장",
                color = Road.Paint,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            val riders = journey?.riders?.takeIf { it.isNotEmpty() }
            if (riders != null) Text("함께 탄 사람: ${Korean.joinListeners(riders)}", color = Road.Paint, fontSize = 18.sp)
            if (today.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    today.takeLast(4).reversed().joinToString("\n") { a ->
                        a.cardName + "  " + a.face.label
                    },
                    color = Road.Paint,
                    fontSize = 19.sp,
                    lineHeight = 28.sp,
                )
            }
        }
        BigButton("함께 탄 사람", "이름을 말하면 카드에 함께 남아요", Road.Lane, onClick = onRiders)
        BigButton(
            "여정 도감",
            if (car.parked) "지난 여정과 카드 보기" else "차를 세우면 열려요",
            Road.CenterLine,
            enabled = car.parked,
            onClick = onOpenDex,
        )
        if (journey != null && car.parked) {
            TextButton(onClick = onEndJourney) {
                Text("여기서 여정 마치기", color = Road.Faded, fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun BigButton(title: String, hint: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    val ink = if (color == Road.CenterLine) Road.Asphalt else Road.Paint
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = ink),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(hint, fontSize = 17.sp, color = ink.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun Footer(ui: UiState, onToggleConsole: () -> Unit, consoleOpen: Boolean) {
    val s = ui.sources
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "음성 ${s.tts}   인식 ${s.stt}   차량 ${s.vehicle}   와이퍼 ${s.wiper}   내비 ${s.navi}   위치 ${s.location}",
            color = Road.Faded,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToggleConsole) {
            Text(if (consoleOpen) "시연 콘솔 닫기" else "시연 콘솔", color = Road.Faded, fontSize = 17.sp)
        }
    }
}
