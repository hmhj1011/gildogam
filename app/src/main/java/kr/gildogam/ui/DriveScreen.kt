package kr.gildogam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import kr.gildogam.core.Acquisition
import kr.gildogam.core.CardKind
import kr.gildogam.dex.UiState

/** One stop on today's road strip. */
private data class Stop(val id: String, val name: String, val kind: CardKind, val got: Acquisition?)

/**
 * The screen on the road. One big line says where we are; one strip shows this trip's cards,
 * lit as they are earned. Nothing here needs the driver.
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
    val car = ui.car
    val journey = car.journey
    // While parked after a trip, keep showing the page just finished.
    val shown = journey ?: if (car.parked) car.dex.journeys.maxByOrNull { it.endedAtMs ?: it.startedAtMs } else null
    val earned = shown?.let { j -> car.dex.acquisitions.filter { it.journeyId == j.id } }.orEmpty()

    val preview = car.preview
    val stops: List<Stop> = buildList {
        preview?.cards?.forEach { seg -> add(Stop(seg.id, seg.name, seg.kind, earned.lastOrNull { it.cardId == seg.id })) }
        earned.filter { a -> none { it.id == a.cardId } }.forEach { a -> add(Stop(a.cardId, a.cardName, a.kind, a)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Road.Asphalt),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 28.dp),
        ) {
            TopBar(ui, cards = earned.size, showCount = shown != null)
            Spacer(Modifier.height(14.dp))
            CenterLine()
            Spacer(Modifier.weight(1f))

            Headline(
                ui,
                shownTitle = if (journey == null) shown?.title else null,
                earnedCount = earned.size,
                nextStop = stops.firstOrNull { it.got == null && it.name != car.inSegment }?.name,
            )

            if (stops.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                RoadStrip(stops, current = car.inSegment)
            }

            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The last spoken line, as a caption for passengers and silent demo videos.
                Text(
                    car.lastLine,
                    color = Road.Faded,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(24.dp))
                if (journey != null && car.parked) {
                    TextButton(onClick = onEndJourney) { Text("여정 마치기", color = Road.Faded, fontSize = 18.sp) }
                    Spacer(Modifier.width(8.dp))
                }
                PillButton("함께 탄 사람", Road.Lane, Road.Paint, onClick = onRiders)
                Spacer(Modifier.width(12.dp))
                PillButton("여정 도감", Road.CenterLine, Road.Asphalt, enabled = car.parked, onClick = onOpenDex)
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onToggleConsole) {
                    Text(if (consoleOpen) "시연 닫기" else "시연", color = Road.Faded, fontSize = 17.sp)
                }
            }
        }

        ui.justAcquired?.let { a ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Road.Asphalt.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                CardFace(a, width = 320.dp)
            }
        }
    }
}

@Composable
private fun TopBar(ui: UiState, cards: Int, showCount: Boolean) {
    val car = ui.car
    val journey = car.journey
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("길도감", color = Road.CenterLine, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(20.dp))
        val (label, fill, ink) = when {
            journey != null && car.driving -> Triple("기록 중", Road.CenterLine, Road.Asphalt)
            journey != null -> Triple("잠시 정차", Road.Lane, Road.Paint)
            else -> Triple("주차 중", Road.Shoulder, Road.Faded)
        }
        Text(
            label,
            color = ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(fill, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 5.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            journey?.title ?: car.destination?.let { "$it 가는 길" } ?: "",
            color = Road.Faded,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val right = listOfNotNull(
            if (showCount) "카드 ${cards}장" else null,
            car.remainMinutes?.takeIf { it > 0 && journey != null }?.let { "도착까지 ${it}분" },
            if (car.raining) (if ((car.outsideC ?: 99f) <= 1f) "눈" else "비") else null,
        ).joinToString("   ")
        Text(right, color = Road.Paint, fontSize = 20.sp)
    }
}

/** The one thing to read: where on the road we are. */
@Composable
private fun Headline(ui: UiState, shownTitle: String?, earnedCount: Int, nextStop: String?) {
    val car = ui.car
    val preview = car.preview
    val (label, big) = when {
        car.inSegment != null -> "지나는 중" to car.inSegment!!
        car.upcoming != null && car.driving -> "곧 만날 길" to car.upcoming!!
        car.journey != null && nextStop != null -> "다음 길" to nextStop
        car.journey != null -> "여정 기록 중" to "달린 길이 오늘의 페이지에 남고 있어요"
        preview != null && preview.cards.isNotEmpty() -> {
            val newCards = preview.cards.count { !car.dex.has(it.id) }
            "이번 여정에서 만날 카드" to (if (newCards > 0) "${preview.cards.size}장, 새 카드 ${newCards}장" else "${preview.cards.size}장, 새 에디션 도전")
        }
        shownTitle != null -> "방금 마친 여정 · $shownTitle" to "카드 ${earnedCount}장"
        else -> "여정 대기" to "출발하면 여정이 시작돼요"
    }
    Text(label, color = Road.CenterLine, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(
        big,
        color = Road.Paint,
        fontSize = 64.sp,
        lineHeight = 72.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** This trip's cards in driving order: lit in their edition colour once earned. */
@Composable
private fun RoadStrip(stops: List<Stop>, current: String?) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stops.forEach { s ->
            val got = s.got
            val style = got?.face?.style()
            val isCurrent = current == s.name
            Column(
                Modifier
                    .widthIn(min = 150.dp)
                    .background(style?.fill ?: Road.Shoulder, RoundedCornerShape(14.dp))
                    .then(if (isCurrent) Modifier.border(2.dp, Road.CenterLine, RoundedCornerShape(14.dp)) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(s.kind.mark(), color = style?.ink?.copy(alpha = 0.75f) ?: Road.Faded, fontSize = 15.sp)
                Text(
                    s.name,
                    color = style?.ink ?: if (isCurrent) Road.Paint else Road.Faded,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    got?.face?.label?.let { "$it 에디션" } ?: if (isCurrent) "지나는 중" else " ",
                    color = style?.ink ?: Road.CenterLine,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun PillButton(text: String, fill: Color, ink: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = ink),
    ) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
    }
}
