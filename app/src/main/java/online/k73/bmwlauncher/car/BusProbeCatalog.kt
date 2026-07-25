package online.k73.bmwlauncher.car

/** Risk class of a probe telegram — drives the ignition gate + colour on the probe screen. */
enum class ProbeRisk { SAFE, DANGER }

/**
 * One I-Bus telegram the bus-probe screen can send. [bytesNoChk] is the frame WITHOUT its trailing
 * XOR checksum — the checksum is always computed ([MirrorTelegrams.checksum]) so a hand-typed value
 * can never put a malformed frame on the wire.
 *
 * Extracted from the decompiled OEM «i-Bus App» — see `~/bmw-ibus-recon/ibus-write-catalog.md`.
 * Every [label] is a CLAIM to verify by watching the car, NOT proven behaviour: on this car's
 * General Module the OEM "mirror" job actually drives the WINDOWS (window incident 2026-07-21).
 */
class ProbeCmd(
    val label: String,
    private val bytesNoChk: IntArray,
    val risk: ProbeRisk,
    val note: String = "",
) {
    /** Full frame with the computed XOR checksum appended. */
    val frame: IntArray get() = bytesNoChk + MirrorTelegrams.checksum(bytesNoChk)
    val hex: String get() = frame.joinToString(" ") { "%02X".format(it) }
}

/**
 * The telegrams the probe screen exposes, split by risk. SAFE ones only write to the instrument
 * cluster / OBC / aux systems (nothing mechanical moves). DANGER ones are the body-relay family
 * (GM job 0x0C and friends) that can move windows / locks / tailgate — this is where the real
 * mirror actuation must be hunted, so they stay behind the ignition gate + a per-tap confirm.
 */
object BusProbeCatalog {
    val safe: List<ProbeCmd> = listOf(
        ProbeCmd("CheckControl: сброс лимита", intArrayOf(0x3B, 0x05, 0x80, 0x41, 0x09, 0x08), ProbeRisk.SAFE, "убирает LIMIT 6 KM/H"),
        ProbeCmd("Догреватель ВКЛ", intArrayOf(0x3B, 0x04, 0x80, 0x41, 0x12), ProbeRisk.SAFE, "Standheizung"),
        ProbeCmd("Догреватель ВЫКЛ", intArrayOf(0x3B, 0x04, 0x80, 0x41, 0x11), ProbeRisk.SAFE),
        ProbeCmd("Довентиляция ВКЛ", intArrayOf(0x3B, 0x04, 0x80, 0x41, 0x14), ProbeRisk.SAFE),
        ProbeCmd("Довентиляция ВЫКЛ", intArrayOf(0x3B, 0x04, 0x80, 0x41, 0x13), ProbeRisk.SAFE),
        ProbeCmd("БК: средняя скорость", intArrayOf(0x3B, 0x05, 0x80, 0x41, 0x0A, 0x01), ProbeRisk.SAFE),
        ProbeCmd("БК: расход", intArrayOf(0x3B, 0x05, 0x80, 0x41, 0x04, 0x01), ProbeRisk.SAFE),
        ProbeCmd("БК: запас хода", intArrayOf(0x3B, 0x05, 0x80, 0x41, 0x06, 0x01), ProbeRisk.SAFE),
        ProbeCmd("Гонг IKE", intArrayOf(0xBF, 0x03, 0x80, 0x1D), ProbeRisk.SAFE),
    )

    val danger: List<ProbeCmd> = listOf(
        // Most likely real-mirror candidates: the seat/mirror-memory modules (not the GM window job).
        ProbeCmd("Память зеркал 0x9B · 05", intArrayOf(0x3F, 0x05, 0x9B, 0x0C, 0x05, 0x00), ProbeRisk.DANGER, "кандидат на зеркало"),
        ProbeCmd("Память зеркал 0x9B · 06", intArrayOf(0x3F, 0x05, 0x9B, 0x0C, 0x06, 0x00), ProbeRisk.DANGER, "кандидат на зеркало"),
        ProbeCmd("Память зеркал 0x51 · 05", intArrayOf(0x3F, 0x05, 0x51, 0x0C, 0x05, 0x00), ProbeRisk.DANGER, "кандидат на зеркало"),
        ProbeCmd("Память зеркал 0x51 · 06", intArrayOf(0x3F, 0x05, 0x51, 0x0C, 0x06, 0x00), ProbeRisk.DANGER, "кандидат на зеркало"),
        // CONFIRMED mirrors on this car (2026-07-23): 0x39 = fold, 0x3A = unfold; side 01=left, 02=right.
        ProbeCmd("✅ Зеркало: сложить ПРАВОЕ", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x02, 0x39, 0x01), ProbeRisk.DANGER, "зеркало"),
        ProbeCmd("✅ Зеркало: сложить ЛЕВОЕ", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x01, 0x39, 0x01), ProbeRisk.DANGER, "зеркало"),
        ProbeCmd("✅ Зеркало: разложить ПРАВОЕ", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x02, 0x3A, 0x01), ProbeRisk.DANGER, "зеркало"),
        ProbeCmd("✅ Зеркало: разложить ЛЕВОЕ", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x01, 0x3A, 0x01), ProbeRisk.DANGER, "зеркало"),
        // CONFIRMED windows on this car — the old "mirror" codes. Keep for reference, clearly flagged.
        ProbeCmd("⚠ОКНО 01·31", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x01, 0x31, 0x01), ProbeRisk.DANGER, "открывает окно"),
        ProbeCmd("⚠ОКНО 01·30", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x01, 0x30, 0x01), ProbeRisk.DANGER, "окно"),
        ProbeCmd("⚠ОКНО 02·31", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x02, 0x31, 0x01), ProbeRisk.DANGER, "открывает окно"),
        ProbeCmd("⚠ОКНО 02·30", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x02, 0x30, 0x01), ProbeRisk.DANGER, "окно"),
        // Representative sample of the undecoded GM relay codes.
        ProbeCmd("GM relay 03·02", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x03, 0x02, 0x01), ProbeRisk.DANGER),
        ProbeCmd("GM relay 03·06", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x03, 0x06, 0x01), ProbeRisk.DANGER),
        ProbeCmd("GM relay 01·19", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x01, 0x19, 0x01), ProbeRisk.DANGER),
        ProbeCmd("GM relay 02·21", intArrayOf(0x3F, 0x06, 0x00, 0x0C, 0x02, 0x21, 0x01), ProbeRisk.DANGER),
        ProbeCmd("Багажник 0x24 ⚠", intArrayOf(0x3F, 0x05, 0x24, 0x0C, 0x00, 0x01), ProbeRisk.DANGER, "может открыть багажник"),
    )
}
