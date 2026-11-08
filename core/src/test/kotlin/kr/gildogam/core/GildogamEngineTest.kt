package kr.gildogam.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GildogamEngineTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private fun kst(day: Int, hour: Int, minute: Int = 0, month: Int = 11): Long =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, seoul).toInstant().toEpochMilli()

    /** Plays timed events into the engine like the app does: a tick every second, speech that finishes. */
    private class Drive(val engine: GildogamEngine, var now: Long) {
        val said = mutableListOf<String>()
        val acquired = mutableListOf<Acquisition>()

        fun send(e: CarEvent) {
            collect(engine.handle(now, e))
        }

        private fun collect(effects: List<Effect>) {
            for (fx in effects) {
                when (fx) {
                    is Effect.Say -> {
                        said += fx.text
                        collect(engine.handle(now, CarEvent.SpeechFinished))
                    }
                    is Effect.Acquired -> acquired += fx.acquisition
                    is Effect.Save -> Unit
                }
            }
        }

        fun advance(ms: Long) {
            val end = now + ms
            while (now + 1_000 <= end) {
                now += 1_000
                send(CarEvent.Tick)
            }
            now = end
        }

        fun play(trip: DemoTrip, settleMs: Long = 10_000) {
            val start = now
            for ((t, e) in DemoPlayer.events(trip)) {
                val at = start + t
                if (at > now) advance(at - now)
                send(e)
            }
            advance(settleMs)
        }
    }

    private fun drive(at: Long, dex: Dex = Dex()) = Drive(GildogamEngine(EngineConfig.demo(), dex = dex), at)

    // ------------------------------------------------------------------ sun

    @Test
    fun sun_parts_of_day_in_seoul() {
        val lat = 37.57
        val lng = 126.98
        val t = Sun.times(kst(8, 12), lat, lng)
        assertNotNull(t)
        val rise = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(t!!.sunriseMs), seoul)
        val set = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(t.sunsetMs), seoul)
        assertTrue("sunrise $rise", rise.hour == 6 && rise.minute >= 40 || rise.hour == 7 && rise.minute <= 20)
        assertTrue("sunset $set", set.hour == 17 && set.minute in 10..50)
        assertEquals(DayPart.DAY, Sun.dayPart(kst(8, 12), lat, lng))
        assertEquals(DayPart.NIGHT, Sun.dayPart(kst(8, 22), lat, lng))
        assertEquals(DayPart.NIGHT, Sun.dayPart(kst(9, 1), lat, lng))
        assertEquals(DayPart.NIGHT, Sun.dayPart(kst(8, 4), lat, lng))
        assertEquals(DayPart.DAWN, Sun.dayPart(kst(8, 6, 15), lat, lng))
    }

    // ------------------------------------------------------------------ whole trips

    @Test
    fun gangneung_trip_on_a_clear_day_fills_one_journey_page() {
        val d = drive(kst(8, 9))
        d.play(DemoTrips.gangneung)

        val ids = d.acquired.map { it.cardId }
        assertEquals(
            listOf("border-gangwon", "rest-hoengseong", "tunnel-dunnae", "pass-daegwallyeong", "scenic-gyeongpo"),
            ids,
        )
        assertTrue(d.acquired.all { it.editions == setOf(Edition.BASIC) })
        assertTrue(d.acquired.all { Badge.FIRST in it.badges })

        val dex = d.engine.state.dex
        assertEquals(1, dex.journeys.size)
        val page = dex.journeys.single()
        assertEquals("강릉 경포해변", page.destination)
        assertEquals("강릉 경포해변 가는 길", page.title)
        assertEquals(d.acquired.map { it.id }, page.acquisitionIds)
        assertNull(d.engine.state.journey)

        assertTrue(d.said.first(), d.said.first().startsWith("이번 여정에서 만날 수 있는 카드 5장, 그중 5장은 아직 없는 카드예요."))
        assertTrue(d.said.contains("곧 대관령 구간이에요."))
        assertTrue(d.said.contains("대관령 카드 획득! 처음 함께 지난 길이에요."))
        assertTrue(d.said.contains("횡성휴게소 카드 획득! 잘 쉬다 가세요."))
        assertTrue(d.said.last(), d.said.last().startsWith("오늘 여정에서 카드 5장을 남겼어요."))
    }

    @Test
    fun rain_at_night_unlocks_both_editions_and_snow_replaces_rain_below_freezing() {
        val d = drive(kst(8, 21))
        d.send(CarEvent.Wiper(true))
        d.send(CarEvent.OutsideTemp(8f))
        d.play(DemoTrips.airport)
        val bridge = d.acquired.single { it.cardId == "bridge-incheon" }
        assertEquals(setOf(Edition.RAIN, Edition.NIGHT), bridge.editions)
        assertEquals(Edition.RAIN, bridge.face)
        assertTrue(d.said.contains("인천대교, 빗길·야경 에디션 획득! 처음 함께 지난 길이에요."))

        val cold = drive(kst(9, 13), d.engine.state.dex)
        cold.send(CarEvent.Wiper(true))
        cold.send(CarEvent.OutsideTemp(-2f))
        cold.play(DemoTrips.airport)
        val snow = cold.acquired.single { it.cardId == "bridge-incheon" }
        assertEquals(setOf(Edition.SNOW), snow.editions)
        assertFalse("second pass is not a first", Badge.FIRST in snow.badges)
        assertEquals(setOf(Edition.RAIN, Edition.NIGHT, Edition.SNOW), cold.engine.state.dex.editionsOf("bridge-incheon"))
        assertTrue(cold.said.first(), cold.said.first().contains("새로운 에디션"))
    }

    @Test
    fun resting_mid_bridge_still_completes_the_bridge_and_earns_the_rest_area() {
        val d = drive(kst(8, 10))
        d.play(DemoTrips.west)
        assertEquals(listOf("rest-haengdamdo", "bridge-seohae"), d.acquired.map { it.cardId })
    }

    @Test
    fun driving_the_other_way_counts_too() {
        val d = drive(kst(8, 10))
        val reverse = DemoTrips.airport.copy(waypoints = DemoTrips.airport.waypoints.reversed(), destination = "송도")
        d.play(reverse)
        assertEquals(listOf("bridge-incheon"), d.acquired.map { it.cardId })
    }

    // ------------------------------------------------------------------ segment rules

    @Test
    fun tunnel_without_gps_inside_is_judged_from_entrance_and_exit() {
        val seg = Catalog.byId("tunnel-dunnae")!!
        val d = drive(kst(8, 10))
        d.send(CarEvent.GearChanged(Gear.DRIVE))
        d.send(CarEvent.Speed(80f))
        d.send(CarEvent.Location(seg.entry.lat - 0.02, seg.entry.lng - 0.03))
        d.advance(1_000)
        d.send(CarEvent.Location(seg.entry.lat, seg.entry.lng))
        d.advance(1_000)
        d.send(CarEvent.Location(seg.entry.lat + 0.004, seg.entry.lng + 0.01)) // last fix in the tunnel mouth
        d.advance(150_000) // no GPS for two and a half minutes
        d.send(CarEvent.Location(seg.exit.lat, seg.exit.lng))
        assertEquals(listOf("tunnel-dunnae"), d.acquired.map { it.cardId })
    }

    @Test
    fun turning_back_before_the_far_end_earns_nothing() {
        val seg = Catalog.byId("pass-daegwallyeong")!!
        val d = drive(kst(8, 10))
        d.send(CarEvent.GearChanged(Gear.DRIVE))
        d.send(CarEvent.Location(seg.entry.lat, seg.entry.lng))
        d.advance(1_000)
        d.send(CarEvent.Location(seg.entry.lat + 0.01, seg.entry.lng + 0.02))
        d.advance(1_000)
        d.send(CarEvent.Location(seg.entry.lat, seg.entry.lng)) // U-turn back to the start
        d.advance(1_000)
        d.send(CarEvent.Location(seg.entry.lat - 0.01, seg.entry.lng - 0.02))
        d.advance(3 * 3_600_000L)
        assertTrue(d.acquired.isEmpty())
    }

    @Test
    fun parking_is_needed_for_a_rest_area_card() {
        val seg = Catalog.byId("rest-hoengseong")!!
        val d = drive(kst(8, 10))
        d.send(CarEvent.GearChanged(Gear.DRIVE))
        d.send(CarEvent.Location(seg.entry.lat, seg.entry.lng))
        d.advance(60_000) // driving through without stopping
        assertTrue(d.acquired.isEmpty())
        d.send(CarEvent.GearChanged(Gear.PARK))
        d.advance(EngineConfig.demo().restMs + 1_000)
        assertEquals(listOf("rest-hoengseong"), d.acquired.map { it.cardId })
    }

    @Test
    fun slow_traffic_inside_a_segment_earns_the_jam_edition() {
        val seg = Catalog.byId("bridge-gwangan")!!
        val d = drive(kst(8, 10))
        d.send(CarEvent.GearChanged(Gear.DRIVE))
        d.send(CarEvent.Location(seg.entry.lat, seg.entry.lng))
        d.send(CarEvent.Speed(8f))
        d.advance(EngineConfig.demo().jamMs + 2_000)
        d.send(CarEvent.Location((seg.entry.lat + seg.exit.lat) / 2, (seg.entry.lng + seg.exit.lng) / 2))
        d.send(CarEvent.Speed(40f))
        d.send(CarEvent.Location(seg.exit.lat, seg.exit.lng))
        assertEquals(setOf(Edition.JAM), d.acquired.single().editions)
    }

    // ------------------------------------------------------------------ speech, riders, notes

    @Test
    fun navigation_turn_instructions_hold_the_announcement() {
        val seg = Catalog.byId("bridge-gwangan")!!
        val d = drive(kst(8, 10))
        d.send(CarEvent.GearChanged(Gear.DRIVE))
        d.send(CarEvent.NaviQuiet(true))
        d.send(CarEvent.Location(seg.entry.lat, seg.entry.lng))
        d.send(CarEvent.Location((seg.entry.lat + seg.exit.lat) / 2, (seg.entry.lng + seg.exit.lng) / 2))
        d.send(CarEvent.Location(seg.exit.lat, seg.exit.lng))
        assertEquals(1, d.acquired.size)
        assertTrue("held while the navigation speaks", d.said.none { it.contains("광안대교") })
        d.send(CarEvent.NaviQuiet(false))
        assertTrue(d.said.any { it.startsWith("광안대교 카드 획득!") })
    }

    @Test
    fun riders_mark_cards_as_shared_and_notes_are_kept() {
        val d = drive(kst(8, 10))
        d.send(CarEvent.Riders(listOf("엄마", "아빠", "지우")))
        assertTrue(d.said.contains("어머님, 아버님, 지우님, 오늘 여정을 함께 남길게요."))
        d.play(DemoTrips.airport)
        val card = d.acquired.single()
        assertTrue(Badge.FAMILY in card.badges)
        assertEquals(listOf("엄마", "아빠", "지우"), card.riders)
        assertEquals(listOf("엄마", "아빠", "지우"), d.engine.state.dex.journeys.single().riders)

        d.send(CarEvent.Note(card.id, " 다리 위에서 비행기 봤다 "))
        assertEquals("다리 위에서 비행기 봤다", d.engine.state.dex.acquisitions.single().note)
    }

    // ------------------------------------------------------------------ journeys and 내 길

    @Test
    fun the_third_commute_turns_the_route_into_a_my_road_card() {
        var dex = Dex()
        val earned = mutableListOf<List<Acquisition>>()
        for (day in 2..4) {
            val d = drive(kst(day, 8), dex)
            d.play(DemoTrips.commute)
            dex = d.engine.state.dex
            earned += d.acquired.toList()
            if (day == 4) assertTrue(d.said.contains("자주 달린 이 길이 이제 '내 길: 회사 가는 길' 카드가 되었어요."))
        }
        assertTrue(earned[0].isEmpty())
        assertTrue(earned[1].isEmpty())
        val card = earned[2].single()
        assertEquals(CardKind.MY_ROAD, card.kind)
        assertEquals("내 길: 회사 가는 길", card.cardName)
        assertTrue(Badge.FIRST in card.badges)
        assertEquals(3, dex.myRoads.single().trips)
        assertEquals(3, dex.journeys.size)
        assertEquals(listOf(card.id), dex.journeys.last().acquisitionIds)
    }

    @Test
    fun a_shuffle_in_the_car_park_is_not_a_journey() {
        val d = drive(kst(8, 10))
        d.send(CarEvent.Location(37.5, 127.0))
        d.send(CarEvent.GearChanged(Gear.DRIVE))
        d.advance(5_000)
        d.send(CarEvent.Location(37.5001, 127.0001))
        d.send(CarEvent.GearChanged(Gear.PARK))
        d.advance(EngineConfig.demo().parkedEndMs + 1_000)
        assertNull(d.engine.state.journey)
        assertTrue(d.engine.state.dex.journeys.isEmpty())
    }

    @Test
    fun end_button_closes_the_page_right_away() {
        val d = drive(kst(8, 10))
        d.send(CarEvent.Location(37.5, 127.0))
        d.send(CarEvent.GearChanged(Gear.DRIVE))
        d.send(CarEvent.Location(37.53, 127.03))
        d.send(CarEvent.GearChanged(Gear.PARK))
        d.send(CarEvent.EndJourney)
        assertEquals(1, d.engine.state.dex.journeys.size)
        assertEquals("오늘 여정을 기록했어요.", d.said.last())
    }

    @Test
    fun preview_counts_cards_on_the_way() {
        assertEquals(5, Catalog.onTheWayTo("강릉 경포해변").size)
        assertEquals(listOf("bridge-seohae", "rest-haengdamdo"), Catalog.onTheWayTo("당진 시청").map { it.id })
        assertTrue(Catalog.onTheWayTo("대전역").isEmpty())
    }

    @Test
    fun the_same_trip_again_is_previewed_again_after_arriving() {
        val d = drive(kst(8, 10))
        d.play(DemoTrips.airport)
        assertNull(d.engine.state.preview)
        d.said.clear()
        d.play(DemoTrips.airport)
        assertTrue(d.said.first(), d.said.first().startsWith("이번 여정에서 만날 수 있는 카드 1장. 새로운 에디션을 노려 볼까요?"))
    }
}
