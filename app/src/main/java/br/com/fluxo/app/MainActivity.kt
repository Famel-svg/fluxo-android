package br.com.fluxo.app

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.fluxo.core.Direction
import br.com.fluxo.core.BenefitDays
import br.com.fluxo.core.Budget
import br.com.fluxo.core.Account
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
        val navy = android.graphics.Color.rgb(0, 16, 32)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(navy),
            navigationBarStyle = SystemBarStyle.dark(navy)
        )
        setContent { FluxoTheme { FluxoScreen() } }
    }
}

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as FluxoApplication).repository
    val transactions = repository.transactions
    val accounts = repository.accounts
    val budgets = repository.budgets
    val benefits = repository.benefits
    val captureEnabled = repository.captureEnabled
    val allowOtherApps = repository.allowOtherApps
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
    fun saveAccount(account: Account, onSuccess: () -> Unit) = edit(onSuccess) { repository.saveAccount(account) }
    fun createTransfer(from: String, to: String, cents: Long, at: Long, onSuccess: () -> Unit) = edit(onSuccess) { repository.createTransfer(from, to, cents, at) }
    fun linkTransfer(outgoing: String, incoming: String, onSuccess: () -> Unit) = edit(onSuccess) { repository.linkTransfer(outgoing, incoming) }
    fun unlinkTransfer(id: String, onSuccess: () -> Unit) = edit(onSuccess) { repository.unlinkTransfer(id) }
    fun delete(id: String, onSuccess: () -> Unit) = edit(onSuccess) { repository.delete(id) }
    fun saveBudget(budget: Budget, onSuccess: () -> Unit) = edit(onSuccess) { repository.saveBudget(budget) }
    fun deleteBudget(month: String, category: Category, onSuccess: () -> Unit) = edit(onSuccess) { repository.deleteBudget(month, category) }
    fun saveBenefits(month: YearMonth, vr: Long, vt: Long, days: BenefitDays, onSuccess: () -> Unit) =
        edit(onSuccess) { repository.saveBenefits(month, vr, vt, days) }
    fun resetEditError() { _editError.value = null }
    fun clear() = execute { repository.clear() }
    fun capture(enabled: Boolean) = execute { repository.setCaptureEnabled(enabled) }
    fun allowOtherApps(enabled: Boolean) = execute { repository.setAllowOtherApps(enabled) }
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
    FluxoVisualTheme(content = content)
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
internal fun FluxoScreen(model: LedgerViewModel = viewModel()) {
    val transactions by model.transactions.collectAsStateWithLifecycle()
    val accounts by model.accounts.collectAsStateWithLifecycle()
    val budgets by model.budgets.collectAsStateWithLifecycle()
    val benefits by model.benefits.collectAsStateWithLifecycle()
    val capture by model.captureEnabled.collectAsStateWithLifecycle()
    val allowOtherApps by model.allowOtherApps.collectAsStateWithLifecycle()
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
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    LaunchedEffect(model) { model.events.collect { snackbar.showSnackbar(it) } }
    val monthly = remember(transactions, month) { transactions.filter { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())) == month }.sortedByDescending { it.occurredAt } }
    FluxoVisualTheme {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            if (tab != 0) Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(painterResource(R.drawable.ic_fluxo_mark), contentDescription = null, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fluxo", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (!largeFont) Text("Suas finanças, no seu aparelho", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val destinations = listOf("Resumo", "Lançamentos", "Planejamento", "Ajustes")
            val labels = listOf("Início", "Registros", "Planejar", "Ajustes")
            val icons = listOf(Icons.Default.Home, Icons.AutoMirrored.Filled.ReceiptLong, Icons.Default.PieChart, Icons.Default.Settings)
            if (largeFont) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp)) {
                        destinations.chunked(2).forEachIndexed { row, names ->
                            Row(Modifier.fillMaxWidth()) {
                                names.forEachIndexed { column, name ->
                                    val index = row * 2 + column
                                    TextButton(
                                        onClick = { tab = index },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("navigation.$index").semantics { selected = tab == index; role = Role.Tab; contentDescription = name },
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = if (tab == index) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                            contentColor = if (tab == index) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(labels[index], textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    destinations.forEachIndexed { index, name ->
                        NavigationBarItem(
                            modifier = Modifier.testTag("navigation.$index").semantics { contentDescription = name },
                            selected = tab == index, onClick = { tab = index },
                            label = { Text(labels[index]) }, icon = { Icon(icons[index], null) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = { if (tab == 1) FloatingActionButton(onClick = { model.resetEditError(); adding = true }) { Icon(Icons.Default.Add, "Adicionar lançamento") } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
                if (captureError != null) Text(captureError.orEmpty(), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (tab != 0 && tab != 3 && !(tab == 1 && allMonths)) MonthPicker(month, { monthText = month.minusMonths(1).toString() }, { monthText = month.plusMonths(1).toString() })
                when (tab) {
                    0 -> Dashboard(
                        rows = monthly, month = month,
                        active = capture && access && (banks.isNotEmpty() || allowOtherApps),
                        previous = { monthText = month.minusMonths(1).toString() },
                        next = { monthText = month.plusMonths(1).toString() },
                        settings = { tab = 3 },
                        showAll = { filter = 0; allMonths = false; tab = 1 },
                        review = { filter = 3; allMonths = false; tab = 1 },
                        add = { model.resetEditError(); adding = true },
                        planning = { tab = 2 },
                        edit = { model.resetEditError(); editingId = it.id }
                    )
                    1 -> RecordsScreen(
                        rows = if (allMonths) transactions else monthly,
                        filter = filter,
                        setFilter = { filter = it },
                        allMonths = allMonths,
                        setAllMonths = { allMonths = it },
                        edit = { model.resetEditError(); editingId = it.id },
                        bankOptions = listOf<String?>(null, "manual") + supportedBanks.map { it.packageName },
                        bankLabel = ::bankName,
                        moneyLabel = ::money,
                        directionLabel = { it.label() },
                        methodLabel = { it.label() }
                    )
                    2 -> PlanningScreen(transactions, budgets, month, saving, editError, model::resetEditError, model::saveBudget, model::deleteBudget, benefits, model::saveBenefits)
                    else -> SettingsScreen(capture, allowOtherApps, banks, access, model, transactions)
                }
            }
        }
    }
    }
    if (adding || editing != null) TransactionEditor(editing, saving, editError, accounts,
        onUnlink = { model.unlinkTransfer(it) { editingId = null } },
        onDismiss = { adding = false; editingId = null }, onSave = { model.save(it) { adding = false; editingId = null } }, onDelete = { model.delete(it) { editingId = null } })
}

@Composable
private fun MonthPicker(month: YearMonth, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = previous) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Mês anterior") }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBr)).replaceFirstChar { it.titlecase(ptBr) }, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = next) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Próximo mês") }
    }
}

// Dashboard accents match the shared navy theme used by all destinations and editors.
private val NeonMint = Color(0xFF00E5B5)
private val NeonBlue = Color(0xFF51C8FF)
private val NeonLime = Color(0xFFD5F568)
private val NeonAmber = Color(0xFFFFD166)
@Composable
private fun NeonPanel(
    modifier: Modifier = Modifier,
    accent: Color = NeonBlue,
    background: List<Color> = listOf(Color(0xFF061D2E), Color(0xFF001020)),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = Color.Transparent, border = BorderStroke(1.dp, accent.copy(alpha = .38f))
    ) {
        Column(
            Modifier.background(Brush.linearGradient(background)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content
        )
    }
}

@Composable
private fun NeonHeading(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color = NeonMint) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun NeonPair(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 340.dp || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { first(); second() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        }
    }
}

@Composable
private fun Dashboard(
    rows: List<Transaction>, month: YearMonth, active: Boolean,
    previous: () -> Unit, next: () -> Unit, settings: () -> Unit,
    showAll: () -> Unit, review: () -> Unit, add: () -> Unit,
    planning: () -> Unit, edit: (Transaction) -> Unit
) {
    // Preserve baseline accounting: only confirmed, non-transfer records enter totals.
    val confirmed = rows.filter { it.status == ReviewStatus.CONFIRMED && it.transferId == null }
    fun total(predicate: (Transaction) -> Boolean): BigDecimal = confirmed.filter(predicate)
        .fold(BigDecimal.ZERO) { sum, row -> sum + BigDecimal.valueOf(row.amountCents ?: 0, 2) }
    val income = total { it.direction == Direction.INCOME }
    val cash = total { it.direction == Direction.EXPENSE && it.method != PaymentMethod.CREDIT_CARD }
    val credit = total { it.direction == Direction.EXPENSE && it.method == PaymentMethod.CREDIT_CARD }
    val pending = rows.count { it.status == ReviewStatus.REVIEW }
    val pendingCredit = rows.count { it.status == ReviewStatus.REVIEW && it.method == PaymentMethod.CREDIT_CARD }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var chartCredit by rememberSaveable { mutableStateOf(false) }
    var showDailyValues by rememberSaveable { mutableStateOf(false) }
    fun amount(value: BigDecimal) = if (hidden) "••••" else money(value)
    val expenseRows = confirmed.filter {
        it.direction == Direction.EXPENSE && (it.method == PaymentMethod.CREDIT_CARD) == chartCredit
    }
    val expenseTotal = if (chartCredit) credit else cash
    val today = LocalDate.now()
    val weekEnd = if (YearMonth.from(today) == month) today else month.atEndOfMonth()
    val weekStart = maxOf(month.atDay(1), weekEnd.minusDays(6))
    val dates = (0L..java.time.temporal.ChronoUnit.DAYS.between(weekStart, weekEnd)).map { weekStart.plusDays(it) }
    val zone = ZoneId.systemDefault()
    val daily = dates.map { date -> expenseRows.filter {
        Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() == date
    }.fold(BigDecimal.ZERO) { sum, row -> sum + BigDecimal.valueOf(row.amountCents ?: 0, 2) } }
    val largest = daily.maxOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE
    val categories = expenseRows.groupBy { it.category }.map { (category, items) ->
        category.label to items.fold(BigDecimal.ZERO) { sum, row -> sum + BigDecimal.valueOf(row.amountCents ?: 0, 2) }
    }.sortedByDescending { it.second }
    val accents = listOf(NeonMint, NeonBlue, Color(0xFF43D8EB), NeonLime)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard.neon"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
                    Image(painterResource(R.drawable.ic_fluxo_mark), contentDescription = null, modifier = Modifier.padding(8.dp).size(32.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Olá!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Suas finanças, no seu aparelho", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = showAll) { Icon(Icons.Default.Search, "Buscar lançamentos") }
                IconButton(onClick = settings) { Icon(Icons.Default.Notifications, "Configurar captura de notificações", tint = if (active) NeonMint else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            Text("Visão geral", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            MonthPicker(month, previous, next)
        }
        item {
            NeonPanel(accent = NeonMint, background = listOf(Color(0xFF00584E), Color(0xFF003347), Color(0xFF00162D))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Resultado observado", Modifier.weight(1f), color = Color(0xFF9CF6E1), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { hidden = !hidden }, modifier = Modifier.testTag("dashboard.visibility")) {
                        Icon(if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (hidden) "Mostrar valores do resumo" else "Ocultar valores do resumo")
                    }
                }
                Text(amount(income - cash), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Entradas registradas: ${amount(income)}", style = MaterialTheme.typography.bodyMedium)
                Text("Entradas menos saídas de caixa. Cobertura parcial: este valor não é seu saldo bancário.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            NeonPair(first = {
                NeonPanel(accent = NeonMint, background = listOf(Color(0xFF004439), Color(0xFF002031))) {
                    NeonHeading("Dinheiro", Icons.Default.AccountBalanceWallet)
                    Text(amount(cash), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Saídas de caixa no mês", style = MaterialTheme.typography.bodySmall)
                    Text("Crédito separado", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }, second = {
                NeonPanel(accent = NeonBlue, background = listOf(Color(0xFF123D75), Color(0xFF08163D))) {
                    NeonHeading("Crédito", Icons.Default.CreditCard, NeonBlue)
                    Text(amount(credit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Compras confirmadas no mês", style = MaterialTheme.typography.bodySmall)
                    Text("Limite não informado", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            })
        }
        item {
            NeonPanel {
                NeonHeading("Seus gastos", Icons.Default.BarChart)
                Text("${amount(expenseTotal)} no mês · ${if (chartCredit) "crédito" else "caixa"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !chartCredit, onClick = { chartCredit = false }, label = { Text("Caixa") }, modifier = Modifier.heightIn(min = 48.dp))
                    FilterChip(selected = chartCredit, onClick = { chartCredit = true }, label = { Text("Crédito") }, modifier = Modifier.heightIn(min = 48.dp))
                }
                Text("Semana: ${weekStart.format(DateTimeFormatter.ofPattern("dd/MM"))} a ${weekEnd.format(DateTimeFormatter.ofPattern("dd/MM"))}", style = MaterialTheme.typography.labelLarge)
                if (hidden) {
                    Text("Gráfico e categorias ocultos", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        dates.forEachIndexed { index, date ->
                            val ratio = daily[index].divide(largest, 6, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                            Column(
                                Modifier.widthIn(min = 42.dp).padding(horizontal = 4.dp).clearAndSetSemantics {
                                    contentDescription = "${date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", ptBr))}: ${money(daily[index])}, ${if (chartCredit) "crédito" else "saídas de caixa"}"
                                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(Modifier.height(104.dp).width(24.dp), contentAlignment = Alignment.BottomCenter) {
                                    if (ratio > 0f) Box(Modifier.fillMaxWidth().height((100 * ratio).coerceAtLeast(2f).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Brush.verticalGradient(listOf(accents[index % accents.size], accents[index % accents.size].copy(alpha = .55f)))))
                                }
                                Text(date.format(DateTimeFormatter.ofPattern("EE", ptBr)), style = MaterialTheme.typography.labelSmall)
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Altura relativa ao maior gasto diário. Valores por dia abaixo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { showDailyValues = !showDailyValues }) { Text(if (showDailyValues) "Ocultar valores por dia" else "Ver valores por dia") }
                    if (showDailyValues) dates.forEachIndexed { index, date ->
                        Text("${date.format(DateTimeFormatter.ofPattern("EE dd/MM", ptBr))}: ${money(daily[index])}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Categorias do mês", style = MaterialTheme.typography.titleSmall)
                    if (categories.isEmpty()) Text("Sem gastos confirmados neste recorte.", style = MaterialTheme.typography.bodyMedium)
                    categories.chunked(2).forEachIndexed { group, pair ->
                        NeonPair(first = { NeonCategory(pair[0], expenseTotal, accents[(group * 2) % accents.size]) }, second = {
                            if (pair.size > 1) NeonCategory(pair[1], expenseTotal, accents[(group * 2 + 1) % accents.size])
                        })
                    }
                }
                Text("Revisões e transferências próprias não entram nos totais.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            NeonPanel {
                NeonHeading("Ações rápidas", Icons.Default.Bolt)
                NeonPair(first = { NeonAction("Adicionar", Icons.Default.Add, add, NeonMint) }, second = { NeonAction("Lançamentos", Icons.AutoMirrored.Filled.ReceiptLong, showAll, NeonBlue) })
                NeonPair(first = { NeonAction("Planejar", Icons.Default.PieChart, planning, NeonBlue) }, second = { NeonAction("Mais opções", Icons.Default.Settings, settings, NeonLime) })
            }
        }
        item {
            NeonPanel(accent = NeonMint, background = listOf(Color(0xFF08473C), Color(0xFF00202D))) {
                NeonHeading("Saúde dos registros", Icons.Default.Spa, NeonLime)
                Text(if (pending > 0) "$pending lançamento(s) precisam de revisão." else "Nenhuma revisão pendente neste mês.", fontWeight = FontWeight.SemiBold)
                Text("Cobertura parcial. Os registros disponíveis não permitem avaliar sua saúde financeira.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = if (pending > 0) review else showAll, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (pending > 0) "Revisar lançamentos" else "Ver registros") }
            }
        }
        item {
            NeonPanel(accent = NeonAmber, background = listOf(Color(0xFF34301A), Color(0xFF071C2B))) {
                NeonHeading("Crédito para revisar", Icons.AutoMirrored.Filled.ReceiptLong, NeonAmber)
                Text(if (pendingCredit > 0) "$pendingCredit lançamento(s) de crédito pendente(s)" else "Nenhuma pendência de crédito neste mês", fontWeight = FontWeight.SemiBold)
                Text("Fatura, limite e vencimento não estão disponíveis. Confira no emissor do cartão.", style = MaterialTheme.typography.bodySmall)
                if (pending > 0) TextButton(onClick = review, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abrir todas as revisões") }
            }
        }
        item {
            OutlinedCard(onClick = settings, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NeonHeading(if (active) "Captura habilitada" else "Configure a captura", Icons.Default.Notifications)
                    Text(if (active) "Notificações futuras das origens autorizadas. Isso não garante entrega de cada notificação." else "Escolha bancos ou permita outros apps, conceda acesso e ative a captura.", style = MaterialTheme.typography.bodySmall)
                    Text("Processamento local · texto bruto não armazenado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("Últimos lançamentos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            TextButton(onClick = showAll) { Text("Ver todos") }
        }
        if (rows.isEmpty()) item { EmptyState("Seu mês começa aqui", "Adicione um lançamento ou configure a captura. Nenhum histórico anterior será importado.") }
        else if (hidden) item { Text("Lançamentos ocultos neste resumo.") }
        else items(rows.take(5), key = { it.id }) { TransactionRow(it, edit) }
    }
}

@Composable
private fun NeonCategory(entry: Pair<String, BigDecimal>, total: BigDecimal, accent: Color) {
    val fraction = if (total > BigDecimal.ZERO) entry.second.divide(total, 6, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f) else 0f
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.first, style = MaterialTheme.typography.labelLarge)
            Text(money(entry.second), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant).clearAndSetSemantics { }) {
                if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent))
            }
            Text("${NumberFormat.getPercentInstance(ptBr).format(fraction.toDouble())} do recorte mensal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NeonAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: () -> Unit, accent: Color) {
    OutlinedCard(
        onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, accent.copy(alpha = .5f)),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = accent)
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable
private fun Metric(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, subtitle: String? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
private fun SettingsScreen(capture: Boolean, allowOtherApps: Boolean, banks: Set<String>, access: Boolean, model: LedgerViewModel, transactions: List<Transaction>) {
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

@Composable
private fun TransactionEditor(existing: Transaction?, saving: Boolean, editError: String?, accounts: List<Account>, onUnlink: (String) -> Unit, onDismiss: () -> Unit, onSave: (Transaction) -> Unit, onDelete: (String) -> Unit) {
    val transferId = existing?.transferId
    if (transferId != null) {
        AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Transferência vinculada") },
            text = { Text(editError ?: "Os dois lançamentos compõem uma transferência própria e não entram em renda/consumo. Para editar ou excluir, desvincule primeiro. Isso mantém ambos os registros e volta a tratá-los como entrada e saída comuns.") },
            confirmButton = { TextButton(enabled = !saving, onClick = { onUnlink(transferId) }) { Text("Desvincular transferência") } },
            dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Fechar") } })
        return
    }
    var amount by rememberSaveable(existing?.id) { mutableStateOf(existing?.amountCents?.let { BigDecimal.valueOf(it, 2).toPlainString().replace('.', ',') } ?: "") }
    var description by rememberSaveable(existing?.id) { mutableStateOf(existing?.description ?: "") }
    var direction by rememberSaveable(existing?.id) { mutableStateOf(existing?.direction ?: Direction.EXPENSE) }
    var method by rememberSaveable(existing?.id) { mutableStateOf(existing?.method ?: PaymentMethod.UNKNOWN) }
    var bank by rememberSaveable(existing?.id) { mutableStateOf(existing?.bankPackage ?: "manual") }
    var accountId by rememberSaveable(existing?.id) { mutableStateOf(existing?.accountId) }
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
            Choice("Banco", bank, listOf("manual") + supportedBanks.map { it.packageName }, ::bankName) { bank = it; accountId = null }
            if (accounts.isNotEmpty()) Choice("Conta", accountId, listOf<String?>(null) + accounts.filter { !it.archived || it.id == accountId }.map { it.id },
                { id -> accounts.firstOrNull { it.id == id }?.name ?: "Não atribuída" }) { id -> accountId = id; accounts.firstOrNull { it.id == id }?.let { bank = it.bankPackage } }
            Choice("Categoria", category, Category.entries, { it.label }) { category = it }
            if (method == PaymentMethod.CREDIT_CARD) Text("Compras no crédito ficam separadas das saídas de caixa.", style = MaterialTheme.typography.bodySmall)
            if (existing != null) TextButton(enabled = !saving, onClick = { deleting = true }) { Text("Excluir lançamento", color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !saving && cents != null && direction != Direction.UNKNOWN && parsedDate != null, onClick = {
        val validDate = parsedDate ?: return@TextButton
        val originalDate = existing?.let { Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
        val occurred = if (existing != null && originalDate == validDate) existing.occurredAt else validDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        onSave(Transaction(existing?.id ?: UUID.randomUUID().toString(), existing?.sourceKey, bank, cents, direction, method, ReviewStatus.CONFIRMED, occurred, description.ifBlank { "Lançamento manual" }, "", category, accountId))
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
