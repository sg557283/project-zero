package com.zero.assistant.core

data class BatteryAlertSettings(
    val lowEnabled: Boolean = true,
    val lowThreshold: Int = 20,
    val chargingStartedEnabled: Boolean = false,
    val fullyChargedEnabled: Boolean = false,
) {
    init {
        require(lowThreshold in 1..100) { "Low battery threshold must be between 1 and 100" }
    }
}

data class BatterySnapshot(val percent: Int, val charging: Boolean) {
    init {
        require(percent in 0..100) { "Battery percentage must be between 0 and 100" }
    }
}

sealed interface BatteryAlertEvent {
    data class Low(val percent: Int, val threshold: Int) : BatteryAlertEvent
    data object ChargingStarted : BatteryAlertEvent
    data object FullyCharged : BatteryAlertEvent
}

data class BatteryAlertState(
    val settings: BatteryAlertSettings = BatteryAlertSettings(),
    val lastPercent: Int? = null,
    val lastCharging: Boolean? = null,
    val lowArmed: Boolean = true,
    val lastFullAlertPercent: Int? = null,
)

interface BatteryAlertStorage {
    fun read(): BatteryAlertState
    fun write(state: BatteryAlertState)
}

class BatteryAlertEvaluator(private val storage: BatteryAlertStorage) {
    fun state(): BatteryAlertState = storage.read()

    @Synchronized
    fun updateSettings(settings: BatteryAlertSettings) {
        require(settings.lowThreshold in 1..100)
        storage.write(storage.read().copy(settings = settings))
    }

    @Synchronized
    fun onSnapshot(snapshot: BatterySnapshot): List<BatteryAlertEvent> {
        val state = storage.read()
        val events = mutableListOf<BatteryAlertEvent>()
        val wasAbove = state.lastPercent?.let { it > state.settings.lowThreshold } == true
        var armed = state.lowArmed
        if (snapshot.percent > state.settings.lowThreshold) armed = true
        if (state.settings.lowEnabled && armed && wasAbove && snapshot.percent <= state.settings.lowThreshold) {
            events += BatteryAlertEvent.Low(snapshot.percent, state.settings.lowThreshold)
            armed = false
        }
        if (state.settings.chargingStartedEnabled && state.lastCharging == false && snapshot.charging) {
            events += BatteryAlertEvent.ChargingStarted
        }
        var fullAlertPercent = state.lastFullAlertPercent
        if (state.settings.fullyChargedEnabled && snapshot.charging && snapshot.percent == 100 && state.lastPercent != 100 && fullAlertPercent != 100) {
            events += BatteryAlertEvent.FullyCharged
            fullAlertPercent = 100
        }
        if (snapshot.percent < 100) fullAlertPercent = null
        storage.write(
            state.copy(
                lastPercent = snapshot.percent,
                lastCharging = snapshot.charging,
                lowArmed = armed,
                lastFullAlertPercent = fullAlertPercent,
            ),
        )
        return events
    }
}