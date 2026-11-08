package kr.gildogam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.gildogam.core.Acquisition
import kr.gildogam.core.Badge
import kr.gildogam.core.CardKind
import kr.gildogam.core.Edition
import kr.gildogam.dex.UiState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** Colours of the light navigation map. */
private object MapInk {
    val Ground = Color(0xFFF6F4E6)
    val Street = Color(0xFFFFFFFF)
    val StreetEdge = Color(0xFFE3E0D2)
    val Highway = Color(0xFFF3E68A)
    val HighwayEdge = Color(0xFFE2CF6C)
    val Water = Color(0xFFA4C0F4)
    val Park = Color(0xFFC3EDBE)
    val Car = Color(0xFFE03A2E)
    val CarEdge = Color(0xFFB02A20)
    val Glass = Color(0xFF1B3550)
    val Title = Color(0xFF111111)
    val Chip = Color(0xFFFFFFFF)
    val ChipText = Color(0xFF1F2430)
    val ChipFaded = Color(0xFF6B7280)
}

/**
 * The screen on the road: a plain navigation map with our car on it. Nothing else asks for
 * the driver's eyes; when a road card is earned it slides in from the right for a few seconds
 * and the map comes back by itself.
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

    Box(Modifier.fillMaxSize().background(MapInk.Ground)) {
        MovingMap(speedKmh = car.speedKmh, modifier = Modifier.fillMaxSize())

        Text(
            "길도감",
            color = MapInk.Title,
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 96.dp, top = 40.dp),
        )

        StatusChip(ui, Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 48.dp))

        // Bottom: the last spoken line as a caption, and the few buttons the app has.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Leave room for the demo console when it is open.
                .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = if (consoleOpen) 150.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (car.lastLine.isNotBlank()) {
                    Text(
                        car.lastLine,
                        color = MapInk.ChipText,
                        fontSize = 19.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .background(MapInk.Chip.copy(alpha = 0.85f), RoundedCornerShape(50))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (journey != null && car.parked) MapButton("여정 마치기", onClick = onEndJourney)
                MapButton("함께 탄 사람", onClick = onRiders)
                MapButton("여정 도감", strong = true, enabled = car.parked, onClick = onOpenDex)
                MapButton(if (consoleOpen) "시연 닫기" else "시연", quiet = true, onClick = onToggleConsole)
            }
        }

        // The card alert. Keep the last card while it slides out.
        var lastCard by remember { mutableStateOf<Acquisition?>(null) }
        val acquired = ui.justAcquired
        if (acquired != null && acquired != lastCard) lastCard = acquired
        AnimatedVisibility(
            visible = ui.justAcquired != null,
            enter = slideInHorizontally { it / 2 } + fadeIn(),
            exit = slideOutHorizontally { it / 2 } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 96.dp, bottom = 40.dp),
        ) {
            lastCard?.let { RoadCardAlert(it) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Map

private const val BLOCK = 560f          // px between cross streets along the highway
private const val PX_PER_METRE = 1.6f
private const val HIGHWAY_ANGLE = -22f  // degrees; the highway runs up and to the right

/**
 * A stylised map that scrolls under a fixed car, as a navigation screen does. It is drawn,
 * not fetched: the prototype needs no map key, and the real app would sit on Pleos navigation.
 */
@Composable
private fun MovingMap(speedKmh: Float, modifier: Modifier) {
    val speed by rememberUpdatedState(speedKmh)
    var travelled by remember { mutableStateOf(0.0) } // pixels along the highway
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) {
                    val dt = (t - last) / 1e9
                    travelled += speed / 3.6 * dt * PX_PER_METRE
                }
                last = t
            }
        }
    }
    Canvas(modifier) {
        drawRect(MapInk.Ground)
        val anchor = Offset(size.width * 0.34f, size.height * 0.56f)
        val reach = hypot(size.width, size.height)
        rotate(HIGHWAY_ANGLE, pivot = anchor) {
            translate(anchor.x, anchor.y) {
                drawMapWorld(travelled, reach)
                drawCar()
            }
        }
    }
}

