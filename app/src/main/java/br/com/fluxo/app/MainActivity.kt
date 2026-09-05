package br.com.fluxo.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.fluxo.core.Direction
import br.com.fluxo.core.Budget
import br.com.fluxo.core.Category
import br.com.fluxo.core.TransactionSearch
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FluxoTheme { FluxoScreen() } }
    }
}

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as FluxoApplication).repository
    val transactions = repository.transactions
    val budgets = repository.budgets
    val captureEnabled = repository.captureEnabled
    val enabledBanks = repository.enabledBanks
    val captureError = repository.captureError
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _editError = MutableStateFlow<String?>(null)
    val editError = _editError.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    init { execute { repository.refresh() } }
    private fun execute(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { messages.send("Não foi possível concluir. Tente novamente.") }
        }
    }
    private fun edit(onSuccess: () -> Unit, action: suspend () -> Unit) {
        if (!_saving.compareAndSet(false, true)) return
        _editError.value = null
        execute {
            try { action(); withContext(Dispatchers.Main) { onSuccess() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _editError.value = "Não foi possível salvar a alteração. Seus campos foram mantidos; tente novamente." }
            finally { _saving.value = false }
        }
    }
    fun save(transaction: Transaction, onSuccess: () -> Unit) = edit(onSuccess) { repository.save(transaction) }
    fun delete(id: String, onSuccess: () -> Unit) = edit(onSuccess) { repository.delete(id) }
    fun saveBudget(budget: Budget, onSuccess: () -> Unit) = edit(onSuccess) { repository.saveBudget(budget) }
    fun deleteBudget(month: String, category: Category, onSuccess: () -> Unit) = edit(onSuccess) { repository.deleteBudget(month, category) }
    fun resetEditError() { _editError.value = null }
    fun clear() = execute { repository.clear() }
    fun capture(enabled: Boolean) = execute { repository.setCaptureEnabled(enabled) }
    fun bank(packageName: String, enabled: Boolean) = execute { repository.setBankEnabled(packageName, enabled) }
}

private val ptBr = Locale.forLanguageTag("pt-BR")
private fun money(cents: Long?) = cents?.let { money(BigDecimal.valueOf(it, 2)) } ?: "Valor pendente"
private fun money(value: BigDecimal) = NumberFormat.getCurrencyInstance(ptBr).format(value)
private fun Direction.label() = when (this) { Direction.INCOME -> "Entrada"; Direction.EXPENSE -> "Saída"; Direction.UNKNOWN -> "Não definida" }
private fun PaymentMethod.label() = when (this) { PaymentMethod.PIX -> "Pix"; PaymentMethod.CREDIT_CARD -> "Cartão de crédito"; PaymentMethod.DEBIT_CARD -> "Cartão de débito"; PaymentMethod.UNKNOWN -> "Outro / não informado" }
private fun bankName(packageName: String) = supportedBanks.firstOrNull { it.packageName == packageName }?.name ?: "Manual"

@Composable
private fun FluxoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme(primary = Color(0xFF80D5BF), secondary = Color(0xFFB2CCC2))
    else lightColorScheme(primary = Color(0xFF006B56), secondary = Color(0xFF4D635B), primaryContainer = Color(0xFF9CF2D8))
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun notificationAccess(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun check() = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.split(':')?.mapNotNull { ComponentName.unflattenFromString(it) }
        ?.any { it.packageName == context.packageName } == true
    var granted by remember { mutableStateOf(check()) }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) granted = check() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return granted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FluxoScreen(model: LedgerViewModel = viewModel()) {
    val transactions by model.transactions.collectAsStateWithLifecycle()
    val budgets by model.budgets.collectAsStateWithLifecycle()
    val capture by model.captureEnabled.collectAsStateWithLifecycle()
    val banks by model.enabledBanks.collectAsStateWithLifecycle()
    val captureError by model.captureError.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val editError by model.editError.collectAsStateWithLifecycle()
    val access = notificationAccess()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var allMonths by rememberSaveable { mutableStateOf(false) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = transactions.firstOrNull { it.id == editingId }
    var adding by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model) { model.events.collect { snackbar.showSnackbar(it) } }
    val monthly = remember(transactions, month) { transactions.filter { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())) == month }.sortedByDescending { it.occurredAt } }
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("Fluxo", fontWeight = FontWeight.Bold); Text("Suas finanças, no seu aparelho", style = MaterialTheme.typography.labelMedium) } }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                listOf("Resumo", "Lançamentos", "Planejamento", "Ajustes").forEachIndexed { index, name ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, label = { Text(name) }, icon = { Icon(listOf(Icons.Default.Dashboard, Icons.AutoMirrored.Filled.ReceiptLong, Icons.Default.PieChart, Icons.Default.Settings)[index], null) })
                }
            }
        },
        floatingActionButton = { if (tab < 2) FloatingActionButton(onClick = { model.resetEditError(); adding = true }) { Icon(Icons.Default.Add, "Adicionar lançamento") } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
                if (captureError != null) Text(captureError.orEmpty(), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (tab != 3 && !(tab == 1 && allMonths)) MonthPicker(month, { monthText = month.minusMonths(1).toString() }, { monthText = month.plusMonths(1).toString() })
                when (tab) {
                    0 -> Dashboard(monthly, capture && access && banks.isNotEmpty(), { tab = 3 }, { filter = 0; allMonths = false; tab = 1 }, { filter = 3; allMonths = false; tab = 1 }, { model.resetEditError(); editingId = it.id })
                    1 -> Transactions(if (allMonths) transactions else monthly, filter, { filter = it }, allMonths, { allMonths = it }, { model.resetEditError(); editingId = it.id })
                    2 -> PlanningScreen(transactions, budgets, month, saving, editError, model::resetEditError, model::saveBudget, model::deleteBudget)
                    else -> SettingsScreen(capture, banks, access, model, transactions)
                }
            }
        }
    }
    if (adding || editing != null) TransactionEditor(editing, saving, editError, onDismiss = { adding = false; editingId = null }, onSave = { model.save(it) { adding = false; editingId = null } }, onDelete = { model.delete(it) { editingId = null } })
}

