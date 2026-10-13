package br.com.fluxo.app

import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.lifecycle.repeatOnLifecycle
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
        // The stored choice is read synchronously so the window background and the bar icons are right from the
        // first frame (no light flash when the app is set to dark).
        val mode = (application as FluxoApplication).repository.themeMode.value
        val systemNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (mode) { ThemeMode.SYSTEM -> systemNight; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
        val paper = (if (dark) FluxoPalette.Dark else FluxoPalette.Light).paper.toArgb()
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(paper) else SystemBarStyle.light(paper, paper),
            navigationBarStyle = if (dark) SystemBarStyle.dark(paper) else SystemBarStyle.light(paper, paper)
        )
        window.setBackgroundDrawable(ColorDrawable(paper))
        setContent { FluxoScreen() }
    }
}

/** Readable width of tab content and of the bottom navigation on very wide windows (tablets, unfolded foldables). */
private val CONTENT_MAX_WIDTH = 640.dp
private val NAV_MAX_WIDTH = 560.dp

@Composable
private fun notificationAccess(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun check() = CaptureConnection.hasAccess(context)
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
    val themeMode by model.themeMode.collectAsStateWithLifecycle()
    val salary by model.salaryMonthlyCents.collectAsStateWithLifecycle()
    val capture by model.captureEnabled.collectAsStateWithLifecycle()
    val allowOtherApps by model.allowOtherApps.collectAsStateWithLifecycle()
    val banks by model.enabledBanks.collectAsStateWithLifecycle()
    val captureError by model.captureError.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val editError by model.editError.collectAsStateWithLifecycle()
    val access = notificationAccess()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Foreground only, no polling: if the system has not connected the listener shortly after the app
    // opens, ask for one recovery (rate limited inside CaptureConnection).
    LaunchedEffect(access, capture, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            CaptureConnection.autoReconnectIfNeeded(context, capture)
        }
    }
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
    val darkTheme = themeMode.resolveDark()
    SideEffect {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            window.setBackgroundDrawable(ColorDrawable((if (darkTheme) FluxoPalette.Dark else FluxoPalette.Light).paper.toArgb()))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    LaunchedEffect(model) { model.events.collect { snackbar.showSnackbar(it) } }
    val monthly = remember(transactions, month) { transactions.filter { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault())) == month }.sortedByDescending { it.occurredAt } }
    FluxoVisualTheme(themeMode) {
    if (!onboardingDone) {
        OnboardingScreen(
            initialName = profileName, initialSalaryCents = salary, initialSettings = benefits, saving = saving, error = editError,
            onStart = { name, salaryCents, vr, vt, vrDays, vtDays -> model.completeOnboarding(name, salaryCents, vr, vt, vrDays, vtDays) }
        )
    } else {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val destinations = listOf("Resumo", "Lançamentos", "Planejamento", "Ajustes")
            val labels = listOf("Início", "Registros", "Planejar", "Ajustes")
            val icons = listOf(Icons.Outlined.Home, Icons.AutoMirrored.Filled.ReceiptLong, Icons.Outlined.BarChart, Icons.Outlined.Tune)
            Surface(color = FluxoColors.Paper, tonalElevation = 0.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalDivider(color = FluxoColors.Line)
                    if (largeFont) {
                        Column(Modifier.widthIn(max = NAV_MAX_WIDTH).fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
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
                            Modifier.widthIn(max = NAV_MAX_WIDTH).fillMaxWidth().navigationBarsPadding().height(56.dp).padding(horizontal = 6.dp),
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
        // Month changes dip the content and fade it back in (the data swaps at once, so nothing reflows mid-read).
        val monthFade = remember { Animatable(1f) }
        var lastMonth by remember { mutableStateOf(month) }
        LaunchedEffect(month) {
            if (month != lastMonth) {
                lastMonth = month
                monthFade.snapTo(.3f)
                monthFade.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            }
        }
        val saveableStateHolder = rememberSaveableStateHolder()
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth()) {
                if (captureError != null) Text(captureError.orEmpty(), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                // Fade-through with a small horizontal offset in the direction of the tab order. Each tab keeps
                // its saveable state (search text, scroll position) while it is off screen.
                AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = monthFade.value },
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(130, delayMillis = 70, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { direction * it / 12 }) togetherWith
                            (fadeOut(tween(90, easing = FastOutSlowInEasing)) +
                                slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -direction * it / 12 })
                    },
                    label = "tabs"
                ) { page ->
                    saveableStateHolder.SaveableStateProvider(page) {
                        when (page) {
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
                                previousMonth = { monthText = month.minusMonths(1).toString() }, nextMonth = { monthText = month.plusMonths(1).toString() },
                                salaryMonthlyCents = salary)
                            else -> SettingsScreen(capture, allowOtherApps, banks, access, model, transactions)
                        }
                    }
                }
            }
        }
    }
    }
    // Inside the theme: the editor dialog window reads the same palette and Material scheme.
    if (adding || editing != null) TransactionEditor(editing, saving, editError, accounts,
        onUnlink = { model.unlinkTransfer(it) { editingId = null } },
        onDismiss = { adding = false; editingId = null }, onSave = { model.save(it) { adding = false; editingId = null } }, onDelete = { model.delete(it) { editingId = null } })
    }
}

/** Bottom navigation destination: petrol pill with white icon and label when selected. */
@Composable
private fun FluxoNavItem(
    label: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, showIcon: Boolean = true
) {
    // One 0..1 progress drives both colours, so a theme switch (which moves the palette every frame) never retargets it.
    val selection by animateFloatAsState(if (selected) 1f else 0f, tween(180, easing = FastOutSlowInEasing), label = "navSelected")
    val fg = lerp(FluxoColors.Mute, FluxoColors.OnPetrol, selection)
    Column(
        modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 60.dp)
            .clip(RoundedCornerShape(22.dp))
            .drawBehind { drawRect(FluxoColors.Petrol.copy(alpha = selection)) }
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
