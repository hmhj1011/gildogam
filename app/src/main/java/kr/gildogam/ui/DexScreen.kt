package kr.gildogam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kr.gildogam.core.Acquisition
import kr.gildogam.core.Catalog
import kr.gildogam.core.CardKind
import kr.gildogam.core.Dex
import kr.gildogam.core.Edition
import kr.gildogam.core.Journey
import kr.gildogam.core.Korean
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFormat = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREA)
private val timeFormat = SimpleDateFormat("a h:mm", Locale.KOREA)
private val shortFormat = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA)

/** One entry of the card list: a catalogue road or a 내 길 born from repeated trips. */
private data class Entry(val id: String, val name: String, val kind: CardKind, val story: String)

private fun entries(dex: Dex): List<Entry> =
    Catalog.segments.map { Entry(it.id, it.name, it.kind, it.story) } +
        dex.myRoads.filter { r -> dex.has("my-${r.id}") }.map { r ->
            Entry("my-${r.id}", "내 길: ${r.name}", CardKind.MY_ROAD, "자주 달린 길이 카드가 되었어요. 지금까지 이 길을 ${r.trips}번 함께 달렸어요.")
        }

private enum class Tab(val label: String) { PAGES("여정 페이지"), CARDS("카드 도감") }

/** The journey book. Opens only while parked. */
@Composable
fun DexScreen(dex: Dex, onBack: () -> Unit, onNote: (Acquisition) -> Unit) {
    var tab by remember { mutableStateOf(Tab.PAGES) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Road.Asphalt)
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("여정 도감", color = Road.CenterLine, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(32.dp))
            Tab.entries.forEach { t ->
                TextButton(onClick = { tab = t }) {
                    Text(
                        t.label,
                        color = if (tab == t) Road.Paint else Road.Faded,
                        fontSize = 22.sp,
                        fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("주행 화면으로", color = Road.CenterLine, fontSize = 20.sp) }
        }
        Spacer(Modifier.height(12.dp))
        CenterLine()
        Spacer(Modifier.height(20.dp))
        when (tab) {
            Tab.PAGES -> Pages(dex, onNote)
            Tab.CARDS -> Cards(dex, onNote)
        }
    }
}

// ------------------------------------------------------------------ journey pages