@Composable
private fun MonthPicker(month: YearMonth, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = previous) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Mês anterior") }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBr)).replaceFirstChar { it.titlecase(ptBr) }, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = next) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Próximo mês") }
    }
}

@Composable
private fun Dashboard(rows: List<Transaction>, active: Boolean, settings: () -> Unit, showAll: () -> Unit, review: () -> Unit, edit: (Transaction) -> Unit) {
    val confirmed = rows.filter { it.status == ReviewStatus.CONFIRMED }
    fun total(predicate: (Transaction) -> Boolean): BigDecimal = confirmed.filter(predicate).fold(BigDecimal.ZERO) { total, row -> total + BigDecimal.valueOf(row.amountCents ?: 0, 2) }
    val income = total { it.direction == Direction.INCOME }
    val cash = total { it.direction == Direction.EXPENSE && it.method != PaymentMethod.CREDIT_CARD }
    val credit = total { it.direction == Direction.EXPENSE && it.method == PaymentMethod.CREDIT_CARD }
    val pending = rows.count { it.status == ReviewStatus.REVIEW }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RESULTADO OBSERVADO", style = MaterialTheme.typography.labelLarge)
                    Text(money(income - cash), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Entradas menos saídas de caixa. Cobertura parcial: este valor não é seu saldo bancário.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Metric("Entradas registradas", money(income), Icons.Default.SouthWest) }
        item { Metric("Saídas de caixa", money(cash), Icons.Default.NorthEast) }
        item { Metric("Compras no crédito", money(credit), Icons.Default.CreditCard, "Compromissos separados das saídas de caixa") }
        item {
            OutlinedCard(onClick = settings) {
                ListItem(headlineContent = { Text(if (active) "Captura habilitada" else "Configure a captura") }, supportingContent = { Text(if (active) "Somente notificações futuras dos bancos selecionados" else "Escolha bancos, permita acesso e ative a captura") }, leadingContent = { Icon(Icons.Default.Notifications, null) })
            }
        }
        if (pending > 0) item { FilledTonalButton(onClick = review, modifier = Modifier.fillMaxWidth()) { Text("$pending lançamento(s) para revisar") } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Últimos lançamentos", style = MaterialTheme.typography.titleMedium); TextButton(onClick = showAll) { Text("Ver todos") } } }
        if (rows.isEmpty()) item { EmptyState("Seu mês começa aqui", "Adicione um lançamento ou ative a captura nas configurações. Nenhum histórico anterior será importado.") }
        items(rows.take(5), key = { it.id }) { TransactionRow(it, edit) }
    }
}

