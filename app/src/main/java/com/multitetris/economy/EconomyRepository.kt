package com.multitetris.economy

import android.content.Context
import com.multitetris.game.GameMode
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
 * Thread-safe repository managing the player's local coin wallet, unlocked skins,
 * and daily missions backed by Jetpack DataStore.
 */
class EconomyRepository(
    private val dataStore: LocalProfileDataStore,
    private val missionManager: DailyMissionManager = DailyMissionManager(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    constructor(context: Context) : this(LocalProfileDataStore(context))

    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _coinBalance = MutableStateFlow(EconomyUiState.DEFAULT_STARTING_COINS)
    val coinBalance: StateFlow<Int> = _coinBalance.asStateFlow()

    private val _unlockedSkins = MutableStateFlow(EconomyUiState.DEFAULT_UNLOCKED_SKINS)
    val unlockedSkins: StateFlow<Set<String>> = _unlockedSkins.asStateFlow()

    private val _dailyMissions = MutableStateFlow(DailyMissionManager.defaultMissions())
    val dailyMissions: StateFlow<List<DailyMission>> = _dailyMissions.asStateFlow()

    init {
        scope.launch {
            dataStore.economySnapshotFlow.collect { snapshot ->
                _coinBalance.value = snapshot.coinBalance
                _unlockedSkins.value = snapshot.unlockedSkins
                val decoded = decodeMissionsSnapshot(snapshot.dailyMissionsJson)
                val todaySnapshot = missionManager.ensureForToday(decoded)
                _dailyMissions.value = todaySnapshot.missions
                if (decoded == null || decoded.dateKey != todaySnapshot.dateKey) {
                    dataStore.saveDailyMissionsJson(json.encodeToString(todaySnapshot))
                }
            }
        }
    }

    suspend fun addCoins(amount: Int) {
        if (amount <= 0) return
        mutex.withLock {
            val updatedBalance = dataStore.addCoins(amount)
            _coinBalance.value = updatedBalance
        }
    }

    suspend fun purchaseSkin(skinId: String, price: Int): Boolean {
        if (price < 0) return false
        return mutex.withLock {
            val purchased = dataStore.purchaseSkin(skinId, price)
            if (purchased) {
                val snapshot = dataStore.getEconomySnapshot()
                _coinBalance.value = snapshot.coinBalance
                _unlockedSkins.value = snapshot.unlockedSkins
            }
            purchased
        }
    }

    suspend fun refreshDailyMissionsIfNeeded() {
        mutex.withLock {
            val snapshot = dataStore.getEconomySnapshot()
            val decoded = decodeMissionsSnapshot(snapshot.dailyMissionsJson)
            val todaySnapshot = missionManager.ensureForToday(decoded)
            _dailyMissions.value = todaySnapshot.missions
            if (decoded == null || decoded.dateKey != todaySnapshot.dateKey) {
                dataStore.saveDailyMissionsJson(json.encodeToString(todaySnapshot))
            }
        }
    }

    suspend fun recordLinesCleared(linesCleared: Int) {
        if (linesCleared <= 0) return
        mutex.withLock {
            val snapshot = dataStore.getEconomySnapshot()
            val current = decodeMissionsSnapshot(snapshot.dailyMissionsJson)
            val updated = missionManager.recordLinesCleared(current, linesCleared)
            _dailyMissions.value = updated.missions
            dataStore.saveDailyMissionsJson(json.encodeToString(updated))
        }
    }

    suspend fun recordMatchCompleted(
        mode: GameMode,
        isMultiplayer: Boolean,
        didLocalPlayerWin: Boolean
    ) {
        mutex.withLock {
            val snapshot = dataStore.getEconomySnapshot()
            val current = decodeMissionsSnapshot(snapshot.dailyMissionsJson)
            val updated = missionManager.recordMatchCompleted(
                existing = current,
                mode = mode,
                isMultiplayer = isMultiplayer,
                didLocalPlayerWin = didLocalPlayerWin
            )
            _dailyMissions.value = updated.missions
            dataStore.saveDailyMissionsJson(json.encodeToString(updated))
        }
    }

    suspend fun claimMissionReward(missionId: String): Int {
        return mutex.withLock {
            val snapshot = dataStore.getEconomySnapshot()
            val current = decodeMissionsSnapshot(snapshot.dailyMissionsJson)
            val (updated, reward) = missionManager.claimMission(current, missionId)
            if (reward > 0) {
                dataStore.saveDailyMissionsJson(json.encodeToString(updated))
                val updatedBalance = dataStore.addCoins(reward)
                _dailyMissions.value = updated.missions
                _coinBalance.value = updatedBalance
            }
            reward
        }
    }

    private fun decodeMissionsSnapshot(raw: String?): DailyMissionsSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<DailyMissionsSnapshot>(raw) }.getOrNull()
    }
}