@Composable
private fun Pages(dex: Dex, onNote: (Acquisition) -> Unit) {
    val pages = dex.journeys.sortedByDescending { it.startedAtMs }
    if (pages.isEmpty()) {
        Empty("아직 남긴 여정이 없어요. 출발하면 오늘의 페이지가 시작돼요.")
        return
    }
    var selectedId by remember { mutableStateOf(pages.first().id) }
    val selected = pages.firstOrNull { it.id == selectedId } ?: pages.first()
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
            items(pages) { j ->
                val active = j.id == selected.id
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(if (active) Road.Lane else Road.Shoulder, RoundedCornerShape(16.dp))
                        .clickable { selectedId = j.id }
                        .padding(18.dp),
                ) {
                    Text(dayFormat.format(Date(j.startedAtMs)), color = Road.Faded, fontSize = 16.sp)
                    Text(j.title, color = Road.Paint, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("카드 ${j.acquisitionIds.size}장", color = Road.CenterLine, fontSize = 17.sp)
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        PageDetail(dex, selected, onNote, Modifier.weight(1.8f).fillMaxHeight())
    }
}

@Composable
private fun PageDetail(dex: Dex, j: Journey, onNote: (Acquisition) -> Unit, modifier: Modifier) {
    val cards = j.acquisitionIds.mapNotNull { id -> dex.acquisitions.firstOrNull { it.id == id } }
    LazyColumn(modifier) {
        item {
            Text(dayFormat.format(Date(j.startedAtMs)), color = Road.Faded, fontSize = 18.sp)
            Text(j.title, color = Road.Paint, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            val end = j.endedAtMs
            val span = if (end != null) "${timeFormat.format(Date(j.startedAtMs))} 출발 · ${timeFormat.format(Date(end))} 도착" else "기록 중"
            Text(span, color = Road.Faded, fontSize = 18.sp)
            if (j.riders.isNotEmpty()) Text("함께 탄 사람  ${Korean.joinListeners(j.riders)}", color = Road.Paint, fontSize = 19.sp)
            Spacer(Modifier.height(18.dp))
            if (cards.isEmpty()) Text("이 여정에서는 카드를 만나지 못했어요. 길은 그대로 기록돼 있어요.", color = Road.Faded, fontSize = 19.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                cards.forEach { CardFace(it, width = 200.dp, showDate = timeFormat.format(Date(it.atMs))) }
            }
            Spacer(Modifier.height(18.dp))
        }
        items(cards) { a -> NoteRow(a, onNote) }
    }
}

@Composable
private fun NoteRow(a: Acquisition, onNote: (Acquisition) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(a.cardName, color = Road.Paint, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(a.note?.let { "\"$it\"" } ?: "아직 한마디가 없어요", color = if (a.note != null) Road.Paint else Road.Faded, fontSize = 18.sp)
        }
        TextButton(onClick = { onNote(a) }) {
            Text(if (a.note == null) "한마디 남기기" else "다시 남기기", color = Road.CenterLine, fontSize = 18.sp)
        }
    }
}

// ------------------------------------------------------------------ cards

@Composable
private fun Cards(dex: Dex, onNote: (Acquisition) -> Unit) {
    val list = entries(dex)
    var selectedId by remember { mutableStateOf(list.first().id) }
    val selected = list.firstOrNull { it.id == selectedId } ?: list.first()
    val owned = list.count { dex.has(it.id) }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
            item {
                Text("모은 길 ${owned} / ${list.size}", color = Road.Faded, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            items(list) { e ->
                val active = e.id == selected.id
                val has = dex.has(e.id)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .background(if (active) Road.Lane else Road.Shoulder, RoundedCornerShape(14.dp))
                        .clickable { selectedId = e.id }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.kind.mark(), color = Road.Faded, fontSize = 14.sp)
                        Text(e.name, color = if (has) Road.Paint else Road.Faded, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    EditionDots(dex.editionsOf(e.id))
                }
            }
        }
        Spacer(Modifier.width(32.dp))
        CardDetail(dex, selected, onNote, Modifier.weight(1.6f).fillMaxHeight())
    }
}

@Composable
private fun CardDetail(dex: Dex, e: Entry, onNote: (Acquisition) -> Unit, modifier: Modifier) {
    val history = dex.acquisitions.filter { it.cardId == e.id }.sortedByDescending { it.atMs }
    val collected = dex.editionsOf(e.id)
    LazyColumn(modifier) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                val latest = history.firstOrNull()
                if (latest != null) CardFace(latest, width = 240.dp) else CardBack(e.name, e.kind, width = 240.dp)
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.story, color = Road.Paint, fontSize = 20.sp, lineHeight = 29.sp)
                    Spacer(Modifier.height(14.dp))
                    Text("모은 에디션", color = Road.Faded, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Edition.entries.forEach { ed ->
                            val on = ed in collected
                            Text(
                                ed.label,
                                color = if (on) ed.style().ink else Road.Faded,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .background(if (on) ed.style().fill else Road.Shoulder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                if (history.isEmpty()) "이 길을 끝까지 달리면 첫 카드가 생겨요." else "함께 지난 기록 ${history.size}번",
                color = Road.Faded,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
        items(history) { a ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    shortFormat.format(Date(a.atMs)) + "  " + Edition.entries.filter { it in a.editions }.joinToString(" · ") { it.label },
                    color = Road.Paint,
                    fontSize = 19.sp,
                )
                val page = dex.journeys.firstOrNull { it.id == a.journeyId }
                val who = if (a.riders.isNotEmpty()) "  ·  " + Korean.joinListeners(a.riders) else ""
                Text((page?.title ?: "여정") + who, color = Road.Faded, fontSize = 17.sp)
                NoteRow(a, onNote)
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, color = Road.Faded, fontSize = 24.sp, lineHeight = 34.sp)
}