@Composable
private fun Metric(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, subtitle: String? = null) {
    OutlinedCard {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column { Text(title, style = MaterialTheme.typography.bodyMedium); Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold); if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun Transactions(rows: List<Transaction>, filter: Int, setFilter: (Int) -> Unit, allMonths: Boolean, setAllMonths: (Boolean) -> Unit, edit: (Transaction) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var bank by rememberSaveable { mutableStateOf<String?>(null) }
    var method by rememberSaveable { mutableStateOf<PaymentMethod?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(rows, filter, query, bank, method, category) {
        TransactionSearch.filter(rows.filter { when (filter) { 1 -> it.direction == Direction.INCOME; 2 -> it.direction == Direction.EXPENSE; 3 -> it.status == ReviewStatus.REVIEW; else -> true } }, query, bank, method, category).sortedByDescending { it.occurredAt }
    }
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { OutlinedTextField(query, { query = it }, label = { Text("Buscar descrição, banco ou categoria") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todos", "Entradas", "Saídas", "Revisar").forEachIndexed { index, label -> FilterChip(selected = filter == index, onClick = { setFilter(index) }, label = { Text(label) }) }
        } }
        item { FilterChip(selected = allMonths, onClick = { setAllMonths(!allMonths) }, label = { Text("Todos os meses") }) }
        item { TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar filtros" else "Banco, meio e categoria") } }
        if (advanced) {
            item { Choice("Banco", bank, listOf<String?>(null, "manual") + supportedBanks.map { it.packageName }, { it?.let(::bankName) ?: "Todos" }) { bank = it } }
            item { Choice("Meio", method, listOf<PaymentMethod?>(null) + PaymentMethod.entries, { it?.label() ?: "Todos" }) { method = it } }
            item { Choice("Categoria", category, listOf<Category?>(null) + Category.entries, { it?.label ?: "Todas" }) { category = it } }
        }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} lançamento(s)")
            TextButton(onClick = { query = ""; bank = null; method = null; category = null; setFilter(0); setAllMonths(false) }) { Text("Limpar filtros") }
        } }
        if (filtered.isEmpty()) item { EmptyState("Nenhum lançamento", "Nenhum registro corresponde aos filtros. Altere a busca ou escolha Todos os meses.") }
        items(filtered, key = { it.id }) { TransactionRow(it, edit) }
    }
}

@Composable
private fun TransactionRow(row: Transaction, edit: (Transaction) -> Unit) {
    OutlinedCard(onClick = { edit(row) }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(bankName(row.bankPackage) + " · " + row.direction.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(money(row.amountCents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(row.description.ifBlank { row.method.label() }, style = MaterialTheme.typography.bodyMedium)
            Text(row.category.label, style = MaterialTheme.typography.labelMedium)
            Text(row.method.label() + " · " + Instant.ofEpochMilli(row.occurredAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ptBr)), style = MaterialTheme.typography.labelMedium)
            if (row.status == ReviewStatus.REVIEW) Text("Revisar · ${row.reason.ifBlank { "Confirme valor e classificação" }}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingsScreen(capture: Boolean, banks: Set<String>, access: Boolean, model: LedgerViewModel, transactions: List<Transaction>) {
    val context = LocalContext.current
    val backupActions = rememberBackupActions()
    var clear by rememberSaveable { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Você controla a captura", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("O Android permite acesso às notificações. O Fluxo processa somente os bancos selecionados, com captura ativada. Não guarda o texto completo nem envia dados à internet.") }
        item {
            OutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (access) "Acesso às notificações permitido" else "Acesso às notificações necessário", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { settingsError = true } }) { Text("Abrir configurações Android") }
                if (settingsError) Text("Abra Configurações do Android → Acesso especial → Acesso às notificações.", color = MaterialTheme.colorScheme.error)
            } }
        }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Capturar notificações", style = MaterialTheme.typography.titleMedium); Text(if (!access) "Aguardando permissão Android" else if (capture && banks.isNotEmpty()) "Ativa para os bancos selecionados" else "Pausada ou sem bancos selecionados", style = MaterialTheme.typography.bodySmall) }
            Switch(checked = capture, onCheckedChange = model::capture, modifier = Modifier.semantics { contentDescription = "Capturar notificações bancárias" })
        } }
        item { Text("Bancos permitidos", style = MaterialTheme.typography.titleMedium) }
        items(supportedBanks, key = { it.packageName }) { bank ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(bank.name, Modifier.weight(1f)); Switch(checked = bank.packageName in banks, onCheckedChange = { model.bank(bank.packageName, it) }, modifier = Modifier.semantics { contentDescription = "Permitir notificações de ${bank.name}" }) }
        }
        item { CaptureHealthCard(access, capture, banks) }
        item { HorizontalDivider() }
        item { ExportCsvCard(transactions) }
        item { BackupCard(backupActions) }
        item { Text("Privacidade e cobertura", style = MaterialTheme.typography.titleMedium) }
        item { Text("Dados no armazenamento privado do aplicativo. Sem login, servidor ou backup automático. A proteção depende também do bloqueio e da segurança do aparelho.\n\nBancos podem omitir valores, mudar mensagens ou não notificar. Revise registros ambíguos. Pagamentos de fatura não devem virar novas compras. Totais não substituem o extrato bancário.") }
        item { OutlinedButton(onClick = { clear = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Apagar todos os lançamentos") } }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Apagar todo o histórico?") }, text = { Text("Esta ação apaga permanentemente todos os lançamentos, os limites de orçamento e os identificadores usados para evitar duplicatas. Não é possível desfazer. A captura será pausada. Após reativá-la, notificações futuras poderão gerar novos registros.") }, confirmButton = { TextButton(onClick = { model.clear(); clear = false }) { Text("Apagar definitivamente") } }, dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancelar") } })
}

