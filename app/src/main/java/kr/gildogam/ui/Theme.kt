package kr.gildogam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kr.gildogam.core.CardKind
import kr.gildogam.core.Edition

/**
 * Road at night: tunnel-blue asphalt, the yellow centre line as the one accent,
 * and card colours taken from what each edition looks like on the road.
 */
object Road {
    val Asphalt = Color(0xFF1C2333)
    val Shoulder = Color(0xFF252E42)
    val Lane = Color(0xFF313C55)
    val CenterLine = Color(0xFFF2C14E)
    val Paint = Color(0xFFEDEFF3)
    val Faded = Color(0xFF8D98AD)
    val Brake = Color(0xFFE5484D)
}

data class EditionStyle(val fill: Color, val ink: Color)

fun Edition.style(): EditionStyle = when (this) {
    Edition.BASIC -> EditionStyle(Color(0xFFD9DEE5), Color(0xFF1C2333)) // concrete
    Edition.RAIN -> EditionStyle(Color(0xFF4F83B3), Color(0xFFFFFFFF)) // wet road
    Edition.SNOW -> EditionStyle(Color(0xFFE6F0F7), Color(0xFF23415C)) // snow bank
    Edition.NIGHT -> EditionStyle(Color(0xFF34397F), Color(0xFFF2C14E)) // street lights
    Edition.DAWN -> EditionStyle(Color(0xFFC4859F), Color(0xFF26172A)) // first light
    Edition.JAM -> EditionStyle(Color(0xFFE07B39), Color(0xFF231206)) // brake lights
}

fun CardKind.mark(): String = when (this) {
    CardKind.TUNNEL -> "터널"
    CardKind.BRIDGE -> "다리"
    CardKind.PASS -> "고개"
    CardKind.BORDER -> "경계"
    CardKind.SCENIC -> "풍경길"
    CardKind.REST_AREA -> "휴게소"
    CardKind.MY_ROAD -> "내 길"
}

@Composable
fun GildogamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Road.CenterLine,
            onPrimary = Road.Asphalt,
            secondary = Road.Brake,
            background = Road.Asphalt,
            onBackground = Road.Paint,
            surface = Road.Shoulder,
            onSurface = Road.Paint,
            surfaceVariant = Road.Lane,
            onSurfaceVariant = Road.Faded,
        ),
        content = content,
    )
}
