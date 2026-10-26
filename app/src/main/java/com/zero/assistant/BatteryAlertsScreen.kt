package com.zero.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zero.assistant.core.BatteryAlertSettings
import com.zero.assistant.data.BatteryStatus

@Composable
internal fun BatteryAlertsScreen(
    modifier: Modifier,
    status: BatteryStatus,
    settings: BatteryAlertSettings,
    notificationsGranted: Boolean,
    onSettingsChanged: (BatteryAlertSettings) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    var thresholdText by remember(settings.lowThreshold) { mutableStateOf(settings.lowThreshold.toString()) }
    var sliderThreshold by remember(settings.lowThreshold) { mutableFloatStateOf(settings.lowThreshold.toFloat()) }
    var validation by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Battery Alerts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${status.percent}% · ${if (status.charging) "Charging" else "On battery"}", style = MaterialTheme.typography.headlineSmall)
        SwitchRow("Low battery alert", settings.lowEnabled) { onSettingsChanged(settings.copy(lowEnabled = it)) }
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Threshold", modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { value ->
                        if (value.length <= 3 && value.all(Char::isDigit)) {
                            thresholdText = value
                            val parsed = value.toIntOrNull()
                            if (parsed != null && parsed in 1..100) {
                                validation = null
                                sliderThreshold = parsed.toFloat()
                                onSettingsChanged(settings.copy(lowThreshold = parsed!!))
                            } else validation = "Choose a threshold from 1 to 100."
                        }
                    },
                    modifier = Modifier.weight(0.45f),
                    singleLine = true,
                    label = { Text("Percent") },
                )
            }
            Slider(
                value = sliderThreshold,
                onValueChange = { value ->
                    val selected = value.toInt().coerceIn(1, 100)
                    sliderThreshold = selected.toFloat()
                    thresholdText = selected.toString()
                    validation = null
                },
                onValueChangeFinished = { onSettingsChanged(settings.copy(lowThreshold = sliderThreshold.toInt())) },
                valueRange = 1f..100f,
            )
            validation?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        SwitchRow("Charging started", settings.chargingStartedEnabled) { onSettingsChanged(settings.copy(chargingStartedEnabled = it)) }
        SwitchRow("Battery full", settings.fullyChargedEnabled) { onSettingsChanged(settings.copy(fullyChargedEnabled = it)) }
        if (!notificationsGranted) {
            Text("Android notification permission is required for alerts. Android may defer background checks; force-stopping ZERO prevents delivery until you open it again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRequestNotifications) { Text("Enable notifications") }
        } else {
            Text("Background battery checks run periodically while any alert is enabled. Alerts fire once per crossing and re-arm after the battery rises above the threshold.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
