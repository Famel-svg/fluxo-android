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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
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
        val paper = android.graphics.Color.rgb(0xFB, 0xFC, 0xFC)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(paper, paper),
            navigationBarStyle = SystemBarStyle.light(paper, paper)
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
    val profileName by model.profileName.collectAsStateWithLifecycle()
    val onboardingDone by model.onboardingDone.collectAsStateWithLifecycle()
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
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    LaunchedEffect(model) { model.events.collect { snackbar.showSnackbar(it) } }
    val monthly = remember(transactions, month) { transactions.filter { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())) == month }.sortedByDescending { it.occurredAt } }
    FluxoVisualTheme {
    if (!onboardingDone) {
        OnboardingScreen(
            initialName = profileName, initialSettings = benefits, saving = saving, error = editError,
            onStart = { name, vr, vt, vrDays, vtDays -> model.completeOnboarding(name, vr, vt, vrDays, vtDays) },
            onSkip = { model.skipOnboarding() }
        )
    } else
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val destinations = listOf("Resumo", "Lançamentos", "Planejamento", "Ajustes")
            val labels = listOf("Início", "Registros", "Planejar", "Ajustes")
            val icons = listOf(Icons.Outlined.Home, Icons.AutoMirrored.Filled.ReceiptLong, Icons.Outlined.BarChart, Icons.Outlined.Tune)
            Surface(color = FluxoColors.Paper, tonalElevation = 0.dp) {
                Column(Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = FluxoColors.Line)
                    if (largeFont) {
                        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                            destinations.chunked(2).forEachIndexed { row, names ->
                                Row(Modifier.fillMaxWidth()) {
                                    names.forEachIndexed { column, name ->
                                        val index = row * 2 + column
                                        FluxoNavItem(labels[index], name, icons[index], tab == index, { tab = index },
                                            Modifier.weight(1f).testTag("navigation.$index"), showIcon = false)
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp).padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically
                        ) {
                            destinations.forEachIndexed { index, name ->
                                FluxoNavItem(labels[index], name, icons[index], tab == index, { tab = index },
                                    Modifier.testTag("navigation.$index"))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 800.dp).fillMaxWidth()) {
                if (captureError != null) Text(captureError.orEmpty(), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                when (tab) {
                    0 -> Dashboard(
                        rows = monthly, month = month, allRows = transactions,
                        active = capture && access && (banks.isNotEmpty() || allowOtherApps),
                        previous = { monthText = month.minusMonths(1).toString() },
                        next = { monthText = month.plusMonths(1).toString() },
                        settings = { tab = 3 },
                        showAll = { filter = 0; allMonths = false; tab = 1 },
                        review = { filter = 3; allMonths = false; tab = 1 },
                        add = { model.resetEditError(); adding = true },
                        edit = { model.resetEditError(); editingId = it.id },
                        greetingName = profileName,
                        benefits = benefits
                    )
                    1 -> RecordsScreen(
                        rows = if (allMonths) transactions else monthly,
                        allRows = transactions,
                        filter = filter,
                        setFilter = { filter = it },
                        allMonths = allMonths,
                        setAllMonths = { allMonths = it },
                        edit = { model.resetEditError(); editingId = it.id },
                        month = month,
                        previousMonth = { monthText = month.minusMonths(1).toString() },
                        nextMonth = { monthText = month.plusMonths(1).toString() },
                        add = { model.resetEditError(); adding = true },
                        bankOptions = listOf<String?>(null, "manual") + supportedBanks.map { it.packageName },
                        bankLabel = ::bankName,
                        moneyLabel = ::money,
                        directionLabel = { it.label() },
                        methodLabel = { it.label() }
                    )
                    2 -> PlanningScreen(transactions, budgets, month, saving, editError, model::resetEditError, model::saveBudget, model::deleteBudget, benefits, model::saveBenefits,
                        previousMonth = { monthText = month.minusMonths(1).toString() }, nextMonth = { monthText = month.plusMonths(1).toString() })
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

/** Bottom navigation destination: petrol pill with white icon and label when selected. */
@Composable
private fun FluxoNavItem(
    label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, showIcon: Boolean = true
) {
    val fg = if (selected) Color.White else FluxoColors.Mute
    Column(
        modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 60.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) FluxoColors.Petrol else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        if (showIcon) Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, color = fg, textAlign = TextAlign.Center, maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold))
    }
}
