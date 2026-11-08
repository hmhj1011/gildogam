package kr.gildogam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.gildogam.core.Acquisition
import kr.gildogam.core.Badge
import kr.gildogam.core.CardKind
import kr.gildogam.core.Edition

/** The yellow centre line: double solid with a dashed white lane beside it. Divides sections. */
@Composable
fun CenterLine(modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val y1 = size.height * 0.2f
        val y2 = size.height * 0.55f
        drawLine(Road.CenterLine, Offset(0f, y1), Offset(size.width, y1), strokeWidth = 3f)
        drawLine(Road.CenterLine, Offset(0f, y2), Offset(size.width, y2), strokeWidth = 3f)
        drawLine(
            Road.Paint.copy(alpha = 0.25f),
            Offset(0f, size.height - 1f),
            Offset(size.width, size.height - 1f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 22f)),
        )
    }
}

/** A collected card, face up: its edition colour, kind, name and the moment it was earned. */
@Composable
fun CardFace(a: Acquisition, width: Dp = 180.dp, modifier: Modifier = Modifier, showDate: String? = null) {
    val face = a.face.style()
    Column(
        modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .background(face.fill)
            .padding(14.dp),
    ) {
        Text(a.kind.mark(), color = face.ink.copy(alpha = 0.75f), fontSize = 15.sp)
        Text(
            a.cardName,
            color = face.ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 28.sp,
        )
        Spacer(Modifier.height(10.dp))
        val eds = Edition.entries.filter { it in a.editions }
        Text(eds.joinToString(" · ") { it.label } + " 에디션", color = face.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (Badge.FIRST in a.badges || Badge.FAMILY in a.badges) {
            Text(
                a.badges.sortedBy { it.ordinal }.joinToString(" · ") { it.label },
                color = face.ink.copy(alpha = 0.8f),
                fontSize = 14.sp,
            )
        }
        showDate?.let { Text(it, color = face.ink.copy(alpha = 0.7f), fontSize = 14.sp) }
    }
}

/** A road not met yet: an outline with its kind only. */
@Composable
fun CardBack(name: String, kind: CardKind, width: Dp = 180.dp, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, Road.Lane, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(kind.mark(), color = Road.Faded, fontSize = 15.sp)
        Text(name, color = Road.Faded, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 28.sp)
        Spacer(Modifier.height(10.dp))
        Text("아직 함께 지나지 않은 길", color = Road.Faded, fontSize = 15.sp)
    }
}

/** Six dots: which editions of this road are collected. */
@Composable
fun EditionDots(collected: Set<Edition>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Edition.entries.forEach { e ->
            val on = e in collected
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (on) e.style().fill else Road.Lane),
            )
        }
    }
}
