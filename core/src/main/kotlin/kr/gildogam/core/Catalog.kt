package kr.gildogam.core

/**
 * Demo card set: four routes, nine roads.
 *
 * Coordinates are approximate demo values, not surveyed points. Gates are generous (300 m)
 * so they still catch a real drive near the right place, but before field testing they
 * should be replaced with segment ends taken from OpenStreetMap (see README).
 * Stories stick to short, well-known facts and are written by hand, never generated.
 */
object Catalog {

    private const val GANGNEUNG = "영동고속도로"
    private const val WEST = "서해안고속도로"
    private const val INCHEON = "인천대교"
    private const val BUSAN = "부산"

    private val toGangneung = setOf("강릉", "경포", "속초", "양양", "주문진", "동해", "평창", "대관령")
    private val toWest = setOf("당진", "서산", "태안", "보령", "대천", "홍성", "군산", "목포", "행담도")
    private val toAirport = setOf("인천공항", "인천국제공항", "영종", "을왕리")
    private val toHaeundae = setOf("해운대", "광안", "센텀", "송정", "기장")

    val segments: List<RoadSegment> = listOf(
        RoadSegment(
            id = "border-gangwon", name = "강원도 진입", kind = CardKind.BORDER,
            entry = Gate(37.300, 127.700), exit = Gate(37.330, 127.790), lengthKm = 8.5,
            story = "경기도에서 강원도로 넘어가는 경계예요. 여기서부터 강원도의 산길이 시작돼요.",
            destinations = toGangneung, route = GANGNEUNG,
        ),
        RoadSegment(
            id = "rest-hoengseong", name = "횡성휴게소", kind = CardKind.REST_AREA,
            entry = Gate(37.497, 128.013, 250.0), exit = Gate(37.497, 128.013, 250.0), lengthKm = 0.0,
            story = "영동고속도로의 휴게소예요. 차를 세우고 잠시 쉬어 가야 받을 수 있는 카드예요.",
            destinations = toGangneung, route = GANGNEUNG,
        ),
        RoadSegment(
            id = "tunnel-dunnae", name = "둔내터널", kind = CardKind.TUNNEL,
            entry = Gate(37.512, 128.170), exit = Gate(37.522, 128.215), lengthKm = 4.1,
            story = "강원도 횡성 둔내면을 지나는 영동고속도로의 터널이에요.",
            destinations = toGangneung, route = GANGNEUNG,
        ),
        RoadSegment(
            id = "pass-daegwallyeong", name = "대관령", kind = CardKind.PASS,
            entry = Gate(37.680, 128.735), exit = Gate(37.720, 128.830), lengthKm = 9.4,
            story = "강원도 평창과 강릉 사이의 고개예요. 영서와 영동을 나누는 관문으로 불려요.",
            destinations = toGangneung, route = GANGNEUNG,
        ),
        RoadSegment(
            id = "scenic-gyeongpo", name = "경포 해안길", kind = CardKind.SCENIC,
            entry = Gate(37.790, 128.915), exit = Gate(37.815, 128.893), lengthKm = 3.4,
            story = "강릉 경포해변을 따라 이어지는 바닷가 길이에요.",
            destinations = setOf("경포", "강릉"), route = GANGNEUNG,
        ),
        RoadSegment(
            id = "bridge-seohae", name = "서해대교", kind = CardKind.BRIDGE,
            entry = Gate(36.975, 126.860), exit = Gate(36.935, 126.815), lengthKm = 7.3,
            story = "경기도 평택과 충청남도 당진을 잇는 서해안고속도로의 다리로, 2000년에 개통했어요.",
            destinations = toWest, route = WEST,
        ),
        RoadSegment(
            id = "rest-haengdamdo", name = "행담도휴게소", kind = CardKind.REST_AREA,
            entry = Gate(36.955, 126.8375, 250.0), exit = Gate(36.955, 126.8375, 250.0), lengthKm = 0.0,
            story = "서해대교 중간의 섬, 행담도에 자리한 휴게소예요.",
            destinations = toWest, route = WEST,
        ),
        RoadSegment(
            id = "bridge-incheon", name = "인천대교", kind = CardKind.BRIDGE,
            entry = Gate(37.413, 126.640), exit = Gate(37.452, 126.553), lengthKm = 9.0,
            story = "인천 송도와 영종도를 잇는 다리로, 2009년에 개통했어요.",
            destinations = toAirport, route = INCHEON,
        ),
        RoadSegment(
            id = "bridge-gwangan", name = "광안대교", kind = CardKind.BRIDGE,
            entry = Gate(35.144, 129.115), exit = Gate(35.156, 129.140), lengthKm = 3.0,
            story = "부산 수영구 남천동과 해운대구를 잇는 2층 구조의 다리로, 2003년에 개통했어요.",
            destinations = toHaeundae, route = BUSAN,
        ),
    )

