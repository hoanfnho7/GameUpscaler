package com.nho.upscaler

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.concurrent.thread

object ShizukuHelper {
    fun newShellProcess(cmd: Array<String>): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, cmd, null, null) as Process
    }

    fun ensureReady(ctx: Context): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(ctx, Lang.str(ctx, R.string.toast_start_shizuku), Toast.LENGTH_LONG).show()
                false
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
                false
            } else {
                true
            }
        } catch (_: Exception) {
            Toast.makeText(ctx, Lang.str(ctx, R.string.toast_shizuku_error), Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun runAsync(cmd: String) {
        thread {
            try {
                val proc = newShellProcess(arrayOf("sh", "-c", cmd))
                proc.waitFor()
            } catch (_: Exception) {}
        }
    }

    fun resetDisplay() {
        runAsync("wm size reset; wm density reset")
    }

    fun fetchPhysicalDisplay(onResult: (w: Int, h: Int, density: Int) -> Unit) {
        thread {
            try {
                val cmd = "wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'; " +
                        "wm density | grep 'Physical density' | cut -d: -f2 | tr -d ' \\r'"
                val proc = newShellProcess(arrayOf("sh", "-c", cmd))
                val lines = BufferedReader(InputStreamReader(proc.inputStream)).readLines()
                proc.waitFor()
                if (lines.size >= 2) {
                    val parts = lines[0].split("x")
                    val w = parts.getOrNull(0)?.toIntOrNull() ?: 1440
                    val h = parts.getOrNull(1)?.toIntOrNull() ?: 3168
                    val den = lines[1].toIntOrNull() ?: 624
                    onResult(w, h, den)
                }
            } catch (_: Exception) {}
        }
    }

    fun scanThermalZones(onSuccess: (count: Int, report: String) -> Unit, onError: (String) -> Unit) {
        thread {
            try {
                val cmd = "for z in /sys/class/thermal/thermal_zone*; do " +
                        "if [ -r \"\$z/type\" ] && [ -r \"\$z/temp\" ]; then " +
                        "read -r t < \"\$z/type\"; read -r v < \"\$z/temp\"; " +
                        "printf \"%s|%s\\n\" \"\$t\" \"\$v\"; fi; done"
                val proc = newShellProcess(arrayOf("sh", "-c", cmd))
                val lines = BufferedReader(InputStreamReader(proc.inputStream)).readLines()
                proc.waitFor()

                val report = StringBuilder()
                for (line in lines) {
                    val parts = line.split("|")
                    if (parts.size == 2) {
                        val name = parts[0].trim()
                        val raw = parts[1].trim().toFloatOrNull() ?: continue
                        val c = when {
                            raw > 1000f -> raw / 1000f
                            raw > 200f -> raw / 10f
                            else -> raw
                        }
                        if (c in 15f..125f) {
                            report.append(String.format(Locale.US, "• %-22s : %.1f°C\n", name, c))
                        }
                    }
                }
                onSuccess(lines.size, report.toString())
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            }
        }
    }

    fun launchGameWithUpscale(pkg: String, factor: Int) {
        val dl = "$"
        val script = "PHYS_RES=" + dl + "(wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'); " +
                "PHYS_DEN=" + dl + "(wm density | grep 'Physical density' | cut -d: -f2 | tr -d ' \\r'); " +
                "W=" + dl + "(echo " + dl + "PHYS_RES | cut -dx -f1); " +
                "H=" + dl + "(echo " + dl + "PHYS_RES | cut -dx -f2); " +
                "NEW_W=" + dl + "(( W * " + factor + " / 100 )); " +
                "NEW_H=" + dl + "(( H * " + factor + " / 100 )); " +
                "NEW_DEN=" + dl + "(( PHYS_DEN * " + factor + " / 100 )); " +
                "wm size " + dl + "{NEW_W}x" + dl + "{NEW_H}; " +
                "wm density " + dl + "{NEW_DEN}; " +
                "sleep 1; " +
                "ACT=" + dl + "(cmd package resolve-activity --brief " + pkg + " | tr -d '\\r' | tail -n 1); " +
                "am start --user 0 -n \"" + dl + "ACT\" > /dev/null 2>&1; " +
                "nohup sh -c 'sleep 8; while pidof " + pkg + " > /dev/null; do sleep 3; done; wm size reset; wm density reset' >/dev/null 2>&1 &"
        runAsync(script)
    }
}