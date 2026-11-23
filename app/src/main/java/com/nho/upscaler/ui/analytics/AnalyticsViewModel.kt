package com.nho.upscaler.ui.analytics

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nho.upscaler.PerfLogManager
import com.nho.upscaler.PerfSample
import com.nho.upscaler.PerfSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AnalyticsUiState(
    val sessions: List<PerfSession> = emptyList(),
    val filteredSessions: List<PerfSession> = emptyList(),
    val gameFilters: List<Pair<String, String>> = emptyList(), // Pair(id, name)
    val selectedGameFilter: String = "all",
    val selectedSession: PerfSession? = null,
    val scrubIndex: Int = -1,
    val scrubSample: PerfSample? = null,
    val isDeleteConfirmOpen: Boolean = false,
    val exportedCsvPath: String? = null
)

class AnalyticsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    fun init(context: Context) {
        loadSessions(context)
    }

    fun loadSessions(context: Context) {
        viewModelScope.launch {
            val list = PerfLogManager.listSessions(context)

            if (list.isEmpty()) {
                _uiState.update {
                    it.copy(
                        sessions = emptyList(),
                        filteredSessions = emptyList(),
                        gameFilters = emptyList(),
                        selectedGameFilter = "all",
                        selectedSession = null,
                        scrubIndex = -1,
                        scrubSample = null
                    )
                }
                return@launch
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

    fun exportSessionCsv(context: Context) {
        val session = _uiState.value.selectedSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val safeName = session.gameName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val file = File(dir, "GameUpscaler_${safeName}_${session.timestampMs}.csv")
                file.bufferedWriter().use { writer ->
                    writer.write("Timestamp_s,FPS,Power_W,BatteryTemp_C,SocTemp_C,Vram_MB,Cpu_MHz\n")
                    for (s in session.samples) {
                        writer.write("${s.sec},${s.fps},${s.watts},${s.batTemp},${s.socTemp},${s.vramMb},${s.cpuMhz}\n")
                    }
                }
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(exportedCsvPath = file.absolutePath) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Lỗi xuất file CSV: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(exportedCsvPath = null) }
    }
}
