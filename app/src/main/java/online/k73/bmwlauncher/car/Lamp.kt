package online.k73.bmwlauncher.car

/**
 * Exterior lamps the light module (LCM, 0xD0) reports as faulty in its lamp-status broadcast:
 *
 *     D0 08 BF 5B <on> <fault> <rear on> <fault per side> <?> <chk>
 *
 * Bit layout per kmalinich/node-bmw-client `modules/LCM.js` (E38/E39/E53). Confirmed on this car on
 * 2026-10-15: with «CHECK BRAKE LIGHTS» lit the LCM sent `00 00 0A 0A 00`, i.e. left brake light and
 * left rear tail light — both in the left rear lamp.
 */
enum class Lamp(val ru: String) {
    FRONT_PARKING("передние габариты"),
    LOW_BEAM("ближний свет"),
    HIGH_BEAM("дальний свет"),
    FOG_FRONT("передние противотуманки"),
    FOG_REAR("задний противотуманный"),
    TURN_LEFT("левый поворотник"),
    TURN_RIGHT("правый поворотник"),
    PLATE("подсветка номера"),
    BRAKE_RIGHT("правый стоп-сигнал"),
    BRAKE_LEFT("левый стоп-сигнал"),
    REAR_RIGHT("правый задний габарит"),
    REAR_LEFT("левый задний габарит"),
    LOW_BEAM_RIGHT("правый ближний"),
    LOW_BEAM_LEFT("левый ближний"),
    ;

    companion object {
        private const val SRC_LCM = 0xD0
        private const val CMD_STATUS = 0x5B

        private val BYTE_FAULT = listOf(FRONT_PARKING, LOW_BEAM, HIGH_BEAM, FOG_FRONT, FOG_REAR, TURN_LEFT, TURN_RIGHT, PLATE)
        private val BYTE_SIDE = listOf(BRAKE_RIGHT, BRAKE_LEFT, REAR_RIGHT, REAR_LEFT, LOW_BEAM_RIGHT, LOW_BEAM_LEFT)

        /** Faulty lamps from a whole frame, or null when it is not the LCM lamp status. */
        fun faults(m: IntArray): Set<Lamp>? {
            if (m.size < 9 || m[0] != SRC_LCM || m[3] != CMD_STATUS) return null
            val out = LinkedHashSet<Lamp>()
            BYTE_FAULT.forEachIndexed { bit, lamp -> if (m[5] and (1 shl bit) != 0) out += lamp }
            BYTE_SIDE.forEachIndexed { bit, lamp -> if (m[7] and (1 shl bit) != 0) out += lamp }
            // The per-side low-beam bits say more than the aggregate one; show only the specific.
            if (LOW_BEAM_LEFT in out || LOW_BEAM_RIGHT in out) out -= LOW_BEAM
            return out
        }
    }
}
