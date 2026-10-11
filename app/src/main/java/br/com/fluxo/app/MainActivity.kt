package br.com.fluxo.app

import android.content.ComponentName
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
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
internal fun MonthPicker(month: YearMonth, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = previous) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Mês anterior") }
        Text(month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ptBr)).replaceFirstChar { it.titlecase(ptBr) }, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = next) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Próximo mês") }
    }
}
