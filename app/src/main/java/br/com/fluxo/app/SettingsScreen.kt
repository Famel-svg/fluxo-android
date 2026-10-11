package br.com.fluxo.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.fluxo.core.SalaryPlanner
import br.com.fluxo.core.Transaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------------------------
// Shared building blocks for the settings cards (also used by BackupCard, ExportCsvCard,
// AccountsPanel and CaptureHealth).
// ---------------------------------------------------------------------------------------------

/** Quiet bordered container used by the secondary settings cards. */
@Composable
internal fun FluxoSettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .clip(FluxoShapes.Ticket)
            .background(FluxoColors.Paper)
            .border(1.5.dp, FluxoColors.Line, FluxoShapes.Ticket)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Pill button whose label may wrap (long Portuguese labels, large font scale). */
@Composable
internal fun FluxoWrapButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    quiet: Boolean = false,
) {
    val alpha = if (enabled) 1f else .38f
    val fg = (if (quiet) FluxoColors.Mute else FluxoColors.Ink).copy(alpha = alpha)
    val border = (if (quiet) FluxoColors.Line else FluxoColors.Ink).copy(alpha = alpha)
    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(FluxoColors.Paper)
            .border(if (quiet) 1.5.dp else 2.dp, border, RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

/**
 * Prototype "item" row of the Dados section: 14sp semibold title, optional caption, chevron.
 * [destructive] renders the discreet red variant without chevron.
 */
@Composable
internal fun FluxoDataRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val alpha = if (enabled) 1f else .38f
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title, modifier = Modifier.weight(1f),
                color = (if (destructive) FluxoColors.Expense else FluxoColors.Ink).copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (destructive) 13.sp else 14.sp,
                    fontWeight = if (destructive) FontWeight.Medium else FontWeight.SemiBold
                )
            )
            if (!destructive) Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = FluxoColors.Mute.copy(alpha = alpha), modifier = Modifier.size(20.dp)
            )
        }
        if (caption != null) Text(
            caption, color = FluxoColors.Mute,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 8.dp)
        )
        if (!destructive) HorizontalDivider(color = FluxoColors.Line)
    }
}

@Composable
private fun fluxoSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White, checkedTrackColor = FluxoColors.Income, checkedBorderColor = FluxoColors.Income,
    uncheckedThumbColor = FluxoColors.Ink, uncheckedTrackColor = FluxoColors.Paper, uncheckedBorderColor = FluxoColors.Ink,
)

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/** "hoje 08:12", "ontem 19:03" or "dd/MM HH:mm". */
private fun lastSeenLabel(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    return when (at.toLocalDate()) {
        today -> "hoje ${timeFormat.format(at)}"
        today.minusDays(1) -> "ontem ${timeFormat.format(at)}"
        else -> dayFormat.format(at)
    }
}

private val bankBadgeColors = listOf(
    FluxoColors.PetrolDark to Color.White,
    FluxoColors.Ink to Color.White,
    FluxoColors.PetrolLight to FluxoColors.Ink,
)

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    FluxoSectionHeading(title, trailing = hint, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp, bottom = 2.dp))
}

@Composable
private fun BankToggleRow(
    badge: String, badgeColors: Pair<Color, Color>, name: String, detail: String, checked: Boolean,
    switchDescription: String, onChange: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(badgeColors.first),
                contentAlignment = Alignment.Center
            ) {
                Text(badge, color = badgeColors.second, maxLines = 1, style = FluxoType.number(15.sp, FontWeight.ExtraBold))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    name, color = if (checked) FluxoColors.Ink else FluxoColors.Mute,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
                )
                Text(detail, color = FluxoColors.Mute, style = FluxoType.Caption.copy(fontSize = 11.5.sp, lineHeight = 14.sp))
            }
            Switch(
                checked = checked, onCheckedChange = onChange, colors = fluxoSwitchColors(),
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = switchDescription }
            )
        }
        HorizontalDivider(color = FluxoColors.Line)
    }
}

