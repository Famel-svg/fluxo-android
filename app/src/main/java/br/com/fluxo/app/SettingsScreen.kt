package br.com.fluxo.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fluxo.core.Transaction

@Composable
internal fun SettingsScreen(capture: Boolean, allowOtherApps: Boolean, banks: Set<String>, access: Boolean, model: LedgerViewModel, transactions: List<Transaction>) {
    val context = LocalContext.current
    val accounts by model.accounts.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val editError by model.editError.collectAsStateWithLifecycle()
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    var clear by rememberSaveable { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf(false) }
    MaterialTheme(shapes = MaterialTheme.shapes.copy(medium = RoundedCornerShape(20.dp))) {
        // Keep document pickers registered even when their cards scroll off screen.
        val backupActions = rememberBackupActions()
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Ajustes", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() })
                }
                item {
                    NeonPanel(accent = NeonMint, background = listOf(Color(0xFF004C42), Color(0xFF002337), Color(0xFF001020))) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF00614F)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Notifications, null, tint = NeonMint, modifier = Modifier.size(28.dp))
                            }
                            Text("Acesso às notificações", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f).semantics { heading() })
                        }
                        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF062A3C), border = BorderStroke(1.dp, NeonBlue)) {
                            Text(if (access) "Acesso às notificações permitido" else "Acesso às notificações necessário",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = if (access) NeonMint else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        Text(if (access) "Acesso concedido pelo Android. A leitura depende também da captura e das autorizações abaixo."
                            else "Para ler suas notificações financeiras, conceda o acesso nas configurações do Android.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { settingsError = true } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, NeonBlue),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Icon(Icons.Default.Settings, null, tint = NeonBlue)
                            Spacer(Modifier.width(12.dp))
                            Text("Abrir configurações Android", modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = NeonBlue)
                        }
                        if (settingsError) Text("Abra Configurações do Android → Acesso especial → Acesso às notificações.", color = MaterialTheme.colorScheme.error)
                    }
                }
                item {
                    NeonPanel {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!largeFont) Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF123348)), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = NeonBlue)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Capturar notificações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(when {
                                    !capture -> "Pausada por você"
                                    !access -> "Aguardando permissão Android"
                                    banks.isNotEmpty() || allowOtherApps -> "Ativa conforme autorizações abaixo"
                                    else -> "Pausada sem origem permitida"
                                }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(checked = capture, onCheckedChange = model::capture,
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Capturar notificações financeiras" })
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!largeFont) Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF00463E)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Shield, null, tint = NeonMint)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Permitir outros apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Processa localmente eventos financeiros reconhecidos de qualquer outro app, inclusive bancos desmarcados abaixo. Não guarda texto bruto.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(checked = allowOtherApps, onCheckedChange = model::allowOtherApps,
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Permitir captura financeira de notificações de outros apps" })
                        }
                    }
                }
                item {
                    NeonPanel {
                        NeonHeading("Bancos permitidos", Icons.Default.Apps)
                        Text("Lista de bancos compatíveis. Sem a autorização para outros apps, somente os bancos selecionados são processados.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        supportedBanks.forEachIndexed { index, bank ->
                            key(bank.packageName) {
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!largeFont) Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF123348)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AccountBalance, null, tint = NeonBlue)
                                }
                                Text(bank.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Switch(checked = bank.packageName in banks, onCheckedChange = { model.bank(bank.packageName, it) },
                                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = "Permitir notificações de ${bank.name}" })
                            }
                            }
                        }
                    }
                }
                item { CaptureHealthCard(access, capture, banks, allowOtherApps) }
                item { AccountsPanel(accounts, transactions, model, saving, editError) }
                item {
                    NeonPanel(accent = NeonMint, background = listOf(Color(0xFF003B32), Color(0xFF001020))) {
                        NeonHeading("Armazenamento local", Icons.Default.Lock)
                        Text("Seus dados ficam no armazenamento privado deste aparelho. Você controla a exportação, o backup e a exclusão.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { ExportCsvCard(transactions) }
                item { BackupCard(backupActions) }
                item {
                    NeonPanel {
                        NeonHeading("Privacidade e cobertura", Icons.Default.Shield)
                        Text("O Android permite acesso às notificações. O Fluxo só processa bancos selecionados ou, com autorização separada, outros apps. Captura também precisa estar ativa. Texto completo não é guardado nem enviado à internet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Dados no armazenamento privado do aplicativo. Sem login, servidor ou backup automático. A proteção depende também do bloqueio e da segurança do aparelho.\n\nBancos podem omitir valores, mudar mensagens ou não notificar. Revise registros ambíguos. Pagamentos de fatura não devem virar novas compras. Totais não substituem o extrato bancário.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    NeonPanel(accent = MaterialTheme.colorScheme.error, background = listOf(Color(0xFF2C1926), Color(0xFF001020))) {
                        NeonHeading("Apagar dados locais", Icons.Default.DeleteOutline, MaterialTheme.colorScheme.error)
                        Text("A exclusão exige confirmação e não pode ser desfeita.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { clear = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text("Apagar todos os lançamentos")
                        }
                    }
                }
            }
        }
        if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Apagar todo o histórico?") }, text = { Text("Esta ação apaga permanentemente todos os lançamentos, as contas, os limites de orçamento e os identificadores usados para evitar duplicatas. Não é possível desfazer. A captura será pausada. Após reativá-la, notificações futuras poderão gerar novos registros.") }, confirmButton = { TextButton(onClick = { model.clear(); clear = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Apagar definitivamente") } }, dismissButton = { TextButton(onClick = { clear = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } })
    }
}
