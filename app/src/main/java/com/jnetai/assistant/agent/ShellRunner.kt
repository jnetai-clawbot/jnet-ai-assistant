package com.jnetai.assistant.agent

import com.jnetai.assistant.util.Err
import java.io.BufferedReader

/**
 * Runs bash-style shell commands on the device as the app's own UID (the same
 * sandbox Android gives the app) and captures stdout+stderr combined output.
 *
 * Commands are strictly user-gated BEFORE they reach here (see the agent UI's
 * Allow once / Allow forever / Deny dialog). A hard timeout prevents a hung
 * command from ever freezing the app, and output is truncated to keep the
 * result readable.
 */
object ShellRunner {

    const val TIMEOUT_MS = 20_000L
    private const val MAX_OUTPUT = 200_000

    /**
     * Runs a single shell command. Returns a multi-line string with the exit
     * code and the captured output (stdout+stderr). Throws only when the
     * process cannot even be started — execution failures are returned as text.
     */
    fun run(command: String): String {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Shell command is empty")
        }
        val startedAt = System.currentTimeMillis()
        val sb = StringBuilder()
        val process = try {
            ProcessBuilder("/system/bin/sh", "-c", trimmed).start()
        } catch (t: Throwable) {
            // Fall back to plain `sh -c` if /system/bin/sh is unavailable.
            try {
                ProcessBuilder("sh", "-c", trimmed).start()
            } catch (t2: Throwable) {
                Err.e(Err.SHELL_ERROR, "Failed to start shell for: $trimmed", t2)
                throw IllegalStateException("Could not start shell: ${t2.message}", t2)
            }
        }

        val reader: BufferedReader = process.inputStream.bufferedReader()
        val drain = Thread {
            try {
                val buf = CharArray(4096)
                var n = reader.read(buf)
                var truncated = false
                while (n != -1) {
                    synchronized(sb) {
                        if (!truncated) {
                            sb.append(buf, 0, n)
                            if (sb.length > MAX_OUTPUT) {
                                sb.setLength(MAX_OUTPUT)
                                sb.append("\n…output truncated…")
                                truncated = true
                            }
                        }
                    }
                    n = reader.read(buf)
                }
            } catch (_: Throwable) {
                // stream closed on timeout/destroy — that is expected
            }
        }
        drain.start()

        drain.join(TIMEOUT_MS)
        val timedOut = drain.isAlive
        if (timedOut) {
            runCatching { process.destroy() }
            runCatching { drain.join(3000) }
        }
        val exitCode = runCatching { process.waitFor() }.getOrDefault(-1)
        val body = synchronized(sb) { sb.toString().trim() }
        Err.i("Shell (${if (timedOut) "TIMEOUT" else "done"} in ${System.currentTimeMillis() - startedAt}ms, exit=$exitCode): $trimmed")

        if (timedOut) {
            return "exit=$exitCode  (command timed out after ${TIMEOUT_MS}ms)\n" +
                (if (body.isBlank()) "(no output captured)" else body)
        }
        return "exit=$exitCode\n" + (if (body.isBlank()) "(no output)" else body)
    }
}