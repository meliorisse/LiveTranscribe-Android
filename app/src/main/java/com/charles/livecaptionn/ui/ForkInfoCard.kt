package com.charles.livecaptionn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.charles.livecaptionn.BuildConfig
import com.charles.livecaptionn.ui.l10n.LocalUiStrings
import com.charles.livecaptionn.update.UpdateCheckStatus
import com.charles.livecaptionn.update.UpdateInfo

@Composable
fun ForkInfoCard(
    status: UpdateCheckStatus,
    availableUpdate: UpdateInfo?,
    onCheck: () -> Unit,
    onAddQuickTile: () -> Unit,
    onDownload: (UpdateInfo) -> Unit
) {
    val t = LocalUiStrings.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(t["About this fork"], style = MaterialTheme.typography.titleMedium)
            Text(t["This is an independent fork of LiveCaptionN maintained by meliorisse. No support is offered for this version."])
            Text(t.format("Version %s", BuildConfig.VERSION_NAME))
            Text(t["Start floating translation from any app using the Quick Settings tile. Tap it again to stop."])
            OutlinedButton(onClick = onAddQuickTile) {
                Text(t["Add quick action"])
            }
            if (BuildConfig.GITHUB_SELF_UPDATE_ENABLED) {
                Text(t["Updates come only from meliorisse/LiveTranscribe-Android releases on GitHub."])
                val message = when (status) {
                    UpdateCheckStatus.IDLE -> null
                    UpdateCheckStatus.CHECKING -> "Checking for updates…"
                    UpdateCheckStatus.UP_TO_DATE -> "You have the latest version."
                    UpdateCheckStatus.AVAILABLE -> "Update available"
                    UpdateCheckStatus.NO_RELEASE -> "No published releases found."
                    UpdateCheckStatus.FAILED -> "Could not check for updates. Check your connection and try again."
                }
                message?.let { Text(t[it]) }
                OutlinedButton(onClick = onCheck, enabled = status != UpdateCheckStatus.CHECKING) {
                    Text(t["Check for updates"])
                }
                availableUpdate?.let { info ->
                    TextButton(onClick = { onDownload(info) }) {
                        Text(t.format("Download %s", info.tagName))
                    }
                }
            }
        }
    }
}
