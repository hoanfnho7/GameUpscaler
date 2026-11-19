package com.nho.upscaler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PerfSample(
    val sec: Int,
    val fps: Float,
    val watts: Float,
    val batTemp: Float,
    val socTemp: Float,
    val vramMb: Int,
    val cpuMhz: Int
)

data class PerfSession(
    val fileName: String,
    val gameName: String,
    val pkg: String,
    val scaleLabel: String,
    val timestampMs: Long,
    val durationSec: Int,
    val avgFps: Float,
    val low1Fps: Float,
    val minFps: Float,
    val maxFps: Float,
    val avgWatts: Float,
    val maxWatts: Float,
    val energyMwh: Float,
    val peakBatTemp: Float,
    val peakSocTemp: Float,
    val avgVramMb: Int,
    val avgCpuMhz: Int,
    val samples: List<PerfSample>
)

object PerfLogManager {
    private const val MAX_SAMPLES = 7200
    private val lock = Any()

    @Volatile
    var isRecording = false
        private set

    private var activePkg = ""
    private var activeGameName = "Game"
    private var activeScale = "Native"
    private var startTimeMs = 0L
    private val buffer = ArrayList<PerfSample>(600)

    fun startSession(pkg: String, gameName: String, scaleLabel: String) {
        synchronized(lock) {
            buffer.clear()
            activePkg = pkg
            activeGameName = gameName.ifBlank { pkg.ifBlank { "Screen Session" } }
            activeScale = scaleLabel.ifBlank { "Native" }
            startTimeMs = System.currentTimeMillis()
            isRecording = true
        }
    }

    fun addSample(
        fps: Float,
        watts: Float,
        batTemp: Float,
        socTemp: Float,
        vramMb: Int,
        cpuKhz: Long
    ) {
        if (!isRecording) return
        synchronized(lock) {
            if (!isRecording || buffer.size >= MAX_SAMPLES) return
            val elapsedSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(buffer.size)
            buffer.add(
                PerfSample(
                    sec = elapsedSec,
                    fps = if (fps > 0f) fps else 0f,
                    watts = if (watts > 0f) watts else 0f,
                    batTemp = if (batTemp > 0f) batTemp else 0f,
                    socTemp = if (socTemp > 0f) socTemp else 0f,
                    vramMb = if (vramMb >= 0) vramMb else 0,
                    cpuMhz = if (cpuKhz > 0L) (cpuKhz / 1000L).toInt() else 0
                )
            )
        }
    }

