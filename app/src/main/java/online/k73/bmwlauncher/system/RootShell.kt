package online.k73.bmwlauncher.system

import java.io.BufferedReader

/** Executes commands through `su -c`. Not unit-tested (needs root); exercised on device. */
class RootShell : Shell {
    override fun exec(command: String): ShellResult {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(false)
                .start()
            val out = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val err = process.errorStream.bufferedReader().use(BufferedReader::readText)
            val code = process.waitFor()
            ShellResult(code, out.trim(), err.trim())
        } catch (t: Throwable) {
            ShellResult(exitCode = 127, stdout = "", stderr = t.message ?: "su failed")
        }
    }
}