/** Deterministic noise per block so the same stretch always looks the same. */
private fun noise(k: Long, salt: Int): Float {
    var x = k * 0x9E3779B97F4A7C15uL.toLong() + salt * 0x632BE59BD9B4E019L
    x = (x xor (x ushr 31)) * 0x94D049BB133111EBuL.toLong()
    x = x xor (x ushr 29)
    return ((x ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat()
}

/** Draws the world in highway coordinates: u along the road, v across it, car at the origin. */
private fun DrawScope.drawMapWorld(travelled: Double, reach: Float) {
    val first = floor((travelled - reach) / BLOCK).toLong()
    val last = floor((travelled + reach) / BLOCK).toLong() + 1
    fun u(world: Double) = (world - travelled).toFloat()

    // Parks and rivers sit under the streets.
    for (k in first..last) {
        val u0 = u(k * BLOCK.toDouble())
        if (noise(k, 1) < 0.34f) {
            val below = noise(k, 2) < 0.5f
            val top = if (below) 70f else -330f
            drawRoundRect(MapInk.Street, Offset(u0 + 50f, top - 12f), Size(BLOCK - 100f, 284f), CornerRadius(40f))
            drawRoundRect(MapInk.Park, Offset(u0 + 62f, top), Size(BLOCK - 124f, 260f), CornerRadius(32f))
        }
    }
    for (k in first..last) {
        if (noise(k, 3) < 0.22f) {
            val u0 = u(k * BLOCK.toDouble()) + BLOCK * 0.25f
            val bend = (noise(k, 4) - 0.5f) * 500f
            val river = Path().apply {
                moveTo(u0 - 200f, -reach)
                cubicTo(u0 + bend, -200f, u0 - bend, 200f, u0 + 200f, reach)
            }
            drawPath(river, MapInk.Water, style = Stroke(width = 34f))
        }
    }

    // Streets: two running alongside, and cross streets at a slight slant.
    fun street(a: Offset, b: Offset, w: Float) {
        drawLine(MapInk.StreetEdge, a, b, strokeWidth = w + 6f)
        drawLine(MapInk.Street, a, b, strokeWidth = w)
    }
    for (k in first..last) {
        val u0 = u(k * BLOCK.toDouble())
        val slant = (noise(k, 5) - 0.5f) * 0.9f
        street(Offset(u0 - reach * slant, -reach), Offset(u0 + reach * slant, reach), 30f)
        if (noise(k, 6) < 0.5f) {
            val uh = u0 + BLOCK * 0.5f
            street(Offset(uh, 60f), Offset(uh + 120f, reach), 22f)
        }
    }
    street(Offset(-reach, -360f), Offset(reach, -360f), 34f)
    street(Offset(-reach, 320f), Offset(reach, 320f), 38f)

    // The highway we are on.
    drawLine(MapInk.HighwayEdge, Offset(-reach, 0f), Offset(reach, 0f), strokeWidth = 86f)
    drawLine(MapInk.Highway, Offset(-reach, 0f), Offset(reach, 0f), strokeWidth = 78f)
}

/** Our car, seen from above, facing along the highway. */
private fun DrawScope.drawCar() {
    val l = 116f
    val w = 58f
    val body = Offset(-l / 2, -w / 2)
    drawRoundRect(Color.Black.copy(alpha = 0.12f), body + Offset(4f, 6f), Size(l, w), CornerRadius(22f))
    drawRoundRect(MapInk.Car, body, Size(l, w), CornerRadius(22f))
    drawRoundRect(MapInk.CarEdge, body, Size(l, w), CornerRadius(22f), style = Stroke(3f))
    // windscreen, roof, rear window
    drawRoundRect(MapInk.Glass, Offset(14f, -w / 2 + 8f), Size(20f, w - 16f), CornerRadius(8f))
    drawRoundRect(MapInk.Car, Offset(-26f, -w / 2 + 8f), Size(40f, w - 16f), CornerRadius(6f))
    drawRoundRect(MapInk.Glass, Offset(-40f, -w / 2 + 10f), Size(14f, w - 20f), CornerRadius(6f))
    // mirrors
    drawRoundRect(MapInk.CarEdge, Offset(16f, -w / 2 - 6f), Size(8f, 8f), CornerRadius(3f))
    drawRoundRect(MapInk.CarEdge, Offset(16f, w / 2 - 2f), Size(8f, 8f), CornerRadius(3f))
}

// ---------------------------------------------------------------------------------------------
// Chrome on top of the map

/** One quiet line: what card is next, or what this trip will bring. */
@Composable
private fun StatusChip(ui: UiState, modifier: Modifier) {
    val car = ui.car
    val journey = car.journey
    val earned = journey?.let { j -> car.dex.acquisitions.count { it.journeyId == j.id } } ?: 0
    val preview = car.preview
    val next = car.inSegment ?: car.upcoming ?: preview?.cards
        ?.firstOrNull { seg -> journey == null || car.dex.acquisitions.none { it.journeyId == journey.id && it.cardId == seg.id } }
        ?.name
    val (title, detail) = when {
        journey != null -> (journey.title) to listOfNotNull(
            next?.let { (if (car.inSegment != null) "지나는 중 " else "다음 카드 ") + it },
            "카드 ${earned}장",
            car.remainMinutes?.takeIf { it > 0 }?.let { "도착까지 ${it}분" },
        ).joinToString(" · ")
        preview != null && preview.cards.isNotEmpty() ->
            "${preview.destination} 가는 길" to "이번 여정에서 만날 카드 ${preview.cards.size}장"
        else -> return
    }
    Column(
        modifier
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .background(MapInk.Chip, RoundedCornerShape(22.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(title, color = MapInk.ChipText, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(detail, color = MapInk.ChipFaded, fontSize = 18.sp, maxLines = 1)
    }
}

@Composable
private fun MapButton(text: String, strong: Boolean = false, quiet: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 60.dp).shadow(if (quiet) 0.dp else 4.dp, RoundedCornerShape(50)),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                strong -> MapInk.ChipText
                quiet -> MapInk.Chip.copy(alpha = 0.7f)
                else -> MapInk.Chip
            },
            contentColor = if (strong) Color.White else MapInk.ChipText,
            disabledContainerColor = MapInk.Chip.copy(alpha = 0.7f),
            disabledContentColor = MapInk.ChipFaded,
        ),
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// The card alert

private fun Edition.band(): Color = when (this) {
    Edition.BASIC -> Color(0xFFE89B65)
    Edition.RAIN -> Color(0xFF4F83B3)
    Edition.SNOW -> Color(0xFF6E9CC4)
    Edition.NIGHT -> Color(0xFF34397F)
    Edition.DAWN -> Color(0xFFC4859F)
    Edition.JAM -> Color(0xFFD9534F)
}

private fun Acquisition.oneLiner(): String {
    val day = when (face) {
        Edition.BASIC -> "맑은 날의"
        Edition.RAIN -> "비 오는 날의"
        Edition.SNOW -> "눈 내린 날의"
        Edition.NIGHT -> "깊은 밤의"
        Edition.DAWN -> "새벽녘의"
        Edition.JAM -> "느릿느릿"
    }
    val verb = if (kind == CardKind.SCENIC || kind == CardKind.REST_AREA || kind == CardKind.MY_ROAD) "방문!" else "통과!"
    return "$day $cardName $verb"
}

@Composable
private fun RoadCardAlert(a: Acquisition) {
    Column(
        Modifier
            .width(400.dp)
            .shadow(12.dp, RoundedCornerShape(40.dp))
            .background(Color(0xFFCBD2DC), RoundedCornerShape(40.dp))
            .padding(horizontal = 30.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CardIllustration(a.kind, a.face, Modifier.fillMaxWidth(0.86f).aspectRatio(1.1f))
        Spacer(Modifier.height(20.dp))
        Text(
            a.cardName,
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .background(a.face.band())
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(a.oneLiner(), color = Color(0xFF1F2430), fontSize = 19.sp, textAlign = TextAlign.Center)
        val marks = listOf("${a.face.label} 에디션") + a.badges.map(Badge::label)
        Text(marks.joinToString(" · "), color = Color(0xFF5B6475), fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private val Outline = Color(0xFF151515)

/** A line drawing of the road, with the weather or time of day it was earned in above it. */
@Composable
private fun CardIllustration(kind: CardKind, face: Edition, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.018f
        val line = Stroke(width = sw, cap = StrokeCap.Round)
        val horizon = h * 0.42f

        drawSky(face, Offset(w * 0.5f, horizon), w, sw)
        val ground = Offset(w * 0.06f, horizon)
        val gw = w * 0.88f
        val gh = h * 0.56f

        when (kind) {
            CardKind.SCENIC -> {
                drawRect(Color(0xFF7FB2DC), ground, Size(gw, gh))
                val land = Path().apply {
                    moveTo(ground.x, horizon); lineTo(ground.x + gw * 0.30f, horizon)
                    cubicTo(ground.x + gw * 0.05f, horizon + gh * 0.35f, ground.x + gw * 0.55f, horizon + gh * 0.6f, ground.x + gw * 0.40f, horizon + gh)
                    lineTo(ground.x, horizon + gh); close()
                }
                drawPath(land, Color(0xFF8DAA74))
                val road = Path().apply {
                    moveTo(ground.x + gw * 0.30f, horizon)
                    cubicTo(ground.x + gw * 0.12f, horizon + gh * 0.35f, ground.x + gw * 0.62f, horizon + gh * 0.55f, ground.x + gw * 0.46f, horizon + gh)
                }
                drawPath(road, Color(0xFFD5D8DD), style = Stroke(width = gw * 0.11f))
                drawPath(road, Outline, style = Stroke(width = sw * 0.6f, cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(sw * 2.4f, sw * 2f))))
                drawPath(land, Outline, style = line)
                for (i in 0 until 4) {
                    val y = horizon + gh * (0.18f + i * 0.2f)
                    val x0 = ground.x + gw * (0.62f + i * 0.03f)
                    val wave = Path().apply {
                        moveTo(x0, y)
                        quadraticBezierTo(x0 + gw * 0.06f, y - gh * 0.06f, x0 + gw * 0.12f, y)
                        quadraticBezierTo(x0 + gw * 0.18f, y + gh * 0.06f, x0 + gw * 0.24f, y)
                    }
                    drawPath(wave, Outline, style = line)
                }
            }
            CardKind.TUNNEL -> {
                drawGrass(ground, gw, gh)
                val hill = Path().apply {
                    moveTo(ground.x, horizon + gh * 0.55f)
                    cubicTo(ground.x + gw * 0.2f, horizon - gh * 0.25f, ground.x + gw * 0.8f, horizon - gh * 0.25f, ground.x + gw, horizon + gh * 0.55f)
                    close()
                }
                drawPath(hill, Color(0xFF6E9563)); drawPath(hill, Outline, style = line)
                drawRoad(Offset(w * 0.5f, horizon + gh * 0.42f), w * 0.12f, horizon + gh, w * 0.6f, sw)
                val arch = Path().apply {
                    moveTo(w * 0.36f, horizon + gh * 0.55f)
                    cubicTo(w * 0.36f, horizon + gh * 0.12f, w * 0.64f, horizon + gh * 0.12f, w * 0.64f, horizon + gh * 0.55f)
                    close()
                }
                drawPath(arch, Color(0xFF2B2F3A)); drawPath(arch, Outline, style = line)
            }
            CardKind.BRIDGE -> {
                drawRect(Color(0xFF7FB2DC), ground, Size(gw, gh))
                val deck = horizon + gh * 0.32f
                drawRect(Color(0xFFD5D8DD), Offset(ground.x, deck), Size(gw, gh * 0.08f))
                drawLine(Outline, Offset(ground.x, deck), Offset(ground.x + gw, deck), sw)
                drawLine(Outline, Offset(ground.x, deck + gh * 0.08f), Offset(ground.x + gw, deck + gh * 0.08f), sw)
                listOf(0.3f, 0.7f).forEach { f ->
                    val x = ground.x + gw * f
                    drawLine(Outline, Offset(x, horizon - gh * 0.25f), Offset(x, deck + gh * 0.5f), sw * 1.6f)
                    for (j in 1..3) {
                        drawLine(Outline, Offset(x, horizon - gh * 0.2f + j * 4f), Offset(x - gw * 0.07f * j, deck), sw * 0.6f)
                        drawLine(Outline, Offset(x, horizon - gh * 0.2f + j * 4f), Offset(x + gw * 0.07f * j, deck), sw * 0.6f)
                    }
                }
                drawWaves(ground, gw, gh, sw, from = 0.62f)
            }
            CardKind.PASS -> {
                val far = Path().apply {
                    moveTo(ground.x, horizon + gh * 0.4f); lineTo(ground.x + gw * 0.3f, horizon - gh * 0.1f)
                    lineTo(ground.x + gw * 0.55f, horizon + gh * 0.25f); lineTo(ground.x + gw * 0.75f, horizon - gh * 0.2f)
                    lineTo(ground.x + gw, horizon + gh * 0.3f); lineTo(ground.x + gw, horizon + gh); lineTo(ground.x, horizon + gh); close()
                }
                drawPath(far, Color(0xFF6E9563)); drawPath(far, Outline, style = line)
                val zig = Path().apply {
                    moveTo(w * 0.3f, horizon + gh); lineTo(w * 0.66f, horizon + gh * 0.78f)
                    lineTo(w * 0.36f, horizon + gh * 0.56f); lineTo(w * 0.6f, horizon + gh * 0.38f); lineTo(w * 0.5f, horizon + gh * 0.26f)
                }
                drawPath(zig, Color(0xFFD5D8DD), style = Stroke(width = w * 0.06f))
                drawPath(zig, Outline, style = Stroke(width = sw * 0.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(sw * 2f, sw * 2f))))
            }
            CardKind.BORDER -> {
                drawGrass(ground, gw, gh)
                drawRoad(Offset(w * 0.5f, horizon), w * 0.04f, horizon + gh, w * 0.5f, sw)
                val px = w * 0.8f
                drawLine(Outline, Offset(px, horizon + gh * 0.85f), Offset(px, horizon - gh * 0.25f), sw * 1.4f)
                drawRoundRect(Color(0xFF2E7D4F), Offset(px - w * 0.15f, horizon - gh * 0.42f), Size(w * 0.24f, gh * 0.24f), CornerRadius(sw))
                drawRoundRect(Outline, Offset(px - w * 0.15f, horizon - gh * 0.42f), Size(w * 0.24f, gh * 0.24f), CornerRadius(sw), style = line)
                drawLine(Color.White, Offset(px - w * 0.1f, horizon - gh * 0.3f), Offset(px + w * 0.04f, horizon - gh * 0.3f), sw)
            }
            CardKind.REST_AREA -> {
                drawGrass(ground, gw, gh)
                drawRect(Color(0xFFD5D8DD), Offset(ground.x, horizon + gh * 0.55f), Size(gw, gh * 0.45f))
                val bx = w * 0.26f; val by = horizon + gh * 0.12f; val bw = w * 0.48f; val bh = gh * 0.43f
                drawRect(Color(0xFFF4EBDD), Offset(bx, by), Size(bw, bh)); drawRect(Outline, Offset(bx, by), Size(bw, bh), style = line)
                val roof = Path().apply { moveTo(bx - w * 0.04f, by); lineTo(bx + bw * 0.5f, by - gh * 0.24f); lineTo(bx + bw + w * 0.04f, by); close() }
                drawPath(roof, Color(0xFFE08B4F)); drawPath(roof, Outline, style = line)
                drawRect(Color(0xFF7FB2DC), Offset(bx + bw * 0.12f, by + bh * 0.25f), Size(bw * 0.3f, bh * 0.35f))
                drawRect(Outline, Offset(bx + bw * 0.6f, by + bh * 0.35f), Size(bw * 0.22f, bh * 0.65f), style = line)
            }
            CardKind.MY_ROAD -> {
                drawGrass(ground, gw, gh)
                drawRoad(Offset(w * 0.5f, horizon + gh * 0.2f), w * 0.05f, horizon + gh, w * 0.5f, sw)
                val hx = w * 0.42f; val hy = horizon - gh * 0.02f
                drawRect(Color(0xFFF4EBDD), Offset(hx, hy), Size(w * 0.16f, gh * 0.2f)); drawRect(Outline, Offset(hx, hy), Size(w * 0.16f, gh * 0.2f), style = line)
                val roof = Path().apply { moveTo(hx - w * 0.02f, hy); lineTo(hx + w * 0.08f, hy - gh * 0.14f); lineTo(hx + w * 0.18f, hy); close() }
                drawPath(roof, Color(0xFFE08B4F)); drawPath(roof, Outline, style = line)
            }
        }
        drawLine(Outline, Offset(ground.x - sw, horizon), Offset(ground.x + gw + sw, horizon), sw * 1.2f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawGrass(ground: Offset, gw: Float, gh: Float) =
    drawRect(Color(0xFF8DAA74), ground, Size(gw, gh))

private fun DrawScope.drawRoad(top: Offset, topWidth: Float, bottomY: Float, bottomWidth: Float, sw: Float) {
    val road = Path().apply {
        moveTo(top.x - topWidth / 2, top.y); lineTo(top.x + topWidth / 2, top.y)
        lineTo(top.x + bottomWidth / 2, bottomY); lineTo(top.x - bottomWidth / 2, bottomY); close()
    }
    drawPath(road, Color(0xFFD5D8DD))
    drawPath(road, Outline, style = Stroke(sw))
    drawLine(Outline, Offset(top.x, top.y + (bottomY - top.y) * 0.15f), Offset(top.x, bottomY), sw * 0.6f,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(sw * 3f, sw * 2.5f)))
}

private fun DrawScope.drawWaves(ground: Offset, gw: Float, gh: Float, sw: Float, from: Float) {
    for (i in 0 until 3) {
        val y = ground.y + gh * (from + i * 0.12f)
        val x0 = ground.x + gw * (0.1f + (i % 2) * 0.35f)
        val p = Path().apply {
            moveTo(x0, y)
            quadraticBezierTo(x0 + gw * 0.07f, y - gh * 0.05f, x0 + gw * 0.14f, y)
            quadraticBezierTo(x0 + gw * 0.21f, y + gh * 0.05f, x0 + gw * 0.28f, y)
        }
        drawPath(p, Outline, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** The sky above the horizon: what the edition was earned under. */
private fun DrawScope.drawSky(face: Edition, horizonCentre: Offset, w: Float, sw: Float) {
    val c = horizonCentre
    val r = w * 0.15f
    val line = Stroke(sw, cap = StrokeCap.Round)
    fun rays(color: Color = Outline) {
        for (i in 0..6) {
            val a = Math.toRadians(180.0 + 15 + i * 25.0)
            val p1 = Offset(c.x + (r * 1.35f) * cos(a).toFloat(), c.y + (r * 1.35f) * sin(a).toFloat())
            val p2 = Offset(c.x + (r * 1.85f) * cos(a).toFloat(), c.y + (r * 1.85f) * sin(a).toFloat())
            drawLine(color, p1, p2, sw * 1.2f, cap = StrokeCap.Round)
        }
    }
    fun halfDisc(color: Color, inner: Color?) {
        drawArc(color, 180f, 180f, true, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
        inner?.let { drawArc(it, 180f, 180f, true, Offset(c.x - r * 0.75f, c.y - r * 0.75f), Size(r * 1.5f, r * 1.5f)) }
        drawArc(Outline, 180f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = line)
    }
    fun cloud(cx: Float, cy: Float, s: Float) {
        val fill = Color(0xFFF2F4F7)
        listOf(Offset(-0.5f, 0.1f) to 0.42f, Offset(0f, -0.15f) to 0.55f, Offset(0.5f, 0.1f) to 0.42f).forEach { (o, rr) ->
            drawCircle(fill, s * rr, Offset(cx + o.x * s, cy + o.y * s))
            drawCircle(Outline, s * rr, Offset(cx + o.x * s, cy + o.y * s), style = line)
        }
        drawRect(fill, Offset(cx - s * 0.5f, cy - s * 0.05f), Size(s, s * 0.55f))
        drawLine(Outline, Offset(cx - s * 0.75f, cy + s * 0.5f), Offset(cx + s * 0.75f, cy + s * 0.5f), sw, cap = StrokeCap.Round)
    }
    when (face) {
        Edition.BASIC -> { rays(); halfDisc(Color(0xFFF6D365), Color(0xFFF0AE57)) }
        Edition.DAWN -> { halfDisc(Color(0xFFF4B6A6), Color(0xFFE88C8C)) }
        Edition.NIGHT -> {
            drawCircle(Color(0xFFF6D365), r * 0.8f, Offset(c.x + w * 0.12f, c.y - r * 1.4f))
            drawCircle(Color(0xFFCBD2DC), r * 0.7f, Offset(c.x + w * 0.12f + r * 0.4f, c.y - r * 1.6f))
            listOf(Offset(-0.3f, -0.35f), Offset(-0.15f, -0.2f), Offset(0.32f, -0.15f), Offset(-0.38f, -0.12f)).forEach { o ->
                val p = Offset(c.x + o.x * w, c.y + o.y * w)
                drawLine(Outline, p - Offset(sw * 1.5f, 0f), p + Offset(sw * 1.5f, 0f), sw * 0.7f)
                drawLine(Outline, p - Offset(0f, sw * 1.5f), p + Offset(0f, sw * 1.5f), sw * 0.7f)
            }
        }
        Edition.RAIN, Edition.SNOW, Edition.JAM -> {
            val cy = c.y - r * 1.5f
            cloud(c.x, cy, r * 1.4f)
            if (face == Edition.RAIN) {
                for (i in -2..2) {
                    val x = c.x + i * r * 0.45f
                    drawLine(Color(0xFF3F74A8), Offset(x, cy + r * 1.0f), Offset(x - r * 0.12f, cy + r * 1.35f), sw, cap = StrokeCap.Round)
                }
            } else if (face == Edition.SNOW) {
                for (i in -2..2) {
                    val p = Offset(c.x + i * r * 0.45f, cy + r * 1.15f + abs(i % 2) * r * 0.15f)
                    drawLine(Outline, p - Offset(sw * 1.4f, 0f), p + Offset(sw * 1.4f, 0f), sw * 0.6f)
                    drawLine(Outline, p - Offset(0f, sw * 1.4f), p + Offset(0f, sw * 1.4f), sw * 0.6f)
                }
            }
        }
    }
}
