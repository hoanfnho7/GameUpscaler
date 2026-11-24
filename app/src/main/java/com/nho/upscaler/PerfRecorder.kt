package com.nho.upscaler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

data class PerfSample(
    val sec: Int,
    val fps: Float,
    val watts: Float,
    val batTemp: Float,
    val socTemp: Float,
    val vramMb: Int,
    val cpuMhz: Int
) {
    val frameTimeMs: Float
        get() = if (fps > 1f) (1000f / fps).coerceIn(4f, 500f) else 0f
}

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
    val samples: List<PerfSample>,
    val avgFrameTimeMs: Float = 0f,
    val frameTimeJitterMs: Float = 0f,
    val stabilityPct: Float = 0f,
    val stutterCount: Int = 0
)

object PerfLogManager {
    private const val MAX_SAMPLES = 7200
    private val lock = Any()

    @Volatile
    var isRecording = false
        private set

    fun getElapsedSec(): Int {
        if (!isRecording || startTimeMs <= 0L) return 0
        return ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(0)
    }

    private var activePkg = ""
    private var activeGameName = "Game"
    private var activeScale = "Native"
    private var startTimeMs = 0L
    private var sessionJankCount = 0
    private val buffer = ArrayList<PerfSample>(600)

    fun startSession(pkg: String, gameName: String, scaleLabel: String) {
        synchronized(lock) {
            buffer.clear()
            activePkg = pkg
            activeGameName = gameName.ifBlank { pkg.ifBlank { "Screen Session" } }
            activeScale = scaleLabel.ifBlank { "Native" }
            startTimeMs = System.currentTimeMillis()
            sessionJankCount = 0
            isRecording = true
        }
    }

    fun addJankDelta(delta: Int) {
        if (!isRecording || delta <= 0) return
        synchronized(lock) {
            if (isRecording) sessionJankCount += delta
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
        val realJanks: Int

        synchronized(lock) {
            if (!isRecording) return null
            isRecording = false
            snapshot = ArrayList(buffer)
            buffer.clear()
            pkg = activePkg
            gName = activeGameName
            scale = activeScale
            started = startTimeMs
            realJanks = sessionJankCount
            sessionJankCount = 0
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

        val (avgFt, jitterMs, stabilityPct, estimatedStutters) = computeSmoothnessMetrics(snapshot, avgFps)
        val stutters = if (realJanks > 0) realJanks else estimatedStutters

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
            put("avgFrameTimeMs", avgFt.toDouble())
            put("frameTimeJitterMs", jitterMs.toDouble())
            put("stabilityPct", stabilityPct.toDouble())
            put("stutterCount", stutters)

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
            energyMwh, peakBat, peakSoc, avgVram, avgCpu, snapshot,
            avgFt, jitterMs, stabilityPct, stutters
        )
    }

    private data class SmoothnessMetrics(
        val avgFrameTimeMs: Float,
        val jitterMs: Float,
        val stabilityPct: Float,
        val stutterCount: Int
    )

    private fun computeSmoothnessMetrics(samples: List<PerfSample>, avgFps: Float): SmoothnessMetrics {
        val validFps = samples.map { it.fps }.filter { it > 1f }
        if (validFps.isEmpty() || avgFps <= 1f) {
            return SmoothnessMetrics(0f, 0f, 0f, 0)
        }
        val frameTimes = validFps.map { (1000f / it).coerceIn(4f, 500f) }
        val avgFt = frameTimes.average().toFloat()

        // Frame-time pacing jitter: mean absolute difference between consecutive samples
        var diffSum = 0.0
        for (i in 1 until frameTimes.size) {
            diffSum += abs(frameTimes[i] - frameTimes[i - 1])
        }
        val jitterMs = if (frameTimes.size > 1) (diffSum / (frameTimes.size - 1)).toFloat() else 0f

        // Stability score (%): 100 - Coefficient of Variation of FPS (inspired by Scene FAS variance)
        var sqSum = 0.0
        for (f in validFps) {
            val d = f - avgFps
            sqSum += d * d
        }
        val stdDev = sqrt(sqSum / validFps.size).toFloat()
        val stabilityPct = ((1f - (stdDev / avgFps)) * 100f).coerceIn(0f, 100f)

        // Stutter count: severe frame drops (>20% below avg FPS or >45% frame-time spike)
        var stutters = 0
        for (i in validFps.indices) {
            if (validFps[i] < avgFps * 0.80f || frameTimes[i] > avgFt * 1.45f) {
                stutters++
            }
        }
        return SmoothnessMetrics(avgFt, jitterMs, stabilityPct, stutters)
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
            val loadedAvgFps = obj.optDouble("avgFps", 0.0).toFloat()
            val computed = computeSmoothnessMetrics(list, loadedAvgFps)
            val loadedAvgFt = if (obj.has("avgFrameTimeMs")) obj.optDouble("avgFrameTimeMs", computed.avgFrameTimeMs.toDouble()).toFloat() else computed.avgFrameTimeMs
            val loadedJitter = if (obj.has("frameTimeJitterMs")) obj.optDouble("frameTimeJitterMs", computed.jitterMs.toDouble()).toFloat() else computed.jitterMs
            val loadedStability = if (obj.has("stabilityPct")) obj.optDouble("stabilityPct", computed.stabilityPct.toDouble()).toFloat() else computed.stabilityPct
            val loadedStutters = if (obj.has("stutterCount")) obj.optInt("stutterCount", computed.stutterCount) else computed.stutterCount

            PerfSession(
                fileName = file.name,
                gameName = obj.optString("gameName", "Game"),
                pkg = obj.optString("pkg", ""),
                scaleLabel = obj.optString("scaleLabel", "Native"),
                timestampMs = obj.optLong("timestampMs", file.lastModified()),
                durationSec = obj.optInt("durationSec", list.size),
                avgFps = loadedAvgFps,
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
                samples = list,
                avgFrameTimeMs = loadedAvgFt,
                frameTimeJitterMs = loadedJitter,
                stabilityPct = loadedStability,
                stutterCount = loadedStutters
            )
        } catch (_: Exception) {
            null
        }
    }
}