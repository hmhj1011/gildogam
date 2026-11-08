package kr.gildogam.core

/**
 * The journey book engine.
 *
 * It receives car, navigation and voice events and decides:
 *  - when a road segment has been driven end to end (gate in → gate out, still driving),
 *    including tunnels where GPS drops in between;
 *  - which editions the pass earned (wipers, outside temperature, sun position, traffic);
 *  - when a journey starts and ends, and which cards belong to its page;
 *  - when a route driven again and again becomes a "내 길" card;
 *  - what to say, and when: one line at a time, held while the navigation is about to speak.
 *
 * Pure Kotlin, no Android: the whole flow is unit-tested.
 */
class GildogamEngine(
    private val config: EngineConfig = EngineConfig(),
    private val catalog: List<RoadSegment> = Catalog.segments,
    dex: Dex = Dex(),
) {
    var state = EngineState(dex = dex)
        private set

    private var now = 0L
    private var counter = 0

    // car
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var slowSince: Long? = null
    private var parkedSince: Long? = null

    // journey
    private var riders: List<String> = emptyList()
    private var journeyRain = false
    private var journeySnow = false
    private var journeyJam = false
    private var movedM = 0.0
    private val announced = mutableSetOf<String>()
    private val doneThisJourney = mutableSetOf<String>()
    private var previewedFor: String? = null

    // segments
    private class Armed(var fromEntry: Boolean, var sinceMs: Long) {
        var leftStart = false
        val editions = mutableSetOf<Edition>()
    }

    private val armed = mutableMapOf<String, Armed>()
    private val restSince = mutableMapOf<String, Long>()

    // speech
    private data class Line(val text: String, val expiresAt: Long)

    private val lines = ArrayDeque<Line>()
    private var speaking = 0
    private var lastSayAt = 0L

    fun handle(nowMs: Long, event: CarEvent): List<Effect> {
        now = nowMs
        val out = mutableListOf<Effect>()
        when (event) {
            is CarEvent.GearChanged -> onGear(event.gear, out)
            is CarEvent.Speed -> onSpeed(event.kmh)
            is CarEvent.Wiper -> state = state.copy(raining = event.on)
            is CarEvent.OutsideTemp -> state = state.copy(outsideC = event.celsius)
            is CarEvent.Location -> onLocation(event.lat, event.lng, out)
            is CarEvent.Destination -> onDestination(event.name)
            is CarEvent.RemainingTime -> state = state.copy(remainMinutes = event.minutes)
            is CarEvent.NaviQuiet -> state = state.copy(naviQuiet = event.active)
            is CarEvent.RouteCancelled -> state = state.copy(destination = null, remainMinutes = null, preview = null)
            is CarEvent.Riders -> onRiders(event.names)
            is CarEvent.Note -> onNote(event.acquisitionId, event.text, out)
            is CarEvent.EndJourney -> endJourney(out)
            is CarEvent.SpeechFinished -> {
                if (speaking > 0) speaking--
            }
            is CarEvent.Tick -> Unit
        }
        housekeeping(out)
        pumpSpeech(out)
        return out
    }

    // ------------------------------------------------------------------ car

    private fun onGear(gear: Gear, out: MutableList<Effect>) {
        state = state.copy(gear = gear)
        if (gear == Gear.PARK) {
            parkedSince = parkedSince ?: now
            slowSince = null
        } else {
            parkedSince = null
            restSince.clear()
        }
        if (gear == Gear.DRIVE && state.journey == null) startJourney()
        updateUpcoming()
        if (gear == Gear.PARK) checkRest(out)
    }

    private fun onSpeed(kmh: Float) {
        state = state.copy(speedKmh = kmh)
        slowSince = if (state.gear == Gear.DRIVE && kmh < config.jamSpeedKmh) (slowSince ?: now) else null
    }

    private fun jamming(): Boolean {
        val s = slowSince ?: return false
        return now - s >= config.jamMs
    }

    private fun weatherEdition(): Edition? = when {
        !state.raining -> null
        state.outsideC != null && state.outsideC!! <= config.snowMaxCelsius -> Edition.SNOW
        else -> Edition.RAIN
    }

    private fun drivingNow(): Boolean =
        state.gear == Gear.DRIVE || (state.gear == Gear.UNKNOWN && state.speedKmh > 5f)

    // ------------------------------------------------------------------ journey

    private fun startJourney() {
        val j = Journey(
            id = nextId("j"),
            startedAtMs = now,
            endedAtMs = null,
            destination = state.destination,
            riders = riders,
            acquisitionIds = emptyList(),
            startLat = lastLat,
            startLng = lastLng,
            endLat = null,
            endLng = null,
        )
        journeyRain = false
        journeySnow = false
        journeyJam = false
        movedM = 0.0
        announced.clear()
        doneThisJourney.clear()
        armed.clear()
        state = state.copy(journey = j)
        val p = state.preview
        if (p != null && previewedFor != p.destination) say(previewLine(p), keep = false)
        previewedFor = p?.destination
    }

    private fun endJourney(out: MutableList<Effect>) {
        val j = state.journey ?: return
        val finished = j.copy(
            endedAtMs = now,
            destination = j.destination ?: state.destination,
            endLat = lastLat,
            endLng = lastLng,
            startLat = j.startLat ?: lastLat,
            startLng = j.startLng ?: lastLng,
        )
        state = state.copy(journey = null)
        parkedSince = null
        armed.clear()

        if (finished.acquisitionIds.isEmpty() && movedM < 300.0) return // a shuffle in the car park

        val road = recordMyRoad(finished)
        val card = road.card
        val page = if (card != null) finished.copy(acquisitionIds = finished.acquisitionIds + card.id) else finished
        val dex = state.dex.copy(
            acquisitions = state.dex.acquisitions + listOfNotNull(card),
            journeys = state.dex.journeys + page,
            myRoads = road.roads,
        )
        state = state.copy(dex = dex)
        if (card != null) out += Effect.Acquired(card)
        out += Effect.Save(dex)

        if (card != null && Badge.FIRST in card.badges) {
            say("자주 달린 이 길이 이제 '${card.cardName}' 카드가 되었어요.", keep = true)
        }
        val n = page.acquisitionIds.size
        say(
            if (n > 0) "오늘 여정에서 카드 ${n}장을 남겼어요. 차를 세운 동안 여정 도감에서 펼쳐 보세요."
            else "오늘 여정을 기록했어요.",
            keep = true,
        )
    }

    private class RoadResult(val roads: List<MyRoad>, val card: Acquisition?)

    /** Counts this trip toward a repeated route; from the 3rd trip on it earns the 내 길 card. */
    private fun recordMyRoad(j: Journey): RoadResult {
        val roads = state.dex.myRoads.toMutableList()
        val sLat = j.startLat
        val sLng = j.startLng
        val eLat = j.endLat
        val eLng = j.endLng
        if (sLat == null || sLng == null || eLat == null || eLng == null ||
            Geo.distanceM(sLat, sLng, eLat, eLng) < 1_000
        ) {
            return RoadResult(roads, null)
        }

        val r = config.myRoadRadiusM
        val idx = roads.indexOfFirst {
            Geo.distanceM(it.startLat, it.startLng, sLat, sLng) <= r && Geo.distanceM(it.endLat, it.endLng, eLat, eLng) <= r
        }
        val road = if (idx >= 0) {
            roads[idx].copy(trips = roads[idx].trips + 1).also { roads[idx] = it }
        } else {
            val name = j.destination?.let { "$it 가는 길" } ?: "자주 가는 길 ${roads.size + 1}"
            MyRoad(nextId("road"), name, sLat, sLng, eLat, eLng, trips = 1).also { roads += it }
        }
        if (road.trips < config.myRoadTrips) return RoadResult(roads, null)

        val cardId = "my-${road.id}"
        val editions = mutableSetOf<Edition>()
        when {
            journeySnow -> editions += Edition.SNOW
            journeyRain -> editions += Edition.RAIN
        }
        if (journeyJam) editions += Edition.JAM
        dayEdition(eLat, eLng)?.let { editions += it }
        if (editions.isEmpty()) editions += Edition.BASIC
        val acq = Acquisition(
            id = nextId("a"),
            cardId = cardId,
            cardName = "내 길: ${road.name}",
            kind = CardKind.MY_ROAD,
            editions = editions,
            badges = badgesFor(cardId),
            atMs = now,
            riders = j.riders,
            journeyId = j.id,
        )
        return RoadResult(roads, acq)
    }

    private fun onDestination(name: String) {
        val dest = name.trim()
        if (dest.isEmpty()) return
        val cards = catalog.filter { seg -> Catalog.onTheWayTo(dest).any { it.id == seg.id } }
        val preview = TripPreview(dest, cards, cards.count { !state.dex.has(it.id) })
        state = state.copy(
            destination = dest,
            preview = preview,
            journey = state.journey?.let { if (it.destination == null) it.copy(destination = dest) else it },
        )
        // Announce now when parked before departure; on departure otherwise.
        if (cards.isNotEmpty() && previewedFor != dest && (state.parked || state.journey != null)) {
            previewedFor = dest
            say(previewLine(preview), keep = false)
        }
    }

    private fun previewLine(p: TripPreview): String = when {
        p.cards.isEmpty() -> "${p.destination}까지 가는 길, 오늘의 여정을 기록할게요."
        p.newCards > 0 -> "이번 여정에서 만날 수 있는 카드 ${p.cards.size}장, 그중 ${p.newCards}장은 아직 없는 카드예요."
        else -> "이번 여정에서 만날 수 있는 카드 ${p.cards.size}장. 새로운 에디션을 노려 볼까요?"
    }

    private fun onRiders(names: List<String>) {
        riders = names
        state = state.copy(journey = state.journey?.copy(riders = names))
        if (names.isNotEmpty()) say("${Korean.joinListeners(names)}, 오늘 여정을 함께 남길게요.", keep = false)
    }

    private fun onNote(id: String, text: String, out: MutableList<Effect>) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val dex = state.dex
        if (dex.acquisitions.none { it.id == id }) return
        val updated = dex.copy(acquisitions = dex.acquisitions.map { if (it.id == id) it.copy(note = clean) else it })
        state = state.copy(dex = updated)
        out += Effect.Save(updated)
    }

    // ------------------------------------------------------------------ roads

    private fun onLocation(lat: Double, lng: Double, out: MutableList<Effect>) {
        val pLat = lastLat
        val pLng = lastLng
        if (pLat != null && pLng != null && state.journey != null) movedM += Geo.distanceM(pLat, pLng, lat, lng)
        lastLat = lat
        lastLng = lng

        if (drivingNow() && state.journey == null) startJourney()
        val j = state.journey
        if (j != null && j.startLat == null) state = state.copy(journey = j.copy(startLat = lat, startLng = lng))

        if (drivingNow()) {
            for (seg in catalog) {
                if (seg.kind == CardKind.REST_AREA) continue
                trackSegment(seg, lat, lng, out)
            }
        }
        if (state.gear == Gear.PARK) checkRest(out)
        updateUpcoming()
    }

    private fun trackSegment(seg: RoadSegment, lat: Double, lng: Double, out: MutableList<Effect>) {
        val inEntry = Geo.inside(seg.entry, lat, lng)
        val inExit = Geo.inside(seg.exit, lat, lng)
        val a = armed[seg.id]
        if (a == null) {
            if (seg.id in doneThisJourney && !inEntry && !inExit) return
            if ((inEntry || inExit) && seg.id !in doneThisJourney) {
                armed[seg.id] = Armed(fromEntry = inEntry, sinceMs = now).also { collect(it) }
                return
            }
            if (seg.id !in announced && seg.id !in doneThisJourney) {
                val d = minOf(
                    Geo.distanceM(seg.entry.lat, seg.entry.lng, lat, lng),
                    Geo.distanceM(seg.exit.lat, seg.exit.lng, lat, lng),
                )
                if (d <= config.approachM) {
                    announced += seg.id
                    say("곧 ${seg.name} 구간이에요.", keep = false)
                }
            }
            return
        }
        collect(a)
        val start = if (a.fromEntry) seg.entry else seg.exit
        val end = if (a.fromEntry) seg.exit else seg.entry
        val inStart = Geo.inside(start, lat, lng)
        val inEnd = Geo.inside(end, lat, lng)
        when {
            inEnd && a.leftStart -> complete(seg, a, out)
            inEnd && !inStart -> complete(seg, a, out) // gates far apart; reached the far end directly
            !inStart -> a.leftStart = true
            a.leftStart -> { // came back to where it started: start over
                armed[seg.id] = Armed(a.fromEntry, now).also { collect(it) }
            }
        }
    }

    private fun collect(a: Armed) {
        weatherEdition()?.let {
            a.editions += it
            if (it == Edition.SNOW) a.editions -= Edition.RAIN
        }
        if (jamming()) a.editions += Edition.JAM
    }

    private fun complete(seg: RoadSegment, a: Armed, out: MutableList<Effect>) {
        armed.remove(seg.id)
        doneThisJourney += seg.id
        val g = if (a.fromEntry) seg.exit else seg.entry
        val editions = a.editions.toMutableSet()
        if (Edition.SNOW in editions) editions -= Edition.RAIN
        dayEdition(g.lat, g.lng)?.let { editions += it }
        if (editions.isEmpty()) editions += Edition.BASIC
        acquire(seg, editions, out)
    }

    private fun checkRest(out: MutableList<Effect>) {
        val lat = lastLat ?: return
        val lng = lastLng ?: return
        if (state.journey == null) return
        for (seg in catalog) {
            if (seg.kind != CardKind.REST_AREA || seg.id in doneThisJourney) continue
            if (!Geo.inside(seg.entry, lat, lng)) {
                restSince.remove(seg.id)
                continue
            }
            val since = restSince.getOrPut(seg.id) { now }
            if (now - since >= config.restMs) {
                doneThisJourney += seg.id
                val editions = mutableSetOf<Edition>()
                weatherEdition()?.let { editions += it }
                dayEdition(seg.entry.lat, seg.entry.lng)?.let { editions += it }
                if (editions.isEmpty()) editions += Edition.BASIC
                acquire(seg, editions, out, rest = true)
            }
        }
    }

    private fun acquire(seg: RoadSegment, editions: Set<Edition>, out: MutableList<Effect>, rest: Boolean = false) {
        val j = state.journey ?: return
        val badges = badgesFor(seg.id)
        val acq = Acquisition(
            id = nextId("a"),
            cardId = seg.id,
            cardName = seg.name,
            kind = seg.kind,
            editions = editions,
            badges = badges,
            atMs = now,
            riders = j.riders,
            journeyId = j.id,
        )
        val dex = state.dex.copy(acquisitions = state.dex.acquisitions + acq)
        state = state.copy(dex = dex, journey = j.copy(acquisitionIds = j.acquisitionIds + acq.id))
        out += Effect.Acquired(acq)
        out += Effect.Save(dex)
        say(acquiredLine(seg, acq, rest), keep = true)
    }

    private fun badgesFor(cardId: String): Set<Badge> {
        val b = mutableSetOf<Badge>()
        if (!state.dex.has(cardId)) b += Badge.FIRST
        if (riders.size >= 2) b += Badge.FAMILY
        return b
    }

    private fun acquiredLine(seg: RoadSegment, acq: Acquisition, rest: Boolean): String {
        val eds = Edition.entries.filter { it in acq.editions && it != Edition.BASIC }
        val head = if (eds.isEmpty()) "${seg.name} 카드 획득!"
        else "${seg.name}, ${eds.joinToString("·") { it.label }} 에디션 획득!"
        val tail = when {
            rest -> " 잘 쉬다 가세요."
            Badge.FIRST in acq.badges -> " 처음 함께 지난 길이에요."
            else -> ""
        }
        return head + tail
    }

    private fun dayEdition(lat: Double, lng: Double): Edition? = when (Sun.dayPart(now, lat, lng)) {
        DayPart.NIGHT -> Edition.NIGHT
        DayPart.DAWN -> Edition.DAWN
        DayPart.DAY -> null
    }

    private fun updateUpcoming() {
        val lat = lastLat
        val lng = lastLng
        val inSeg = armed.keys.firstNotNullOfOrNull { id -> catalog.firstOrNull { it.id == id }?.name }
        val upcoming = if (lat == null || lng == null || !state.driving) null else catalog
            .filter { it.id !in doneThisJourney && it.id !in armed && it.kind != CardKind.REST_AREA }
            .map { it to minOf(Geo.distanceM(it.entry.lat, it.entry.lng, lat, lng), Geo.distanceM(it.exit.lat, it.exit.lng, lat, lng)) }
            .filter { it.second <= 5_000 }
            .minByOrNull { it.second }
            ?.first?.name
        state = state.copy(inSegment = inSeg, upcoming = upcoming)
    }

    // ------------------------------------------------------------------ clock

    private fun housekeeping(out: MutableList<Effect>) {
        // conditions seen during the whole trip (for 내 길)
        if (state.journey != null) {
            when (weatherEdition()) {
                Edition.SNOW -> journeySnow = true
                Edition.RAIN -> journeyRain = true
                else -> Unit
            }
            if (jamming()) journeyJam = true
            armed.values.forEach { collect(it) }
        }

        // a pass that never reached its far end is dropped (wrong turn, detour)
        val iter = armed.entries.iterator()
        while (iter.hasNext()) {
            val (id, a) = iter.next()
            val seg = catalog.firstOrNull { it.id == id } ?: continue
            val limitMs = (seg.lengthKm / 8.0 * 3_600_000).toLong() + 30 * 60_000L
            if (now - a.sinceMs > limitMs) iter.remove()
        }

        if (state.gear == Gear.PARK) checkRest(out)

        val parked = parkedSince
        if (state.journey != null && state.gear == Gear.PARK && parked != null) {
            val arrived = (state.remainMinutes ?: Int.MAX_VALUE) <= 1
            val waited = now - parked
            if ((arrived && waited >= config.arrivedEndMs) || waited >= config.parkedEndMs) endJourney(out)
        }
    }

    // ------------------------------------------------------------------ speech

    private fun say(text: String, keep: Boolean) {
        lines.addLast(Line(text, if (keep) Long.MAX_VALUE else now + config.lineTtlMs))
    }

    private fun pumpSpeech(out: MutableList<Effect>) {
        if (speaking > 0 && now - lastSayAt > 30_000) speaking = 0 // lost "done": never freeze
        if (speaking > 0 || state.naviQuiet) return
        while (lines.isNotEmpty()) {
            val line = lines.removeFirst()
            if (line.expiresAt < now) continue
            speaking++
            lastSayAt = now
            state = state.copy(lastLine = line.text)
            out += Effect.Say(line.text)
            return
        }
    }

    private fun nextId(prefix: String): String = "$prefix-$now-${counter++}"
}
