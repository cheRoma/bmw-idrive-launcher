package online.k73.bmwlauncher.car

/**
 * Check Control — the cluster's warning texts («CHECK BRAKE LIGHTS», «FASTEN SEAT BELTS» …).
 * Pure and unit-tested.
 *
 * The check-control module (0x30) hands every message to the IKE as plain ASCII, and we only read it:
 *
 *     30 19 80 1A <mode> <flags> <20 chars of text> <chk>
 *
 * Seen on this car: mode 0x35 / 0x37 with text = show, mode 0x30 with 20 spaces = the message is gone.
 * The flags byte varies per message (0x00, 0x01, 0x05, 0x10) and is kept only for the log.
 */
object CheckControl {
    const val SRC_CCM = 0x30
    const val DST_IKE = 0x80
    const val CMD_TEXT = 0x1A
    private const val MODE_CLEAR = 0x30

    /** One decoded telegram. [text] is trimmed with inner runs of spaces collapsed; blank = clear. */
    data class Telegram(val mode: Int, val flags: Int, val text: String) {
        val isClear: Boolean get() = mode == MODE_CLEAR || text.isEmpty()
    }

    /** `m` = a whole valid frame `[src, len, dst, cmd, …, chk]`, or null if it is not a CC text. */
    fun parse(m: IntArray): Telegram? {
        if (m.size < 7 || m[0] != SRC_CCM || m[2] != DST_IKE || m[3] != CMD_TEXT) return null
        val raw = buildString {
            for (i in 6 until m.size - 1) {
                val b = m[i]
                append(if (b in 0x20..0x7E) b.toChar() else ' ')
            }
        }
        return Telegram(mode = m[4], flags = m[5], text = normalize(raw))
    }

    fun normalize(s: String): String = s.trim().replace(Regex(" {2,}"), " ").uppercase()

    /**
     * Russian for a cluster text. Exact table first, then the open-ended patterns (a speed limit
     * carries its number), and the original text as the last resort — an untranslated warning is
     * still a warning, it must never be dropped.
     */
    fun translate(text: String): String {
        val t = normalize(text)
        RU[key(t)]?.let { return it }
        SPEED.find(t)?.let { return "Ограничение скорости ${it.groupValues[1]} ${unit(it.groupValues[2])}" }
        return t
    }

    /** True when [translate] has a real translation, not the fallback. Logged so gaps get filled. */
    fun isKnown(text: String): Boolean {
        val t = normalize(text)
        return key(t) in RU || SPEED.containsMatchIn(t)
    }

    /** Letters and digits only: the cluster spells one message several ways across years and markets
     *  («CHECK LOWBEAMS», «CHECK LOW BEAM LIGHTS», «TRANS. FAILSAFE PROG»), the table lists each once. */
    private fun key(t: String) = t.filter { it.isLetterOrDigit() }

    /** Every Russian title the table can produce, for the guide-coverage test. */
    internal val titles: Set<String> get() = RU.values.toSet()

    // «LIMIT 6 KM/H» (the OBC limit), «MAX. 60 KM/H» / «MAX. 35 MPH» (winter tyres) — the number varies.
    private val SPEED = Regex("(?:LIMIT|MAX\\.?) ?(\\d+) ?(KM/H|MPH)")
    private fun unit(u: String) = if (u == "MPH") "миль/ч" else "км/ч"

