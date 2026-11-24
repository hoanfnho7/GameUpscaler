package com.nho.upscaler

import android.content.Context
import android.content.pm.PackageManager
import android.os.Parcel
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
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

    private const val VIVO_RMS_MAGIC_HANDLE = 831204701L
    private const val VIVO_RMS_TX_RELEASE = 24
    private const val VIVO_RMS_TX_SET_FIX_RATE = 36
    private const val HZ_RESTORE_SCRIPT = "/data/local/tmp/gameupscaler_hz_restore.sh"

    private data class RefreshSettingKey(
        val namespace: String,
        val key: String,
        val decimal: Boolean
    )

    // Full 7-key set (AOSP + Vivo/OriginOS) used by OriginTools
    private val REFRESH_SETTINGS_KEYS = listOf(
        RefreshSettingKey("system", "peak_refresh_rate", true),
        RefreshSettingKey("system", "min_refresh_rate", true),
        RefreshSettingKey("global", "peak_refresh_rate", false),
        RefreshSettingKey("global", "min_refresh_rate", false),
        RefreshSettingKey("secure", "refresh_rate_mode", false),
        RefreshSettingKey("system", "vivo_screen_refresh_rate_mode", false),
        RefreshSettingKey("global", "vivo_screen_refresh_rate_mode", false)
    )

    private val launchSessionId = AtomicLong(0L)

    @Volatile
    private var lastFpsCapPkg: String = ""

    @Volatile
    private var isVivoRmsLocked: Boolean = false

    fun isVivoRmsAvailable(): Boolean {
        return try {
            if (!isBinderAlive()) return false
            val binder = SystemServiceHelper.getSystemService("rms")
            binder != null && binder.isBinderAlive
        } catch (_: Throwable) {
            false
        }
    }

    fun applyVivoRmsRefreshRate(hz: Int): Boolean {
        if (hz <= 0) return releaseVivoRms()
        val ok = transactVivoRms(hz)
        if (ok) isVivoRmsLocked = true
        return ok
    }

    fun releaseVivoRms(): Boolean {
        val ok = transactVivoRms(0)
        isVivoRmsLocked = false
        return ok
    }

    private fun transactVivoRms(hz: Int): Boolean {
        return try {
            if (!isBinderAlive()) return false
            val raw = SystemServiceHelper.getSystemService("rms") ?: return false
            val wrapped = ShizukuBinderWrapper(raw)
            if (!wrapped.isBinderAlive) return false
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                if (hz == 0) {
                    data.writeInt(0)
                    data.writeLong(VIVO_RMS_MAGIC_HANDLE)
                } else {
                    data.writeLong(VIVO_RMS_MAGIC_HANDLE)
                    data.writeString("FixRate")
                    data.writeString("StarTravelPRO_Fixed$hz")
                    val params = intArrayOf(0, 0, hz, 0, 0, 0, 100, 0, 0, 0, 15, 0, 0, 0)
                    for (v in params) {
                        data.writeInt(v)
                    }
                }
                val code = if (hz == 0) VIVO_RMS_TX_RELEASE else VIVO_RMS_TX_SET_FIX_RATE
                val ok = wrapped.transact(code, data, reply, 0)
                if (!ok) return false
                if (reply.dataSize() >= 4) {
                    reply.setDataPosition(0)
                    if (reply.readInt() < 0) {
                        reply.setDataPosition(0)
                        reply.readException()
                    }
                }
                true
            } finally {
                reply.recycle()
                data.recycle()
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildBackupRefreshSettingsCmd(includeDisplayMode: Boolean): String {
        val sb = StringBuilder()
        sb.append("if [ ! -f ").append(HZ_RESTORE_SCRIPT).append(" ]; then ")
        sb.append("umask 077; : > ").append(HZ_RESTORE_SCRIPT).append("; ")
        for (item in REFRESH_SETTINGS_KEYS) {
            sb.append("VAL=\$(settings get ").append(item.namespace).append(" ").append(item.key)
                .append(" 2>/dev/null | tr -d '\\r'); ")
            sb.append("if [ -n \"\$VAL\" ] && [ \"\$VAL\" != \"null\" ]; then ")
                .append("printf 'settings put ").append(item.namespace).append(" ").append(item.key)
                .append(" %s >/dev/null 2>&1 || true\\n' \"\$VAL\" >> ").append(HZ_RESTORE_SCRIPT).append("; ")
                .append("else ")
                .append("printf 'settings delete ").append(item.namespace).append(" ").append(item.key)
                .append(" >/dev/null 2>&1 || true\\n' >> ").append(HZ_RESTORE_SCRIPT).append("; ")
                .append("fi; ")
        }
        if (includeDisplayMode) {
            sb.append("printf 'cmd display clear-user-preferred-display-mode 0 >/dev/null 2>&1 || true\\n' >> ")
                .append(HZ_RESTORE_SCRIPT).append("; ")
        }
        sb.append("fi; ")
        return sb.toString()
    }

    private fun buildApplyRefreshSettingsCmd(hz: Int, includeDisplayMode: Boolean): String {
        val sb = StringBuilder()
        for (item in REFRESH_SETTINGS_KEYS) {
            val valueStr = if (item.decimal) "$hz.0" else hz.toString()
            sb.append("settings put ").append(item.namespace).append(" ").append(item.key)
                .append(" ").append(valueStr).append(" >/dev/null 2>&1 || true; ")
        }
        if (includeDisplayMode) {
            sb.append("PHYS_RES_HZ=\$(wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'); ")
            sb.append("PW=\$(echo \$PHYS_RES_HZ | cut -dx -f1); PH=\$(echo \$PHYS_RES_HZ | cut -dx -f2); ")
            sb.append("if [ -n \"\$PW\" ] && [ -n \"\$PH\" ]; then ")
                .append("cmd display set-user-preferred-display-mode \$PW \$PH ").append(hz)
                .append(".0 0 >/dev/null 2>&1 || true; fi; ")
        }
        return sb.toString()
    }

    private fun buildRestoreRefreshSettingsCmd(): String {
        return "if [ -f $HZ_RESTORE_SCRIPT ]; then sh $HZ_RESTORE_SCRIPT >/dev/null 2>&1 || true; rm -f $HZ_RESTORE_SCRIPT >/dev/null 2>&1 || true; fi"
    }

    fun resetDisplay() {
        launchSessionId.incrementAndGet()
        thread {
            try {
                if (isVivoRmsLocked || isVivoRmsAvailable()) {
                    releaseVivoRms()
                }
            } catch (_: Throwable) {}

            val fpsResetCmd = if (lastFpsCapPkg.isNotEmpty()) {
                val pkg = lastFpsCapPkg
                lastFpsCapPkg = ""
                "; cmd game reset $pkg >/dev/null 2>&1 || true; device_config delete game_overlay $pkg >/dev/null 2>&1 || true"
            } else ""
            val hzResetCmd = "; ${buildRestoreRefreshSettingsCmd()}"
            try {
                val proc = newShellProcess(arrayOf("sh", "-c", "wm size reset; wm density reset$fpsResetCmd$hzResetCmd"))
                proc.waitFor()
            } catch (_: Exception) {}
        }
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
        val mySession = launchSessionId.incrementAndGet()
        thread {
            try {
                val dl = "$"
                val hasVivoRms = isVivoRmsAvailable()
                // On Vivo/iQOO (OriginOS), Android's `cmd game --fps` is ignored by GameCube/PEM.
                // If user only set fpsCap (>= 60) with refreshRate == Auto, also lock RMS Hz to fpsCap
                // so VSync games are clamped/unlocked cleanly at that target rate.
                val effectiveHz = when {
                    refreshRate > 0 -> refreshRate
                    hasVivoRms && fpsCap >= 60 -> fpsCap
                    else -> 0
                }

                // Release any previous Vivo RMS lock before applying a fresh one (matches OriginTools gu0)
                if (hasVivoRms && (isVivoRmsLocked || effectiveHz > 0)) {
                    releaseVivoRms()
                }

                val setupSb = StringBuilder()

                // 1. SSAA wm size / density override (skip if factor == 100 Native)
                if (factor != 100 && factor > 0) {
                    setupSb.append("PHYS_RES=").append(dl).append("(wm size | grep 'Physical size' | cut -d: -f2 | tr -d ' \\r'); ")
                    setupSb.append("PHYS_DEN=").append(dl).append("(wm density | grep 'Physical density' | cut -d: -f2 | tr -d ' \\r'); ")
                    setupSb.append("W=").append(dl).append("(echo ").append(dl).append("PHYS_RES | cut -dx -f1); ")
                    setupSb.append("H=").append(dl).append("(echo ").append(dl).append("PHYS_RES | cut -dx -f2); ")
                    setupSb.append("NEW_W=").append(dl).append("(( W * ").append(factor).append(" / 100 )); ")
                    setupSb.append("NEW_H=").append(dl).append("(( H * ").append(factor).append(" / 100 )); ")
                    setupSb.append("NEW_DEN=").append(dl).append("(( PHYS_DEN * ").append(factor).append(" / 100 )); ")
                    setupSb.append("wm size ").append(dl).append("{NEW_W}x").append(dl).append("{NEW_H}; ")
                    setupSb.append("wm density ").append(dl).append("{NEW_DEN}; ")
                }

                // 2. Per-game FPS Cap via Android 12+ GameManager (works on AOSP / Pixel / Samsung / Xiaomi)
                if (fpsCap > 0) {
                    lastFpsCapPkg = pkg
                    setupSb.append("(cmd game set --mode performance --fps ").append(fpsCap).append(" ").append(pkg)
                        .append(" >/dev/null 2>&1 || device_config put game_overlay ").append(pkg)
                        .append(" mode=2,fps=").append(fpsCap).append(":mode=3,fps=").append(fpsCap)
                        .append(" >/dev/null 2>&1 || true); cmd game mode performance ").append(pkg).append(" >/dev/null 2>&1 || true; ")
                }

                // 3. Per-game Refresh Rate Lock: backup & write all 7 AOSP + Vivo Settings keys
                //    (+ cmd display user-preferred-display-mode on non-Vivo devices)
                if (effectiveHz > 0) {
                    setupSb.append(buildBackupRefreshSettingsCmd(includeDisplayMode = !hasVivoRms))
                    setupSb.append(buildApplyRefreshSettingsCmd(effectiveHz, includeDisplayMode = !hasVivoRms))
                }

                if (setupSb.isNotEmpty()) {
                    val setupProc = newShellProcess(arrayOf("sh", "-c", setupSb.toString()))
                    setupProc.waitFor()
                }

                // 4. Vivo/iQOO OriginOS RMS Binder FixRate lock (must be sent AFTER the 7 Settings keys)
                if (effectiveHz > 0 && hasVivoRms) {
                    applyVivoRmsRefreshRate(effectiveHz)
                }

                // 5. Launch game activity
                val launchCmd = "sleep 1; " +
                    "ACT=${dl}(cmd package resolve-activity --brief $pkg | tr -d '\\r' | tail -n 1); " +
                    "am start --user 0 -n \"${dl}ACT\" > /dev/null 2>&1"
                val launchProc = newShellProcess(arrayOf("sh", "-c", launchCmd))
                launchProc.waitFor()

                // 6. Auto-restore watcher when game process exits
                val needsRestore = (factor != 100 && factor > 0) || fpsCap > 0 || effectiveHz > 0
                if (needsRestore) {
                    val waitProc = newShellProcess(
                        arrayOf("sh", "-c", "sleep 8; while pidof $pkg > /dev/null 2>&1; do sleep 3; done")
                    )
                    waitProc.waitFor()

                    if (launchSessionId.get() == mySession) {
                        if (effectiveHz > 0 && hasVivoRms) {
                            releaseVivoRms()
                        }
                        val restoreSb = StringBuilder()
                        if (factor != 100 && factor > 0) {
                            restoreSb.append("wm size reset; wm density reset; ")
                        }
                        if (fpsCap > 0) {
                            if (lastFpsCapPkg == pkg) lastFpsCapPkg = ""
                            restoreSb.append("cmd game reset ").append(pkg)
                                .append(" >/dev/null 2>&1 || true; device_config delete game_overlay ")
                                .append(pkg).append(" >/dev/null 2>&1 || true; ")
                        }
                        if (effectiveHz > 0) {
                            restoreSb.append(buildRestoreRefreshSettingsCmd()).append("; ")
                        }
                        if (restoreSb.isNotEmpty()) {
                            val restoreProc = newShellProcess(arrayOf("sh", "-c", restoreSb.toString()))
                            restoreProc.waitFor()
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }
}