/** One line of the Estado block: check (ok) or warning (not ok) mark plus the state text. */
@Composable
private fun StateLine(ok: Boolean, solid: Boolean, text: String) {
    val fg = if (solid) Color.White else FluxoColors.Ink
    Row(
        Modifier.fillMaxWidth().heightIn(min = 25.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            if (ok) Icons.Default.Check else Icons.Default.Warning, contentDescription = null,
            tint = if (ok) fg else FluxoColors.Amber, modifier = Modifier.size(16.dp)
        )
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
internal fun SettingsScreen(capture: Boolean, allowOtherApps: Boolean, banks: Set<String>, access: Boolean, model: LedgerViewModel, transactions: List<Transaction>) {
    val context = LocalContext.current
    val accounts by model.accounts.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val editError by model.editError.collectAsStateWithLifecycle()
    val snapshot by CaptureHealth.state.collectAsStateWithLifecycle()
    val profileName by model.profileName.collectAsStateWithLifecycle()
    val salary by model.salaryMonthlyCents.collectAsStateWithLifecycle()
    var clear by rememberSaveable { mutableStateOf(false) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    var profileDraft by rememberSaveable { mutableStateOf("") }
    var salaryDraft by rememberSaveable { mutableStateOf("") }
    var settingsError by remember { mutableStateOf(false) }
    var reconnectMessage by remember { mutableStateOf<String?>(null) }
    // Keep document pickers registered even when their cards scroll off screen.
    val backupActions = rememberBackupActions()
    val allOk = access && capture && snapshot.connected == true
    val canReconnect = access && capture && snapshot.connected != true

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(FluxoShapes.Hero)
                        .background(if (allOk) FluxoColors.Income else FluxoColors.Soft)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "Estado", color = if (allOk) Color.White else FluxoColors.Ink,
                        style = FluxoType.number(20.sp, FontWeight.ExtraBold),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    StateLine(access, allOk, if (access) "Permissão concedida" else "Permissão não concedida")
                    StateLine(capture, allOk, if (capture) "Captura ativa" else "Captura pausada")
                    StateLine(
                        snapshot.connected == true, allOk, when (snapshot.connected) {
                            true -> "Serviço conectado"
                            false -> "Serviço desconectado"
                            null -> "Serviço ainda não confirmado nesta sessão"
                        }
                    )
                    if (!allOk) {
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!access) FluxoWrapButton("Abrir configurações do Android", onClick = {
                                try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                catch (_: Exception) { settingsError = true }
                            })
                            if (!capture) FluxoWrapButton("Ativar captura", onClick = { model.capture(true) })
                            if (canReconnect) FluxoWrapButton("Reconectar serviço", onClick = {
                                reconnectMessage = requestListenerRebind(context)
                            })
                        }
                        if (settingsError) Text(
                            "Abra Configurações do Android → Acesso especial → Acesso às notificações.",
                            color = FluxoColors.Expense, style = MaterialTheme.typography.bodySmall
                        )
                        reconnectMessage?.let { Text(it, color = FluxoColors.Ink, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Capturar notificações", color = FluxoColors.Ink,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold))
                        Text(
                            when {
                                !capture -> "Pausada por você"
                                !access -> "Aguardando permissão Android"
                                banks.isNotEmpty() || allowOtherApps -> "Ativa conforme autorizações abaixo"
                                else -> "Pausada sem origem permitida"
                            },
                            color = FluxoColors.Mute, style = FluxoType.Caption.copy(fontSize = 11.5.sp, lineHeight = 14.sp)
                        )
                    }
                    Switch(
                        checked = capture, onCheckedChange = model::capture, colors = fluxoSwitchColors(),
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = "Capturar notificações financeiras" }
                    )
                }
            }
            item { SectionTitle("Perfil") }
            item {
                Column(Modifier.fillMaxWidth()) {
                    FluxoDataRow(
                        "Nome", caption = profileName.ifBlank { "Não informado" },
                        onClick = { profileDraft = profileName; salaryDraft = centsFieldText(salary); model.resetEditError(); editingProfile = true },
                        modifier = Modifier.testTag("settings.profile")
                    )
                    FluxoDataRow(
                        "Salário mensal", caption = if (salary > 0) money(salary) else "Não informado",
                        onClick = { profileDraft = profileName; salaryDraft = centsFieldText(salary); model.resetEditError(); editingProfile = true },
                        modifier = Modifier.testTag("settings.salary")
                    )
                    FluxoDataRow(
                        "Rever configuração inicial", caption = "Nome, salário, VR e VT, dias de depósito.",
                        onClick = { model.reopenOnboarding() }, modifier = Modifier.testTag("settings.onboarding")
                    )
                }
            }
            item { SectionTitle("Bancos", "lidos pelas notificações") }
            item {
                Column(Modifier.fillMaxWidth()) {
                    supportedBanks.forEachIndexed { index, bank ->
                        key(bank.packageName) {
                            val enabled = bank.packageName in banks
                            val seen = snapshot.lastObserved[bank.packageName]
                            BankToggleRow(
                                badge = bank.name.take(1).uppercase(),
                                badgeColors = bankBadgeColors[index % bankBadgeColors.size],
                                name = bank.name,
                                detail = when {
                                    !enabled -> "Desligado"
                                    seen != null -> "Última notificação ${lastSeenLabel(seen)}"
                                    else -> "Nenhuma notificação nesta sessão"
                                },
                                checked = enabled,
                                switchDescription = "Permitir notificações de ${bank.name}",
                                onChange = { model.bank(bank.packageName, it) }
                            )
                        }
                    }
                    BankToggleRow(
                        badge = "+", badgeColors = FluxoColors.Soft to FluxoColors.Mute,
                        name = "Outros apps",
                        detail = if (allowOtherApps)
                            "Processa localmente eventos financeiros reconhecidos de qualquer outro app, inclusive bancos desmarcados acima. Não guarda texto bruto."
                        else "Desligado. Processa localmente eventos financeiros reconhecidos de qualquer outro app, inclusive bancos desmarcados acima. Não guarda texto bruto.",
                        checked = allowOtherApps,
                        switchDescription = "Permitir captura financeira de notificações de outros apps",
                        onChange = model::allowOtherApps
                    )
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 1.dp)
                        .clip(RoundedCornerShape(14.dp)).background(FluxoColors.Pale)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = FluxoColors.Ink, modifier = Modifier.size(22.dp))
                    Text(
                        "Tudo fica no aparelho. Texto das notificações não é guardado.", color = FluxoColors.Ink,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
            item { SectionTitle("Dados") }
            item { BackupCard(backupActions) }
            item { ExportCsvCard(transactions) }
            item {
                FluxoDataRow("Apagar histórico", onClick = { clear = true }, destructive = true)
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { CaptureHealthCard(access, capture, banks, allowOtherApps) }
            item { AccountsPanel(accounts, transactions, model, saving, editError) }
            item {
                FluxoSettingsCard {
                    FluxoSectionHeading("Privacidade e cobertura")
                    Text("O Android permite acesso às notificações. O Fluxo só processa bancos selecionados ou, com autorização separada, outros apps. Captura também precisa estar ativa. Texto completo não é guardado nem enviado à internet.",
                        color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
                    Text("Dados no armazenamento privado do aplicativo. Sem login, servidor ou backup automático. A proteção depende também do bloqueio e da segurança do aparelho.\n\nBancos podem omitir valores, mudar mensagens ou não notificar. Revise registros ambíguos. Pagamentos de fatura não devem virar novas compras. Totais não substituem o extrato bancário.",
                        color = FluxoColors.Mute, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    val salaryProblem = salaryError(salaryDraft)
    // Blank clears the salary; anything else must be a valid amount greater than zero.
    val salaryCents = if (salaryDraft.isBlank()) 0L else SalaryPlanner.parseMonthlyCents(salaryDraft)
    if (editingProfile) AlertDialog(
        onDismissRequest = { if (!saving) editingProfile = false },
        title = { Text("Seu perfil") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = profileDraft, onValueChange = { profileDraft = it.take(40) },
                    modifier = Modifier.fillMaxWidth().testTag("settings.profile.input"),
                    label = { Text("Como podemos te chamar?") }, singleLine = true, enabled = !saving,
                    supportingText = { Text("Opcional. Fica só neste aparelho.") }
                )
                OutlinedTextField(
                    value = salaryDraft, onValueChange = { salaryDraft = it.take(32) },
                    modifier = Modifier.fillMaxWidth().testTag("settings.salary.input"),
                    label = { Text("Salário líquido por mês") }, singleLine = true, enabled = !saving,
                    isError = salaryProblem != null, prefix = { Text("R$") },
                    supportingText = { Text(salaryProblem ?: "Opcional, como 3.500,00. Deixe vazio para remover. Fica só neste aparelho.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (editError != null) Text(editError.orEmpty(), color = FluxoColors.Expense, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && salaryCents != null, modifier = Modifier.heightIn(min = 48.dp).testTag("settings.profile.save"),
                onClick = { salaryCents?.let { model.saveProfile(profileDraft, it) { editingProfile = false } } }) { Text(if (saving) "Salvando…" else "Salvar") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = { editingProfile = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } }
    )
    if (clear) AlertDialog(
        onDismissRequest = { clear = false },
        title = { Text("Apagar todo o histórico?") },
        text = { Text("Esta ação apaga permanentemente todos os lançamentos, as contas, os limites de orçamento e os identificadores usados para evitar duplicatas. Não é possível desfazer. A captura será pausada. Após reativá-la, notificações futuras poderão gerar novos registros.") },
        confirmButton = { TextButton(onClick = { model.clear(); clear = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Apagar definitivamente", color = FluxoColors.Expense) } },
        dismissButton = { TextButton(onClick = { clear = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } }
    )
}
