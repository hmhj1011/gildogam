package kr.gildogam.core

/** What kind of road a card stands for. */
enum class CardKind(val label: String) {
    TUNNEL("터널"),
    BRIDGE("다리"),
    PASS("고개"),
    BORDER("경계"),
    SCENIC("풍경길"),
    REST_AREA("휴게소"),
    MY_ROAD("내 길"),
}

/**
 * How a card was earned. One pass can unlock several editions at once
 * (rain at night unlocks both 빗길 and 야경). BASIC is given only when no other applies.
 * Ordered from most to least special: the first one present is the card's face.
 */
enum class Edition(val label: String) {
    SNOW("눈길"),
    RAIN("빗길"),
    NIGHT("야경"),
    DAWN("새벽"),
    JAM("정체"),
    BASIC("기본"),
}

/** Marks on a single acquisition, not collectible editions. */
enum class Badge(val label: String) {
    FIRST("첫 통과"),
    FAMILY("함께 탄 길"),
}

/** A circle the car must pass through. */
data class Gate(val lat: Double, val lng: Double, val radiusM: Double = 300.0)

/**
 * A stretch of road that becomes a card when driven end to end.
 * For [CardKind.REST_AREA] only [entry] is used: park inside it long enough to rest.
 */
data class RoadSegment(
    val id: String,
    val name: String,
    val kind: CardKind,
    val entry: Gate,
    val exit: Gate,
    val lengthKm: Double,
    val story: String,
    /** Navigation destinations whose usual route passes here (used for the trip preview). */
    val destinations: Set<String> = emptySet(),
    val route: String = "",
)

/** One earned card. The same road earned again on another day is a new acquisition. */
data class Acquisition(
    val id: String,
    val cardId: String,
    val cardName: String,
    val kind: CardKind,
    val editions: Set<Edition>,
    val badges: Set<Badge>,
    val atMs: Long,
    val riders: List<String>,
    val journeyId: String,
    val note: String? = null,
) {
    val face: Edition get() = Edition.entries.first { it in editions }
}

/** One drive from departure to parking at the destination: a page of the journey book. */
data class Journey(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val destination: String?,
    val riders: List<String>,
    val acquisitionIds: List<String>,
    val startLat: Double?,
    val startLng: Double?,
    val endLat: Double?,
    val endLng: Double?,
) {
    val finished: Boolean get() = endedAtMs != null

    val title: String
        get() = destination?.let { "$it 가는 길" } ?: "여정"
}

/** A route this car drives again and again (home → office). Becomes a card from the 3rd trip. */
data class MyRoad(
    val id: String,
    val name: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val trips: Int,
)

/** Everything the car has kept so far. Saved by the app, handed back on the next start. */
data class Dex(
    val acquisitions: List<Acquisition> = emptyList(),
    val journeys: List<Journey> = emptyList(),
    val myRoads: List<MyRoad> = emptyList(),
) {
    fun editionsOf(cardId: String): Set<Edition> =
        acquisitions.filter { it.cardId == cardId }.flatMapTo(mutableSetOf()) { it.editions }

    fun has(cardId: String): Boolean = acquisitions.any { it.cardId == cardId }

    fun acquisitionsOf(journeyId: String): List<Acquisition> = acquisitions.filter { it.journeyId == journeyId }
}

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** Car, navigation, voice and button input, as seen by the engine. */
sealed interface CarEvent {
    data class GearChanged(val gear: Gear) : CarEvent
    data class Speed(val kmh: Float) : CarEvent
    data class Wiper(val on: Boolean) : CarEvent
    data class OutsideTemp(val celsius: Float?) : CarEvent
    data class Location(val lat: Double, val lng: Double) : CarEvent
    data class Destination(val name: String) : CarEvent
    data class RemainingTime(val minutes: Int) : CarEvent
    data class NaviQuiet(val active: Boolean) : CarEvent
    data object RouteCancelled : CarEvent
    data class Riders(val names: List<String>) : CarEvent
    data class Note(val acquisitionId: String, val text: String) : CarEvent
    data object EndJourney : CarEvent
    data object SpeechFinished : CarEvent
    data object Tick : CarEvent
}

/** What the app should do. */
sealed interface Effect {
    data class Say(val text: String) : Effect
    data class Acquired(val acquisition: Acquisition) : Effect

    /** The dex changed: save it. */
    data class Save(val dex: Dex) : Effect
}

/** Timing knobs. [demo] shortens waits so a one-minute demo video shows everything. */
data class EngineConfig(
    val approachM: Double = 1_500.0,
    val jamSpeedKmh: Float = 15f,
    val jamMs: Long = 90_000,
    val restMs: Long = 3 * 60_000,
    val arrivedEndMs: Long = 30_000,
    val parkedEndMs: Long = 15 * 60_000,
    val snowMaxCelsius: Float = 1f,
    val myRoadRadiusM: Double = 500.0,
    val myRoadTrips: Int = 3,
    val lineTtlMs: Long = 60_000,
) {
    companion object {
        fun demo() = EngineConfig(jamMs = 8_000, restMs = 5_000, arrivedEndMs = 5_000, parkedEndMs = 30_000)
    }
}

/** Snapshot for the screens. */
data class EngineState(
    val gear: Gear = Gear.UNKNOWN,
    val speedKmh: Float = 0f,
    val raining: Boolean = false,
    val outsideC: Float? = null,
    val destination: String? = null,
    val remainMinutes: Int? = null,
    val naviQuiet: Boolean = false,
    val journey: Journey? = null,
    val inSegment: String? = null,
    val upcoming: String? = null,
    val preview: TripPreview? = null,
    val lastLine: String = "",
    val dex: Dex = Dex(),
) {
    val driving: Boolean get() = gear == Gear.DRIVE || gear == Gear.REVERSE
    val parked: Boolean get() = !driving
}

data class TripPreview(val destination: String, val cards: List<RoadSegment>, val newCards: Int)