    // E53 / E39 / E38 English cluster texts (owner's handbooks + enthusiast lists, 2026-10-16).
    private val RU: Map<String, String> = listOf(
        // Lamps
        "CHECK BRAKE LIGHTS" to "Проверьте стоп-сигналы",
        "BRAKE LAMP CIRCUIT" to "Неисправна цепь стоп-сигналов",
        "CHECK LOW BEAM LIGHTS" to "Проверьте ближний свет",
        "CHECK LOWBEAMS" to "Проверьте ближний свет",
        "CHECK LOW BEAM BULB" to "Проверьте лампу ближнего света",
        "CHECK HIGHBEAM LIGHT" to "Проверьте дальний свет",
        "CHECK HIGHBEAMS" to "Проверьте дальний свет",
        "CHECK HIGH BEAM BULB" to "Проверьте лампу дальнего света",
        "CHECK FRONT FOGLAMPS" to "Проверьте передние противотуманки",
        "CHECK REAR FOGLIGHTS" to "Проверьте задний противотуманный фонарь",
        "CHECK SIDE LIGHTS" to "Проверьте габариты",
        "CHECK SIDE LIGHT" to "Проверьте габариты",
        "CHECK SIDE LAMPS" to "Проверьте габариты",
        "CHECK PARK LIGHTS" to "Проверьте габариты",
        "CHECK PARKING LIGHT" to "Проверьте габариты",
        "CHECK TAIL LIGHTS" to "Проверьте задние фонари",
        "CHECK REAR LIGHTS" to "Проверьте задние фонари",
        "CHECK REAR LAMPS" to "Проверьте задние фонари",
        "CHECK LICPLATE LIGHT" to "Проверьте подсветку номера",
        "CHECK LICPLATE LAMP" to "Проверьте подсветку номера",
        "CHECK NUMPLATE LIGHT" to "Проверьте подсветку номера",
        "CHECK REVERSE LIGHTS" to "Проверьте фонари заднего хода",
        "CHECK BACK UP LIGHTS" to "Проверьте фонари заднего хода",
        "CHECK BACKUP LAMPS" to "Проверьте фонари заднего хода",
        "CHECK TRAILER LIGHTS" to "Проверьте фонари прицепа",
        "LIGHTS ON" to "Не выключен свет",
        // Fluids
        "CHECK ENGINE OIL LEVEL" to "Проверьте уровень масла",
        "CHECK ENGINE OIL LEV" to "Проверьте уровень масла",
        "CHECK ENGINE OIL" to "Проверьте масло в двигателе",
        "STOP! ENGINE OILPRESS" to "СТОП! Давление масла",
        "CHECK COOLANT LEVEL" to "Проверьте уровень охлаждающей жидкости",
        "COOLANT TEMPERATURE" to "Перегрев двигателя",
        "CHECK BRAKE FLUID" to "Проверьте тормозную жидкость",
        "WASHER FLUID LOW" to "Мало жидкости омывателя",
        "CHECK FILLER CAP" to "Проверьте крышку бензобака",
        "CHECK GAS CAP" to "Проверьте крышку бензобака",
        // Body
        "DOOR OPEN" to "Открыта дверь",
        "BONNET OPEN" to "Открыт капот",
        "HOOD OPEN" to "Открыт капот",
        "BOOTLID OPEN" to "Открыта крышка багажника",
        "TRUNK LID OPEN" to "Открыта крышка багажника",
        "TAILGATE OPEN" to "Открыта крышка багажника",
        "FASTEN SEAT BELTS" to "Пристегните ремни",
        "RELEASE PARKING BRAKE" to "Снимите со стояночного тормоза",
        "KEY IN IGNITION LOCK" to "Ключ в замке зажигания",
        "KEY IN IGNITION" to "Ключ в замке зажигания",
        "REMOTE KEY BATTERY" to "Замените батарейку ключа",
        // Brakes, tyres
        "CHECK BRAKE LININGS" to "Проверьте тормозные колодки",
        "CHECK TIRE PRESSURE" to "Проверьте давление в шинах",
        "CHECK TYRE PRESSURE" to "Проверьте давление в шинах",
        "SET TYRE PRESSURE" to "Задайте давление в шинах",
        "TIRE PRESSURE SET" to "Давление в шинах задано",
        "FLAT TIRE" to "Спущено колесо",
        "TIRE LOW/FLAT" to "Спущено колесо",
        "TYRE DEFECT" to "Повреждена шина",
        "TIRE CHECK INACTIVE" to "Контроль шин отключён",
        "TIRECONTROL INACTIVE" to "Контроль шин отключён",
        "TYRECONTROL INACTIVE" to "Контроль шин отключён",
        // Drivetrain
        "ENGINE FAILSAFE PROG" to "Аварийный режим двигателя",
        "FAILSAFE PROG ACTIVE" to "Включён аварийный режим",
        "TRANS FAILSAFE PROG" to "Аварийный режим коробки",
        "TRANS PROG FAILSAFE" to "Аварийный режим коробки",
        "TRANSMISS'N OVERHEAT" to "Перегрев коробки передач",
        "FUEL INJECT.SYSTEM" to "Неисправна система впрыска",
        "SLOW! CAT. OVERHEAT" to "Медленнее! Перегрев катализатора",
        "DSC/4X4 INACTIVE" to "DSC/4x4 отключены",
        "LEVEL CONTROL INACTIVE" to "Пневмоподвеска отключена",
        "SELFLEVEL SUSP.INACT" to "Пневмоподвеска отключена",
        "PRE-HEATING" to "Прогрев свечей",
        "TRAILER MODE" to "Режим прицепа",
        // Misc
        "SPEED LIMIT" to "Ограничение скорости",
        "LIMIT" to "Ограничение скорости",
        "CHECK CONTROL OK" to "Check Control: всё в порядке",
        "BMW ASSIST INACTIVE" to "BMW Assist отключён",
    ).associate { (en, ru) -> key(en) to ru }
}

/**
 * What the cluster is showing now and what it has shown before. Fed every CC telegram; repeats of
 * the message already on screen are ignored, so a warning the module re-sends every few seconds
 * lands in the history once per appearance.
 */
class CheckControlTracker(
    private val onShow: (text: String) -> Unit,
    private val onClear: () -> Unit,
) {
    var active: String? = null
        private set

    fun feed(t: CheckControl.Telegram) {
        if (t.isClear) {
            if (active != null) { active = null; onClear() }
            return
        }
        if (t.text == active) return
        active = t.text
        onShow(t.text)
    }

    /** Key off: the cluster goes dark, so nothing is showing any more. */
    fun reset() {
        if (active != null) { active = null; onClear() }
    }
}