    fun stopAndSave(context: Context): PerfSession? {
        val snapshot: List<PerfSample>
        val pkg: String
        val gName: String
        val scale: String
        val started: Long

        synchronized(lock) {
            if (!isRecording) return null
            isRecording = false
            snapshot = ArrayList(buffer)
            buffer.clear()
            pkg = activePkg
            gName = activeGameName
            scale = activeScale
            started = startTimeMs
        }

        if (snapshot.size < 3) return null

        val validFps = snapshot.map { it.fps }.filter { it > 1f }.sorted()
        val avgFps = if (validFps.isNotEmpty()) validFps.average().toFloat() else 0f
        val minFps = validFps.firstOrNull() ?: 0f
        val maxFps = validFps.lastOrNull() ?: 0f
        val low1Count = (validFps.size / 100).coerceAtLeast(1)
        val low1Fps = if (validFps.isNotEmpty()) validFps.take(low1Count).average().toFloat() else 0f

        val validWatts = snapshot.map { it.watts }.filter { it > 0.05f }
        val avgWatts = if (validWatts.isNotEmpty()) validWatts.average().toFloat() else 0f
        val maxWatts = validWatts.maxOrNull() ?: 0f

        val durationSec = (snapshot.last().sec - snapshot.first().sec).coerceAtLeast(snapshot.size)
        val energyMwh = avgWatts * (durationSec / 3600f) * 1000f

        val peakBat = snapshot.maxOfOrNull { it.batTemp } ?: 0f
        val peakSoc = snapshot.maxOfOrNull { it.socTemp } ?: 0f
        val validVram = snapshot.map { it.vramMb }.filter { it > 0 }
        val avgVram = if (validVram.isNotEmpty()) validVram.average().toInt() else 0
        val validCpu = snapshot.map { it.cpuMhz }.filter { it > 0 }
        val avgCpu = if (validCpu.isNotEmpty()) validCpu.average().toInt() else 0

        val dir = File(context.filesDir, "perf_logs").apply { if (!exists()) mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(started))
        val fileName = "session_${stamp}.json"

        val root = JSONObject().apply {
            put("gameName", gName)
            put("pkg", pkg)
            put("scaleLabel", scale)
            put("timestampMs", started)
            put("durationSec", durationSec)
            put("avgFps", avgFps.toDouble())
            put("low1Fps", low1Fps.toDouble())
            put("minFps", minFps.toDouble())
            put("maxFps", maxFps.toDouble())
            put("avgWatts", avgWatts.toDouble())
            put("maxWatts", maxWatts.toDouble())
            put("energyMwh", energyMwh.toDouble())
            put("peakBatTemp", peakBat.toDouble())
            put("peakSocTemp", peakSoc.toDouble())
            put("avgVramMb", avgVram)
            put("avgCpuMhz", avgCpu)

            val arr = JSONArray()
            for (s in snapshot) {
                arr.put(
                    JSONArray().apply {
                        put(s.sec)
                        put(String.format(Locale.US, "%.1f", s.fps).toDouble())
                        put(String.format(Locale.US, "%.2f", s.watts).toDouble())
                        put(String.format(Locale.US, "%.1f", s.batTemp).toDouble())
                        put(String.format(Locale.US, "%.1f", s.socTemp).toDouble())
                        put(s.vramMb)
                        put(s.cpuMhz)
                    }
                )
            }
            put("samples", arr)
        }

        try {
            File(dir, fileName).writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}

        return PerfSession(
            fileName, gName, pkg, scale, started, durationSec,
            avgFps, low1Fps, minFps, maxFps, avgWatts, maxWatts,
            energyMwh, peakBat, peakSoc, avgVram, avgCpu, snapshot
        )
    }

    fun listSessions(context: Context): List<PerfSession> {
        val dir = File(context.filesDir, "perf_logs")
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.mapNotNull { parseFile(it) }
    }

    fun deleteSession(context: Context, fileName: String) {
        try {
            File(File(context.filesDir, "perf_logs"), fileName).delete()
        } catch (_: Exception) {}
    }

    private fun parseFile(file: File): PerfSession? {
        return try {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val arr = obj.optJSONArray("samples") ?: JSONArray()
            val list = ArrayList<PerfSample>(arr.length())
            for (i in 0 until arr.length()) {
                val row = arr.optJSONArray(i) ?: continue
                list.add(
                    PerfSample(
                        sec = row.optInt(0, i),
                        fps = row.optDouble(1, 0.0).toFloat(),
                        watts = row.optDouble(2, 0.0).toFloat(),
                        batTemp = row.optDouble(3, 0.0).toFloat(),
                        socTemp = row.optDouble(4, 0.0).toFloat(),
                        vramMb = row.optInt(5, 0),
                        cpuMhz = row.optInt(6, 0)
                    )
                )
            }
            PerfSession(
                fileName = file.name,
                gameName = obj.optString("gameName", "Game"),
                pkg = obj.optString("pkg", ""),
                scaleLabel = obj.optString("scaleLabel", "Native"),
                timestampMs = obj.optLong("timestampMs", file.lastModified()),
                durationSec = obj.optInt("durationSec", list.size),
                avgFps = obj.optDouble("avgFps", 0.0).toFloat(),
                low1Fps = obj.optDouble("low1Fps", 0.0).toFloat(),
                minFps = obj.optDouble("minFps", 0.0).toFloat(),
                maxFps = obj.optDouble("maxFps", 0.0).toFloat(),
                avgWatts = obj.optDouble("avgWatts", 0.0).toFloat(),
                maxWatts = obj.optDouble("maxWatts", 0.0).toFloat(),
                energyMwh = obj.optDouble("energyMwh", 0.0).toFloat(),
                peakBatTemp = obj.optDouble("peakBatTemp", 0.0).toFloat(),
                peakSocTemp = obj.optDouble("peakSocTemp", 0.0).toFloat(),
                avgVramMb = obj.optInt("avgVramMb", 0),
                avgCpuMhz = obj.optInt("avgCpuMhz", 0),
                samples = list
            )
        } catch (_: Exception) {
            null
        }
    }
}