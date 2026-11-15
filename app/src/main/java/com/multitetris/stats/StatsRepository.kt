package com.multitetris.stats

import android.content.Context
import com.multitetris.network.social.LocalProfileDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Thread-safe repository managing persistent Career Totals, per-mode stats,
 * and Friend head-to-head rivalry stats backed by Jetpack DataStore.
 */
class StatsRepository(
    private val dataStore: LocalProfileDataStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    constructor(context: Context) : this(LocalProfileDataStore(context))

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _stats = MutableStateFlow(CareerStatsSnapshot())
    val stats: StateFlow<CareerStatsSnapshot> = _stats.asStateFlow()

    init {
        scope.launch {
            dataStore.careerStatsJsonFlow.collect { raw ->
                _stats.value = decodeSnapshot(raw)
            }
        }
    }

    /**
     * Records a completed match into career + friend rivalry stats and returns true
     * if this run set a new Co-op best score record.
     */
    suspend fun recordCompletedMatch(record: MatchStatRecord): Boolean {
        return mutex.withLock {
            val current = decodeSnapshot(dataStore.getCareerStatsJson())
            val (updated, isNewCoopRecord) = StatsReducer.recordMatch(current, record)
            _stats.value = updated
            dataStore.saveCareerStatsJson(json.encodeToString(updated))
            isNewCoopRecord
        }
    }

    private fun decodeSnapshot(raw: String?): CareerStatsSnapshot {
        if (raw.isNullOrBlank()) return CareerStatsSnapshot()
        return runCatching { json.decodeFromString<CareerStatsSnapshot>(raw) }
            .getOrDefault(CareerStatsSnapshot())
    }
}
