package br.com.fluxo.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fluxo.core.AlertKind

private fun AlertKind.label() = when (this) {
    AlertKind.REVIEW_DIGEST -> "Resumo de revisão"
    AlertKind.BUDGET_80 -> "Orçamento 80%"
    AlertKind.BUDGET_OVER -> "Orçamento estourado"
    AlertKind.BILL_DUE -> "Conta vencendo"
    AlertKind.UNUSUAL -> "Gasto incomum"
    AlertKind.CAPTURE_DOWN -> "Captura parada"
    AlertKind.WEEKLY_SUMMARY -> "Resumo semanal"
}

/** Body of the "Notificações inteligentes" settings section. */
@Composable
internal fun SmartAlertsCard(model: LedgerViewModel) {
    val context = LocalContext.current
    val enabled by model.alertsEnabled.collectAsStateWithLifecycle()
    val (quietStart, quietEnd) = model.alertQuietHours.collectAsStateWithLifecycle().value
    var allowed by remember { mutableStateOf(SmartNotifier.canPost(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    // Back from system settings the permission or channel may have changed.
    LifecycleResumeEffect(Unit) {
        allowed = SmartNotifier.canPost(context)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = SmartNotifier.canPost(context)
    }
    FluxoSettingsCard {
        if (!allowed) {
            Text("Sem permissão, o Fluxo não consegue avisar.", color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
            FluxoWrapButton("Permitir notificações", onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !asked) {
                    asked = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    try {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                    }
                }
            }, modifier = Modifier.fillMaxWidth())
        }
        AlertKind.entries.forEach { kind ->
            val on = kind.name in enabled
            FluxoDataRow(kind.label(), onClick = { model.setAlertsEnabled(if (on) enabled - kind.name else enabled + kind.name) },
                trailing = {
                    Switch(checked = on, onCheckedChange = { model.setAlertsEnabled(if (it) enabled + kind.name else enabled - kind.name) },
                        colors = fluxoSwitchColors(),
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Aviso: ${kind.label()}" })
                })
        }
        Text("Horário silencioso", color = FluxoColors.Mute, style = MaterialTheme.typography.labelMedium)
        HourStepper("Começa às", quietStart) { model.setAlertQuietHours(it, quietEnd) }
        HourStepper("Termina às", quietEnd) { model.setAlertQuietHours(quietStart, it) }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), color = FluxoColors.Ink, style = MaterialTheme.typography.bodyMedium)
        FluxoWrapButton("−", onClick = { onChange((hour + 23) % 24) }, quiet = true,
            modifier = Modifier.semantics { contentDescription = "$label: uma hora a menos" })
        Text("%02dh".format(hour), color = FluxoColors.Ink, style = MaterialTheme.typography.titleSmall)
        FluxoWrapButton("+", onClick = { onChange((hour + 1) % 24) }, quiet = true,
            modifier = Modifier.semantics { contentDescription = "$label: uma hora a mais" })
    }
}