    fun byId(id: String): RoadSegment? = segments.firstOrNull { it.id == id }

    /** Roads the usual route to [destination] passes, in driving order. */
    fun onTheWayTo(destination: String): List<RoadSegment> {
        val d = destination.replace(" ", "")
        return segments.filter { seg -> seg.destinations.any { d.contains(it) } }
    }
}

/** A scripted drive for the emulator and the demo video. */
data class DemoTrip(
    val title: String,
    val destination: String,
    val minutes: Int,
    val waypoints: List<Waypoint>,
)

data class Waypoint(val lat: Double, val lng: Double, val restSeconds: Int = 0)

object DemoTrips {
    val gangneung = DemoTrip(
        title = "서울 → 강릉 경포해변",
        destination = "강릉 경포해변",
        minutes = 170,
        waypoints = listOf(
            Waypoint(37.4979, 127.0276),
            Waypoint(37.240, 127.450),
            Waypoint(37.300, 127.700),
            Waypoint(37.330, 127.790),
            Waypoint(37.497, 128.013, restSeconds = 8),
            Waypoint(37.512, 128.170),
            Waypoint(37.522, 128.215),
            Waypoint(37.680, 128.735),
            Waypoint(37.720, 128.830),
            Waypoint(37.790, 128.915),
            Waypoint(37.815, 128.893),
            Waypoint(37.822, 128.885),
        ),
    )

    val west = DemoTrip(
        title = "서울 → 당진",
        destination = "당진",
        minutes = 80,
        waypoints = listOf(
            Waypoint(37.4979, 127.0276),
            Waypoint(37.100, 126.900),
            Waypoint(36.975, 126.860),
            Waypoint(36.955, 126.8375, restSeconds = 8),
            Waypoint(36.935, 126.815),
            Waypoint(36.893, 126.629),
        ),
    )

    val airport = DemoTrip(
        title = "송도 → 인천국제공항",
        destination = "인천국제공항",
        minutes = 30,
        waypoints = listOf(
            Waypoint(37.390, 126.660),
            Waypoint(37.413, 126.640),
            Waypoint(37.452, 126.553),
            Waypoint(37.460, 126.440),
        ),
    )

    /** A short commute between two fixed points, to show 내 길 being born on the 3rd trip. */
    val commute = DemoTrip(
        title = "집 → 회사 (출근)",
        destination = "회사",
        minutes = 25,
        waypoints = listOf(
            Waypoint(37.5665, 126.9780),
            Waypoint(37.5400, 127.0000),
            Waypoint(37.5172, 127.0473),
        ),
    )

    val all = listOf(gangneung, west, airport, commute)
}

/**
 * Turns a [DemoTrip] into timed events: the same events the car adapters send.
 * The app plays them with delays; the tests feed them straight into the engine.
 */
object DemoPlayer {
    fun events(trip: DemoTrip, stepM: Double = 400.0, stepMs: Long = 250, kmh: Float = 90f): List<Pair<Long, CarEvent>> {
        val out = mutableListOf<Pair<Long, CarEvent>>()
        var t = 0L
        out += t to CarEvent.Destination(trip.destination)
        out += t to CarEvent.RemainingTime(trip.minutes)
        t += 1_500
        out += t to CarEvent.GearChanged(Gear.DRIVE)
        out += t to CarEvent.Speed(kmh)

        // total path length to scale remaining time
        val all = Geo.interpolate(trip.waypoints.map { it.lat to it.lng }, stepM)
        val total = all.size.coerceAtLeast(1)
        var done = 0
        var lastMinutes = trip.minutes

        for (i in 1 until trip.waypoints.size) {
            val a = trip.waypoints[i - 1]
            val b = trip.waypoints[i]
            val leg = Geo.interpolate(listOf(a.lat to a.lng, b.lat to b.lng), stepM).drop(1)
            for ((lat, lng) in leg) {
                t += stepMs
                done++
                out += t to CarEvent.Location(lat, lng)
                val minutes = (trip.minutes * (total - done) / total).coerceAtLeast(1)
                if (minutes != lastMinutes) {
                    lastMinutes = minutes
                    out += t to CarEvent.RemainingTime(minutes)
                }
            }
            if (b.restSeconds > 0) {
                out += t to CarEvent.Speed(0f)
                out += t to CarEvent.GearChanged(Gear.PARK)
                t += b.restSeconds * 1_000L
                out += t to CarEvent.GearChanged(Gear.DRIVE)
                out += t to CarEvent.Speed(kmh)
            }
        }
        t += stepMs
        out += t to CarEvent.Speed(0f)
        out += t to CarEvent.RemainingTime(0)
        out += t to CarEvent.GearChanged(Gear.PARK)
        return out
    }
}
