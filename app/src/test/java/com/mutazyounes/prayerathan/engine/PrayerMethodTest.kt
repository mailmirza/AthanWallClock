package com.mutazyounes.prayerathan.engine

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerMethodTest {

    private val dubai = SavedLocation("Dubai", 25.0772, 55.3093, "Asia/Dubai")
    private val dubaiZone = ZoneId.of("Asia/Dubai")
    private val noonUtc = Instant.parse("2026-11-15T08:00:00Z")

    private fun timeOf(day: PrayerDay, name: PrayerName): LocalTime =
        day.times.first { it.name == name }.at.atZone(dubaiZone).toLocalTime()

    private fun instantOf(day: PrayerDay, name: PrayerName): Instant =
        day.times.first { it.name == name }.at

    @Test
    fun autoFollowsTheSavedRegion() {
        assertEquals(CalcMethod.DUBAI, PrayerMethod.suggestedFor("Asia/Dubai"))
        assertEquals(CalcMethod.UMM_AL_QURA, PrayerMethod.suggestedFor("Asia/Riyadh"))
        assertEquals(CalcMethod.KARACHI, PrayerMethod.suggestedFor("Asia/Karachi"))
        assertEquals(CalcMethod.NORTH_AMERICA, PrayerMethod.suggestedFor("America/Toronto"))
        assertEquals(CalcMethod.NORTH_AMERICA, PrayerMethod.suggestedFor("America/New_York"))
        assertEquals(CalcMethod.EGYPTIAN, PrayerMethod.suggestedFor("Africa/Cairo"))
        assertEquals(CalcMethod.SINGAPORE, PrayerMethod.suggestedFor("Asia/Kuala_Lumpur"))
        assertEquals(CalcMethod.MUSLIM_WORLD_LEAGUE, PrayerMethod.suggestedFor("Europe/London"))
    }

    @Test
    fun explicitChoiceBeatsAuto() {
        val picked = MethodSettings(method = CalcMethod.KARACHI)
        assertEquals(CalcMethod.KARACHI, PrayerMethod.resolve(picked, "Asia/Dubai"))
        assertEquals(CalcMethod.DUBAI, PrayerMethod.resolve(MethodSettings.AUTO, "Asia/Dubai"))
    }

    @Test
    fun settingsLineNamesMethodAndAsr() {
        assertEquals(
            "Times: Dubai (auto). Asr is Shafi.",
            PrayerMethod.settingsLine(MethodSettings.AUTO, "Asia/Dubai"),
        )
        assertEquals(
            "Times: Karachi. Asr is Hanafi.",
            PrayerMethod.settingsLine(
                MethodSettings(CalcMethod.KARACHI, AsrSchool.HANAFI),
                "Asia/Dubai",
            ),
        )
    }

    @Test
    fun dubaiTimesLandInTheExpectedWindows() {
        val engine = PrayerCalculator(InMemoryLocationStore(dubai))
        val day = engine.day(noonUtc)
        // Mid-November in Dubai. Wide windows: this guards against a wrong
        // method or zone, not against a one-minute rounding change.
        assertTrue(timeOf(day, PrayerName.FAJR) in LocalTime.of(5, 0)..LocalTime.of(5, 30))
        assertTrue(timeOf(day, PrayerName.DHUHR) in LocalTime.of(11, 55)..LocalTime.of(12, 15))
        assertTrue(timeOf(day, PrayerName.ASR) in LocalTime.of(15, 0)..LocalTime.of(15, 25))
        assertTrue(timeOf(day, PrayerName.MAGHRIB) in LocalTime.of(17, 25)..LocalTime.of(17, 45))
        assertTrue(timeOf(day, PrayerName.ISHA) in LocalTime.of(18, 40)..LocalTime.of(19, 5))
    }

    @Test
    fun dubaiMethodIsEarlierFajrAndLaterIshaThanIsna() {
        val auto = PrayerCalculator(InMemoryLocationStore(dubai)).day(noonUtc)
        val isna = PrayerCalculator(
            InMemoryLocationStore(dubai),
            InMemoryOffsetStore(),
            InMemoryMethodStore(MethodSettings(method = CalcMethod.NORTH_AMERICA)),
        ).day(noonUtc)
        val fajrGap = Duration.between(instantOf(auto, PrayerName.FAJR), instantOf(isna, PrayerName.FAJR))
        val ishaGap = Duration.between(instantOf(isna, PrayerName.ISHA), instantOf(auto, PrayerName.ISHA))
        assertTrue("Fajr gap was $fajrGap", fajrGap > Duration.ofMinutes(8))
        assertTrue("Isha gap was $ishaGap", ishaGap > Duration.ofMinutes(8))
    }

    @Test
    fun hanafiAsrIsLaterThanShafi() {
        val shafi = PrayerCalculator(InMemoryLocationStore(dubai)).day(noonUtc)
        val hanafi = PrayerCalculator(
            InMemoryLocationStore(dubai),
            InMemoryOffsetStore(),
            InMemoryMethodStore(MethodSettings(asr = AsrSchool.HANAFI)),
        ).day(noonUtc)
        val gap = Duration.between(instantOf(shafi, PrayerName.ASR), instantOf(hanafi, PrayerName.ASR))
        assertTrue("Asr gap was $gap", gap > Duration.ofMinutes(30))
        assertEquals(instantOf(shafi, PrayerName.DHUHR), instantOf(hanafi, PrayerName.DHUHR))
    }

    @Test
    fun changingTheMethodIsNotServedFromCache() {
        val store = InMemoryMethodStore()
        val engine = PrayerCalculator(InMemoryLocationStore(dubai), InMemoryOffsetStore(), store)
        val before = instantOf(engine.day(noonUtc), PrayerName.FAJR)
        store.write(MethodSettings(method = CalcMethod.NORTH_AMERICA))
        val after = instantOf(engine.day(noonUtc), PrayerName.FAJR)
        assertNotEquals(before, after)
    }

    @Test
    fun prefsRoundTripAndDefaults() {
        val prefs = MemoryMethodPrefs()
        assertEquals(MethodSettings.AUTO, PrefsMethodStore(prefs).read())
        PrefsMethodStore(prefs).write(MethodSettings(CalcMethod.DUBAI, AsrSchool.HANAFI))
        assertEquals(
            MethodSettings(CalcMethod.DUBAI, AsrSchool.HANAFI),
            PrefsMethodStore(prefs).read(),
        )
        PrefsMethodStore(prefs).write(MethodSettings(null, AsrSchool.HANAFI))
        assertNull(PrefsMethodStore(prefs).read().method)
        assertEquals(AsrSchool.HANAFI, PrefsMethodStore(prefs).read().asr)
    }
}

private class MemoryMethodPrefs : MethodPrefsBackend {
    private val stored = mutableMapOf<String, String>()

    override fun getString(key: String): String? = stored[key]

    override fun write(values: Map<String, String>) {
        stored.putAll(values)
    }
}
