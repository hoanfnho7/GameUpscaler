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
    fun isBinderAlive(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

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

    fun runCommand(cmd: String): List<String> {
        return try {
            val proc = newShellProcess(arrayOf("sh", "-c", cmd))
            val lines = BufferedReader(InputStreamReader(proc.inputStream)).readLines()
            proc.waitFor()
            lines
        } catch (_: Exception) {
            emptyList()
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

    @Volatile
    private var lastFpsCapPkg: String = ""

    fun resetDisplay() {
        val fpsResetCmd = if (lastFpsCapPkg.isNotEmpty()) {
            val pkg = lastFpsCapPkg
            lastFpsCapPkg = ""
            "; cmd game reset $pkg >/dev/null 2>&1 || true; device_config delete game_overlay $pkg >/dev/null 2>&1 || true"
        } else ""
        runAsync("wm size reset; wm density reset$fpsResetCmd")
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

    fun launchGameWithUpscale(pkg: String, factor: Int, fpsCap: Int = 0, refreshRate: Int = 0) {
        val dl = "$"
        val sb = StringBuilder()

        // 1. SSAA wm size / density override (skip if factor == 100 Native)
        if (factor != 100 && factor > 0) {
            sb.append("PHYS_RES=").append(dl).append("(wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'); ")
            sb.append("PHYS_DEN=").append(dl).append("(wm density | grep 'Physical density' | cut -d: -f2 | tr -d ' \\r'); ")
            sb.append("W=").append(dl).append("(echo ").append(dl).append("PHYS_RES | cut -dx -f1); ")
            sb.append("H=").append(dl).append("(echo ").append(dl).append("PHYS_RES | cut -dx -f2); ")
            sb.append("NEW_W=").append(dl).append("(( W * ").append(factor).append(" / 100 )); ")
            sb.append("NEW_H=").append(dl).append("(( H * ").append(factor).append(" / 100 )); ")
            sb.append("NEW_DEN=").append(dl).append("(( PHYS_DEN * ").append(factor).append(" / 100 )); ")
            sb.append("wm size ").append(dl).append("{NEW_W}x").append(dl).append("{NEW_H}; ")
            sb.append("wm density ").append(dl).append("{NEW_DEN}; ")
        }

        // 2. Per-game FPS Cap via Android 12+ GameManager (cmd game set --fps + device_config fallback)
        if (fpsCap > 0) {
            lastFpsCapPkg = pkg
            sb.append("(cmd game set --mode performance --fps ").append(fpsCap).append(" ").append(pkg)
                .append(" >/dev/null 2>&1 || device_config put game_overlay ").append(pkg)
                .append(" mode=2,fps=").append(fpsCap).append(":mode=3,fps=").append(fpsCap)
                .append(" >/dev/null 2>&1 || true); cmd game mode performance ").append(pkg).append(" >/dev/null 2>&1 || true; ")
        }

        // 3. Per-game Refresh Rate Lock (saves previous peak/min values to restore on exit)
        if (refreshRate > 0) {
            sb.append("OLD_PEAK=").append(dl).append("(settings get system peak_refresh_rate 2>/dev/null | tr -d ' \\r'); ")
            sb.append("OLD_MIN=").append(dl).append("(settings get system min_refresh_rate 2>/dev/null | tr -d ' \\r'); ")
            sb.append("settings put system peak_refresh_rate ").append(refreshRate).append(" >/dev/null 2>&1 || true; ")
            sb.append("settings put system min_refresh_rate ").append(refreshRate).append(" >/dev/null 2>&1 || true; ")
        }

        // 4. Launch game activity
        sb.append("sleep 1; ")
        sb.append("ACT=").append(dl).append("(cmd package resolve-activity --brief ").append(pkg).append(" | tr -d '\\r' | tail -n 1); ")
        sb.append("am start --user 0 -n \"").append(dl).append("ACT\" > /dev/null 2>&1; ")

        // 5. Auto-restore watcher in background when game process exits
        val restoreCmd = StringBuilder()
        if (factor != 100 && factor > 0) {
            restoreCmd.append("wm size reset; wm density reset; ")
        }
        if (fpsCap > 0) {
            restoreCmd.append("cmd game reset ").append(pkg).append(" >/dev/null 2>&1 || true; device_config delete game_overlay ").append(pkg).append(" >/dev/null 2>&1 || true; ")
        }
        if (refreshRate > 0) {
            restoreCmd.append("if [ -n \"\$OLD_PEAK\" ] && [ \"\$OLD_PEAK\" != \"null\" ]; then settings put system peak_refresh_rate \"\$OLD_PEAK\" >/dev/null 2>&1; else settings delete system peak_refresh_rate >/dev/null 2>&1; fi; ")
            restoreCmd.append("if [ -n \"\$OLD_MIN\" ] && [ \"\$OLD_MIN\" != \"null\" ]; then settings put system min_refresh_rate \"\$OLD_MIN\" >/dev/null 2>&1; else settings delete system min_refresh_rate >/dev/null 2>&1; fi; ")
        }

        if (restoreCmd.isNotEmpty()) {
            // Pass OLD_PEAK and OLD_MIN into subshell via env vars so single quotes don't break
            val envPrefix = if (refreshRate > 0) "OLD_PEAK=\"${dl}OLD_PEAK\" OLD_MIN=\"${dl}OLD_MIN\" " else ""
            sb.append(envPrefix)
                .append("nohup sh -c 'sleep 8; while pidof ").append(pkg)
                .append(" > /dev/null; do sleep 3; done; ")
                .append(restoreCmd.toString())
                .append("' >/dev/null 2>&1 &")
        }

        runAsync(sb.toString())
    }
}