@Composable
private fun TransactionEditor(existing: Transaction?, saving: Boolean, editError: String?, onDismiss: () -> Unit, onSave: (Transaction) -> Unit, onDelete: (String) -> Unit) {
    var amount by rememberSaveable(existing?.id) { mutableStateOf(existing?.amountCents?.let { BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') } ?: "") }
    var description by rememberSaveable(existing?.id) { mutableStateOf(existing?.description ?: "") }
    var direction by rememberSaveable(existing?.id) { mutableStateOf(existing?.direction ?: Direction.EXPENSE) }
    var method by rememberSaveable(existing?.id) { mutableStateOf(existing?.method ?: PaymentMethod.UNKNOWN) }
    var bank by rememberSaveable(existing?.id) { mutableStateOf(existing?.bankPackage ?: "manual") }
    var category by rememberSaveable(existing?.id) { mutableStateOf(existing?.category ?: Category.UNCATEGORIZED) }
    var date by rememberSaveable(existing?.id) { mutableStateOf((existing?.let { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val cents = remember(amount) { runCatching { require(amount.matches(Regex("[0-9]+([,.][0-9]{1,2})?"))); BigDecimal(amount.replace(',', '.')).movePointRight(2).longValueExact().also { require(it > 0) } }.getOrNull() }
    val parsedDate = remember(date) { runCatching { LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)) }.getOrNull() }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (existing == null) "Novo lançamento" else "Revisar lançamento") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (existing?.status == ReviewStatus.REVIEW) Text(existing.reason.ifBlank { "Confira os dados antes de confirmar." }, color = MaterialTheme.colorScheme.error)
            if (editError != null) Text(editError, color = MaterialTheme.colorScheme.error)
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Valor em reais") }, supportingText = { Text("Ex.: 125,90 · sem separador de milhar") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = amount.isNotEmpty() && cents == null, singleLine = true)
            OutlinedTextField(value = description, onValueChange = { description = it.take(120) }, label = { Text("Descrição") }, singleLine = true)
            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Data (dd/mm/aaaa)") }, isError = parsedDate == null, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
            Choice("Direção", direction, Direction.entries, { it.label() }) { direction = it }
            Choice("Meio", method, PaymentMethod.entries, { it.label() }) { method = it }
            Choice("Banco", bank, listOf("manual") + supportedBanks.map { it.packageName }, ::bankName) { bank = it }
            Choice("Categoria", category, Category.entries, { it.label }) { category = it }
            if (method == PaymentMethod.CREDIT_CARD) Text("Compras no crédito ficam separadas das saídas de caixa.", style = MaterialTheme.typography.bodySmall)
            if (existing != null) TextButton(enabled = !saving, onClick = { deleting = true }) { Text("Excluir lançamento", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving && cents != null && direction != Direction.UNKNOWN && parsedDate != null, onClick = {
        val validDate = parsedDate ?: return@TextButton
        val originalDate = existing?.let { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
        val occurred = if (existing != null && originalDate == validDate) existing.occurredAt else validDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        onSave(Transaction(existing?.id ?: UUID.randomUUID().toString(), existing?.sourceKey, bank, cents, direction, method, ReviewStatus.CONFIRMED, occurred, description.ifBlank { "Lançamento manual" }, "", category))
    }) { Text(if (saving) "Salvando…" else "Salvar e confirmar") } }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } })
    if (deleting && existing != null) AlertDialog(onDismissRequest = { if (!saving) deleting = false }, title = { Text("Excluir lançamento?") }, text = { Text(editError ?: "O lançamento será removido dos totais. Sua notificação original não recriará este registro.") }, confirmButton = { TextButton(enabled = !saving, onClick = { onDelete(existing.id) }) { Text(if (saving) "Excluindo…" else "Excluir") } }, dismissButton = { TextButton(enabled = !saving, onClick = { deleting = false }) { Text("Cancelar") } })
}

@Composable
internal fun <T> Choice(label: String, selected: T, options: List<T>, display: (T) -> String, change: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${display(selected)}", modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(display(option)) }, onClick = { change(option); expanded = false }) } }
    }
}
