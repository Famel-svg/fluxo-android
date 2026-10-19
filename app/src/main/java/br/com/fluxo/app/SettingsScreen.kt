package br.com.fluxo.app

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import br.com.fluxo.core.ListenerState
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
    val border = (if (quiet) FluxoColors.Line else FluxoColors.ChipBorder).copy(alpha = alpha)
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
    checkedThumbColor = FluxoColors.Thumb, checkedTrackColor = FluxoColors.Income, checkedBorderColor = FluxoColors.Income,
    uncheckedThumbColor = FluxoColors.Ink, uncheckedTrackColor = FluxoColors.Paper, uncheckedBorderColor = FluxoColors.ChipBorder,
)

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm")

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    FluxoSectionHeading(title, trailing = hint, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp, bottom = 2.dp))
}

/** One line of the Estado block: check (ok) or warning (not ok) mark plus the state text. */
@Composable
private fun StateLine(ok: Boolean, solid: Boolean, text: String) {
    val fg = if (solid) FluxoColors.OnIncome else FluxoColors.Ink
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
internal fun SettingsScreen(capture: Boolean, access: Boolean, model: LedgerViewModel, transactions: List<Transaction>) {
    val context = LocalContext.current
    val accounts by model.accounts.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val editError by model.editError.collectAsStateWithLifecycle()
    val snapshot by CaptureHealth.state.collectAsStateWithLifecycle()
    val profileName by model.profileName.collectAsStateWithLifecycle()
    val salary by model.salaryMonthlyCents.collectAsStateWithLifecycle()
    val themeMode by model.themeMode.collectAsStateWithLifecycle()
    val triageOn by model.triageEnabled.collectAsStateWithLifecycle()
    val triageRows by model.triage.collectAsStateWithLifecycle()
    // Text stays on the device until the owner clears the triage, even after switching it off.
    val triage = triageOn || triageRows.isNotEmpty()
    var clear by rememberSaveable { mutableStateOf(false) }
    var editingProfile by rememberSaveable { mutableStateOf(false) }
    var profileDraft by rememberSaveable { mutableStateOf("") }
    var salaryDraft by rememberSaveable { mutableStateOf("") }
    var settingsError by remember { mutableStateOf(false) }
    // Keep document pickers registered even when their cards scroll off screen.
    val backupActions = rememberBackupActions()
    val listenerState = snapshot.listenerState(access)
    val allOk = access && capture && listenerState == ListenerState.CONNECTED
    val canReconnect = access && capture && listenerState != ListenerState.CONNECTED

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
                        "Estado", color = if (allOk) FluxoColors.OnIncome else FluxoColors.Ink,
                        style = FluxoType.number(20.sp, FontWeight.ExtraBold),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    StateLine(access, allOk, if (access) "Permissão concedida" else "Permissão não concedida")
                    StateLine(capture, allOk, if (capture) "Captura ativa" else "Captura pausada")
                    StateLine(listenerState == ListenerState.CONNECTED, allOk, serviceStateText(listenerState))
                    if (!allOk) {
                        Spacer(Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!access) FluxoWrapButton("Abrir configurações do Android", onClick = {
                                settingsError = !CaptureConnection.open(context, SettingsTarget.NOTIFICATION_ACCESS)
                            })
                            if (!capture) FluxoWrapButton("Ativar captura", onClick = { model.capture(true) })
                            if (canReconnect) {
                                val busy = listenerState == ListenerState.CONNECTING
                                FluxoWrapButton(if (busy) "Conectando…" else "Reconectar serviço", enabled = !busy,
                                    onClick = { CaptureConnection.launchReconnect(context) })
                            }
                        }
                        if (settingsError) Text(
                            "Abra Configurações do Android → Acesso especial → Acesso às notificações.",
                            color = FluxoColors.Expense, style = MaterialTheme.typography.bodySmall
                        )
                        if (!snapshot.reconnecting) snapshot.lastResult?.let {
                            Text(it.message, color = FluxoColors.Ink, style = MaterialTheme.typography.bodySmall)
                        }
                        if (access && capture && listenerState == ListenerState.STUCK) ListenerRecoveryGuide()
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
                                else -> "Ativa para todos os apps"
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
            item { SectionTitle("Aparência") }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp).selectableGroup().semantics { contentDescription = "Aparência do aplicativo" },
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Triple(ThemeMode.SYSTEM, "Sistema", "settings.theme.system"),
                        Triple(ThemeMode.LIGHT, "Claro", "settings.theme.light"),
                        Triple(ThemeMode.DARK, "Escuro", "settings.theme.dark"),
                    ).forEach { (mode, label, tag) ->
                        FluxoChip(label, selected = themeMode == mode, onClick = { model.setThemeMode(mode) }, modifier = Modifier.testTag(tag))
                    }
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
                        if (triage) "Tudo fica no aparelho. Triagem: o texto das notificações fica guardado até você apagar."
                        else "Tudo fica no aparelho. Texto das notificações não é guardado.", color = FluxoColors.Ink,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
            item { SectionTitle("Dados") }
            item { BackupCard(backupActions) }
            item { ExportCsvCard(transactions) }
            item { TriageCard() }
            item {
                FluxoDataRow("Apagar histórico", onClick = { clear = true }, destructive = true)
            }
            item { Spacer(Modifier.height(8.dp)) }
            item { CaptureHealthCard(access, capture) }
            item { AccountsPanel(accounts, transactions, model, saving, editError) }
            item {
                FluxoSettingsCard {
                    FluxoSectionHeading("Privacidade e cobertura")
                    Text("O Android permite acesso às notificações. Com a captura ativa, o Fluxo lê notificações de todos os apps e só guarda eventos financeiros reconhecidos. Texto completo não é guardado nem enviado à internet, exceto pela triagem provisória, que guarda o texto só neste aparelho enquanto estiver ativa.",
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
