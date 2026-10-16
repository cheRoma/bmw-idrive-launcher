package online.k73.bmwlauncher.car

import online.k73.bmwlauncher.data.CcEntry
import online.k73.bmwlauncher.data.CheckControlStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckControlTest {
    private fun hex(s: String) = s.split(" ").map { it.toInt(16) }.toIntArray()

    // Frames captured on this car (logs of 2026-07-08 and 2026-10-15).
    private val brake = hex("30 19 80 1A 35 01 20 43 48 45 43 4B 20 42 52 41 4B 45 20 4C 49 47 48 54 53 20 93")
    private val belts = hex("30 19 80 1A 37 00 20 46 41 53 54 45 4E 20 53 45 41 54 20 42 45 4C 54 53 20 20 E0")
    private val limit = hex("30 19 80 1A 37 10 20 20 20 4C 49 4D 49 54 20 20 20 36 20 4B 4D 2F 48 20 20 20 96")
    private val clear = hex("30 19 80 1A 30 00 20 20 20 20 20 20 20 20 20 20 20 20 20 20 20 20 20 20 20 20 83")

    @Test fun parses_real_frames() {
        assertEquals("CHECK BRAKE LIGHTS", CheckControl.parse(brake)?.text)
        assertEquals("FASTEN SEAT BELTS", CheckControl.parse(belts)?.text)
        assertEquals("LIMIT 6 KM/H", CheckControl.parse(limit)?.text)
        assertTrue(CheckControl.parse(clear)!!.isClear)
        assertFalse(CheckControl.parse(brake)!!.isClear)
    }

    @Test fun ignores_other_frames() {
        assertNull(CheckControl.parse(hex("80 05 BF 18 00 06 24")))
        assertNull(CheckControl.parse(hex("30 04 BF 51 00 DA")))
    }

    @Test fun decoder_delivers_cc_telegrams() {
        val got = mutableListOf<CheckControl.Telegram>()
        val d = IBusDecoder(nowMs = { 0L }, emit = {}, onCheckControl = { got += it })
        val bytes = ByteArray(brake.size) { brake[it].toByte() }
        d.feed(bytes, bytes.size)
        assertEquals("CHECK BRAKE LIGHTS", got.single().text)
    }

    @Test fun translates() {
        assertEquals("Проверьте стоп-сигналы", CheckControl.translate("CHECK BRAKE LIGHTS"))
        assertEquals("Пристегните ремни", CheckControl.translate("  FASTEN  SEAT BELTS "))
        assertEquals("Ограничение скорости 6 км/ч", CheckControl.translate("LIMIT 6 KM/H"))
        // One message, several spellings across years and markets.
        assertEquals("Проверьте ближний свет", CheckControl.translate("CHECK LOWBEAMS"))
        assertEquals("Проверьте ближний свет", CheckControl.translate("CHECK LOW BEAM LIGHTS"))
        assertEquals("Аварийный режим коробки", CheckControl.translate("TRANS. FAILSAFE PROG"))
        assertEquals("СТОП! Давление масла", CheckControl.translate("STOP!ENGINE OILPRESS"))
        assertEquals("Ограничение скорости 60 км/ч", CheckControl.translate("MAX. 60 KM/H"))
        assertEquals("Ограничение скорости 35 миль/ч", CheckControl.translate("MAX. 35 MPH"))
        // Unknown text is kept as is, never dropped.
        assertEquals("SOMETHING NEW", CheckControl.translate("SOMETHING NEW"))
        assertFalse(CheckControl.isKnown("SOMETHING NEW"))
    }

    @Test fun tracker_records_each_appearance_once() {
        val shown = mutableListOf<String>()
        var clears = 0
        val t = CheckControlTracker(onShow = { shown += it }, onClear = { clears++ })
        val b = CheckControl.parse(brake)!!
        t.feed(b); t.feed(b); t.feed(b)
        assertEquals(listOf("CHECK BRAKE LIGHTS"), shown)
        t.feed(CheckControl.parse(clear)!!)
        assertNull(t.active)
        t.feed(b)
        assertEquals(2, shown.size)
        t.reset()
        assertEquals(2, clears)
    }

    @Test fun every_message_has_a_guide() {
        assertEquals(emptyList<String>(), CheckControl.titles.filter { CheckControlGuide.forTitle(it) == null })
    }

    @Test fun severity_and_lamps() {
        assertEquals(CheckControlGuide.Severity.STOP, CheckControlGuide.severity("STOP!ENGINE OILPRESS"))
        assertEquals(CheckControlGuide.Severity.SERVICE, CheckControlGuide.severity("CHECK BRAKE LIGHTS"))
        assertEquals(CheckControlGuide.Severity.INFO, CheckControlGuide.severity("WASHER FLUID LOW"))
        assertEquals(CheckControlGuide.Severity.INFO, CheckControlGuide.severity("SOMETHING NEW"))
        val faults = setOf(Lamp.BRAKE_LEFT, Lamp.REAR_LEFT, Lamp.PLATE)
        assertEquals(listOf("левый стоп-сигнал", "левый задний габарит"), CheckControlGuide.faultyLampsFor("CHECK BRAKE LIGHTS", faults))
        assertEquals(emptyList<String>(), CheckControlGuide.faultyLampsFor("DOOR OPEN", faults))
        assertEquals(true, CheckControlGuide.hint(faults) != null)
        assertNull(CheckControlGuide.hint(setOf(Lamp.BRAKE_LEFT, Lamp.REAR_RIGHT)))
    }

    @Test fun store_round_trips() {
        val list = listOf(CcEntry(1_792_086_720_436, "CHECK BRAKE LIGHTS"), CcEntry(5, "LIMIT 6 KM/H"))
        assertEquals(list, CheckControlStore.decode(CheckControlStore.encode(list)))
        assertEquals(emptyList<CcEntry>(), CheckControlStore.decode(""))
    }
}
