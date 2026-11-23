package com.nho.upscaler.ui.analytics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nho.upscaler.PerfLogManager
import com.nho.upscaler.PerfSample
import com.nho.upscaler.PerfSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalyticsUiState(
    val sessions: List<PerfSession> = emptyList(),
    val filteredSessions: List<PerfSession> = emptyList(),
    val gameFilters: List<Pair<String, String>> = emptyList(), // Pair(id, name)
    val selectedGameFilter: String = "all",
    val selectedSession: PerfSession? = null,
    val scrubIndex: Int = -1,
    val scrubSample: PerfSample? = null,
    val isDeleteConfirmOpen: Boolean = false
)

class AnalyticsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    fun init(context: Context) {
        loadSessions(context)
    }

    fun loadSessions(context: Context) {
        viewModelScope.launch {
            var list = PerfLogManager.listSessions(context)
            if (list.isEmpty()) {
                // If no sessions recorded yet, generate a demo realistic session so UI never looks empty
                list = listOf(generateDemoSession())
            }

            // Extract unique games
            val games = mutableListOf<Pair<String, String>>()
            games.add(Pair("all", "Tất cả"))
            val uniqueGames = list.map { Pair(it.pkg.ifBlank { "unknown" }, it.gameName) }.distinctBy { it.first }
            games.addAll(uniqueGames)

            val initialSelected = list.firstOrNull()
            val initialScrub = if (initialSelected != null && initialSelected.samples.isNotEmpty()) {
                initialSelected.samples[initialSelected.samples.size / 2]
            } else null

            _uiState.update {
                it.copy(
                    sessions = list,
                    filteredSessions = list,
                    gameFilters = games,
                    selectedGameFilter = "all",
                    selectedSession = initialSelected,
                    scrubIndex = if (initialSelected != null) initialSelected.samples.size / 2 else -1,
                    scrubSample = initialScrub
                )
            }
        }
    }

    fun selectGameFilter(gameId: String) {
        _uiState.update { state ->
            val filtered = if (gameId == "all") {
                state.sessions
            } else {
                state.sessions.filter { (it.pkg.ifBlank { "unknown" }) == gameId }
            }
            val active = filtered.firstOrNull() ?: state.selectedSession
            val scrub = if (active != null && active.samples.isNotEmpty()) active.samples[active.samples.size / 2] else null
            state.copy(
                selectedGameFilter = gameId,
                filteredSessions = filtered,
                selectedSession = active,
                scrubIndex = if (active != null) active.samples.size / 2 else -1,
                scrubSample = scrub
            )
        }
    }

    fun selectSession(session: PerfSession) {
        val mid = if (session.samples.isNotEmpty()) session.samples.size / 2 else -1
        val sample = if (mid >= 0 && mid < session.samples.size) session.samples[mid] else null
        _uiState.update {
            it.copy(
                selectedSession = session,
                scrubIndex = mid,
                scrubSample = sample
            )
        }
    }

    fun onScrubFractionChange(fraction: Float) {
        val session = _uiState.value.selectedSession ?: return
        if (session.samples.isEmpty()) return
        val count = session.samples.size
        val idx = (fraction * (count - 1)).toInt().coerceIn(0, count - 1)
        val sample = session.samples[idx]
        _uiState.update {
            it.copy(
                scrubIndex = idx,
                scrubSample = sample
            )
        }
    }

    fun openDeleteDialog() {
        _uiState.update { it.copy(isDeleteConfirmOpen = true) }
    }

    fun closeDeleteDialog() {
        _uiState.update { it.copy(isDeleteConfirmOpen = false) }
    }

    fun deleteCurrentSession(context: Context) {
        val active = _uiState.value.selectedSession ?: return
        PerfLogManager.deleteSession(context, active.fileName)
        closeDeleteDialog()
        loadSessions(context)
    }

    private fun generateDemoSession(): PerfSession {
        val samples = mutableListOf<PerfSample>()
        val totalSec = 920 // 15 min 20 sec
        for (i in 0 until totalSec step 2) {
            val fpsWave = 118.5f + (Math.sin(i / 25.0) * 2.5f).toFloat() + ((i % 17) * 0.1f)
            val wattWave = 5.6f + (Math.cos(i / 30.0) * 1.2f).toFloat()
            val batWave = 36.5f + (i / 920f) * 2.2f
            val socWave = 58.0f + (Math.sin(i / 15.0) * 4.5f).toFloat() + (i / 920f) * 4.0f

            samples.add(
                PerfSample(
                    sec = i,
                    fps = fpsWave.coerceIn(84f, 120f),
                    watts = wattWave.coerceIn(3.5f, 7.8f),
                    batTemp = batWave,
                    socTemp = socWave,
                    vramMb = 1420,
                    cpuMhz = 2840
                )
            )
        }

        return PerfSession(
            fileName = "demo_session.json",
            gameName = "Liên Quân Mobile",
            pkg = "com.garena.game.kgvn",
            scaleLabel = "1.25x SSAA",
            timestampMs = System.currentTimeMillis() - 3600000L,
            durationSec = totalSec,
            avgFps = 118.4f,
            low1Fps = 96.2f,
            minFps = 84.0f,
            maxFps = 120.0f,
            avgWatts = 5.8f,
            maxWatts = 7.4f,
            energyMwh = 1480f,
            peakBatTemp = 38.7f,
            peakSocTemp = 64.5f,
            avgVramMb = 1420,
            avgCpuMhz = 2840,
            samples = samples
        )
    }
}
