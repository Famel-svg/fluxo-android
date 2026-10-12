package br.com.fluxo.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.fluxo.core.BenefitCalculator
import br.com.fluxo.core.BenefitSettings
import br.com.fluxo.core.MoneyParser
import br.com.fluxo.core.SalaryPlanner
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.YearMonth

private const val MAX_NAME = 40
private const val STEPS = 3
private const val STEP_MILLIS = 250
private const val LINE_MILLIS = 160

/** Field text for stored cents: "3500,00" style, empty when zero (not set). */
internal fun centsFieldText(cents: Long) = if (cents == 0L) "" else BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')

/** Blank means R$ 0,00; anything else must be a strict pt-BR decimal (null = invalid). */
private fun parseRate(text: String): Long? = if (text.isBlank()) 0L else MoneyParser.parseCents(text)

/** Inline error for a salary field; blank is not an error here (callers decide whether blank is allowed). */
internal fun salaryError(text: String): String? = when {
    text.isBlank() || SalaryPlanner.parseMonthlyCents(text) != null -> null
    MoneyParser.parseCents(text) == 0L -> "Informe um valor maior que zero."
    else -> "Valor inválido. Use vírgula para os centavos, como 3.500,00."
}

/**
 * First-run (and "Rever configuração inicial") wizard with one step per page: name, monthly salary, then VR and VT
 * together. The primary button of each step stays disabled until that step is validly filled; every step can also
 * be skipped, which keeps whatever was stored for it. Step and fields survive rotation and process recreation.
 * Everything is computed with integer cents.
 */
@Composable
internal fun OnboardingScreen(
    initialName: String,
    initialSalaryCents: Long,
    initialSettings: BenefitSettings,
    saving: Boolean,
    error: String?,
    onStart: (name: String, salaryCents: Long, vrCents: Long, vtCents: Long, vrWeekdays: Set<DayOfWeek>, vtWeekdays: Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier,
    month: YearMonth = YearMonth.now(),
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var salary by rememberSaveable { mutableStateOf(centsFieldText(initialSalaryCents)) }
    var vr by rememberSaveable { mutableStateOf(centsFieldText(initialSettings.vrDailyCents)) }
    var vt by rememberSaveable { mutableStateOf(centsFieldText(initialSettings.vtDailyCents)) }
    var vrMask by rememberSaveable { mutableIntStateOf(weekdayMask(initialSettings.vrWeekdays)) }
    var vtMask by rememberSaveable { mutableIntStateOf(weekdayMask(initialSettings.vtWeekdays)) }
    val vrDays = weekdaysOfMask(vrMask)
    val vtDays = weekdaysOfMask(vtMask)
    val vrCents = parseRate(vr)
    val vtCents = parseRate(vt)
    val salaryCents = SalaryPlanner.parseMonthlyCents(salary)
    val vrNeedsDays = vrCents != null && vrCents > 0 && vrDays.isEmpty()
    val vtNeedsDays = vtCents != null && vtCents > 0 && vtDays.isEmpty()
    val benefitsFilled = (vrCents != null && vrCents > 0 && vrDays.isNotEmpty()) ||
        (vtCents != null && vtCents > 0 && vtDays.isNotEmpty())
    val benefitsValid = vrCents != null && vtCents != null && !vrNeedsDays && !vtNeedsDays && benefitsFilled
    val total = if (vrCents != null && vtCents != null)
        BenefitCalculator.estimate(month, BenefitSettings(vrCents, vtCents, emptyMap(), vrDays, vtDays)).totalCents
    else null
    val stepValid = when (step) {
        0 -> name.isNotBlank()
        1 -> salaryCents != null
        else -> benefitsValid
    }
    val last = step == STEPS - 1
    val vtFocus = remember { FocusRequester() }

    fun finish(skipBenefits: Boolean) {
        val salaryValue = salaryCents ?: initialSalaryCents
        if (skipBenefits || vrCents == null || vtCents == null)
            onStart(name.trim(), salaryValue, initialSettings.vrDailyCents, initialSettings.vtDailyCents,
                initialSettings.vrWeekdays, initialSettings.vtWeekdays)
        else onStart(name.trim(), salaryValue, vrCents, vtCents, vrDays, vtDays)
    }

    fun primary() {
        if (saving || !stepValid) return
        if (last) finish(skipBenefits = false) else step++
    }

    fun skip() {
        if (saving) return
        when (step) {
            0 -> name = initialName
            1 -> salary = centsFieldText(initialSalaryCents)
        }
        if (last) finish(skipBenefits = true) else step++
    }

    BackHandler(enabled = step > 0 && !saving) { step-- }

    Box(modifier.fillMaxSize().background(FluxoColors.Paper).systemBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize()) {
            // Progress and the back arrow stay pinned at the top, the primary action at the bottom.
            Box(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp)) {
                ProgressHeader(step, onBack = { if (!saving) step-- })
            }
            AnimatedContent(
                targetState = step, modifier = Modifier.weight(1f).fillMaxWidth(), label = "onboardingStep",
                transitionSpec = {
                    val forward = targetState > initialState
                    val offset = tween<IntOffset>(STEP_MILLIS, easing = FastOutSlowInEasing)
                    val alpha = tween<Float>(STEP_MILLIS, easing = FastOutSlowInEasing)
                    (slideInHorizontally(offset) { width -> if (forward) width else -width } + fadeIn(alpha)) togetherWith
                        (slideOutHorizontally(offset) { width -> if (forward) -width else width } + fadeOut(alpha))
                }
            ) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
                    // Centred when the page is shorter than the viewport (tall phones), scrolls when it is taller.
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
                ) {
                    val (title, subtitle) = when (page) {
                        0 -> "Como podemos te chamar?" to "Seu nome aparece na saudação do início e fica só neste aparelho."
                        1 -> "Qual é o seu salário mensal?" to "Com ele, o Planejar mostra quanto da renda do mês já foi gasto."
                        else -> "Vale-refeição e vale-transporte" to "Quanto cai por dia e em quais dias da semana. O Fluxo faz a conta do mês."
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, color = FluxoColors.Ink, modifier = Modifier.semantics { heading() },
                            style = FluxoType.number(26.sp, FontWeight.ExtraBold).copy(letterSpacing = (-0.02).sp, lineHeight = 31.sp))
                        Text(subtitle, color = FluxoColors.Mute,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp))
                    }
                    when (page) {
                        0 -> OutlinedTextField(
                            value = name, onValueChange = { name = it.take(MAX_NAME) },
                            modifier = Modifier.fillMaxWidth().testTag("onboarding.name"),
                            textStyle = FluxoType.number(20.sp, FontWeight.SemiBold).copy(fontFeatureSettings = null),
                            label = { Text("Seu nome") }, singleLine = true, enabled = !saving,
                            shape = FluxoShapes.Ticket, colors = fluxoFieldColors(),
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = FluxoColors.Petrol) },
                            isError = name.isNotEmpty() && name.isBlank(),
                            supportingText = {
                                Text(if (name.isNotEmpty() && name.isBlank()) "Informe um nome." else "Como aparece na saudação.")
                            },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { primary() }),
                        )
                        1 -> {
                            val problem = salaryError(salary)
                            val amountStyle = FluxoType.number(28.sp, FontWeight.ExtraBold)
                            OutlinedTextField(
                                value = salary, onValueChange = { salary = it.take(32) },
                                modifier = Modifier.fillMaxWidth().testTag("onboarding.salary"),
                                textStyle = amountStyle,
                                label = { Text("Salário líquido por mês") }, singleLine = true, enabled = !saving, isError = problem != null,
                                shape = FluxoShapes.Ticket, colors = fluxoFieldColors(),
                                prefix = { Text("R$ ", style = amountStyle, color = FluxoColors.Mute) },
                                supportingText = {
                                    Text(problem ?: "É o líquido que cai na conta todo mês. Fica só neste aparelho.")
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { primary() }),
                            )
                        }
                        else -> {
                            BenefitSection(
                                code = "VR", badge = FluxoColors.Petrol, title = "Vale-refeição (VR)",
                                rate = vr, onRate = { vr = it }, rateInvalid = vrCents == null, rateTag = "onboarding.vrRate",
                                weekdays = vrDays, onWeekdays = { vrMask = weekdayMask(it) }, dayTag = "onboarding.vrDay",
                                needsDays = vrNeedsDays,
                                line = if (vrCents != null) benefitLine(month, vrDays, vrCents) else null, lineTag = "onboarding.vrLine",
                                enabled = !saving, help = "Quanto cai por dia útil.", imeAction = ImeAction.Next,
                                onNext = { vtFocus.requestFocus() }
                            )
                            BenefitSection(
                                code = "VT", badge = FluxoColors.PetrolMid, title = "Vale-transporte (VT)",
                                rate = vt, onRate = { vt = it }, rateInvalid = vtCents == null, rateTag = "onboarding.vtRate",
                                weekdays = vtDays, onWeekdays = { vtMask = weekdayMask(it) }, dayTag = "onboarding.vtDay",
                                needsDays = vtNeedsDays,
                                line = if (vtCents != null) benefitLine(month, vtDays, vtCents) else null, lineTag = "onboarding.vtLine",
                                enabled = !saving, help = "Some ida e volta no valor diário, como 10,80.",
                                imeAction = ImeAction.Done, onDone = { primary() }, focusRequester = vtFocus
                            )
                            Column(
                                Modifier.fillMaxWidth().clip(FluxoShapes.Hero).background(FluxoColors.Hero)
                                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val summary = buildAnnotatedString {
                                    append("Estimativa do mês: ")
                                    withStyle(SpanStyle(fontFamily = FluxoType.Display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)) {
                                        append(total?.let { planningMoney(it) } ?: "—")
                                    }
                                }
                                Box(Modifier.testTag("onboarding.preview").semantics(mergeDescendants = true) {}) {
                                    Crossfade(summary, animationSpec = tween(LINE_MILLIS), label = "monthTotal") { text ->
                                        Text(text, color = FluxoColors.OnHero,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                                    }
                                }
                                Text("É só uma estimativa: feriados e ausências não são considerados.",
                                    color = FluxoColors.OnPetrolSoft, style = FluxoType.Caption.copy(fontSize = 11.5.sp, lineHeight = 15.sp))
                            }
                            if (!benefitsFilled && vrCents != null && vtCents != null && !vrNeedsDays && !vtNeedsDays) {
                                Text("Preencha ao menos um vale para continuar. Quem deixa um vazio não recebe aquele vale.",
                                    color = FluxoColors.Mute, style = FluxoType.Caption.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                                    modifier = Modifier.padding(horizontal = 2.dp))
                            }
                        }
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (error != null) Text(error, color = FluxoColors.Expense, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                FluxoPillButton(
                    if (saving) "Salvando…" else if (last) "Começar" else "Continuar",
                    onClick = { primary() },
                    enabled = stepValid && !saving,
                    modifier = Modifier.fillMaxWidth().testTag(if (last) "onboarding.start" else "onboarding.next")
                )
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(FluxoShapes.Pill)
                        .clickable(enabled = !saving, role = Role.Button, onClick = { skip() })
                        .testTag("onboarding.skipStep").padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (last) "Não recebo VR nem VT" else "Pular esta etapa",
                        color = FluxoColors.Mute.copy(alpha = if (saving) .38f else 1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun fluxoFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = FluxoColors.Soft, unfocusedContainerColor = FluxoColors.Soft,
    disabledContainerColor = FluxoColors.Soft,
    focusedBorderColor = FluxoColors.Petrol, unfocusedBorderColor = FluxoColors.Line,
    focusedTextColor = FluxoColors.Ink, unfocusedTextColor = FluxoColors.Ink,
    focusedLabelColor = FluxoColors.Petrol, unfocusedLabelColor = FluxoColors.Mute,
    focusedPrefixColor = FluxoColors.Mute, unfocusedPrefixColor = FluxoColors.Mute,
    errorContainerColor = FluxoColors.Soft,
)

/** Number text that crossfades when it changes (no bounce, no looping). */
@Composable
private fun SoftText(text: String, tag: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.testTag(tag).semantics(mergeDescendants = true) {}) {
        Crossfade(text, animationSpec = tween(LINE_MILLIS), label = "line") { Text(it, style = style, color = color) }
    }
}

/** Back arrow (steps 2 and 3), "N de 3" and three progress segments. */
@Composable
private fun ProgressHeader(step: Int, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (step > 0) Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClick = onBack).testTag("onboarding.back"),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = FluxoColors.Ink, modifier = Modifier.size(24.dp))
        } else Box(Modifier.height(48.dp))
        Column(
            Modifier.weight(1f).padding(start = if (step > 0) 4.dp else 2.dp, end = 2.dp)
                .testTag("onboarding.progress")
                .semantics(mergeDescendants = true) { contentDescription = "Etapa ${step + 1} de $STEPS" },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("${step + 1} de $STEPS", color = FluxoColors.Mute,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(STEPS) { index ->
                    val fill by animateFloatAsState(if (index <= step) 1f else 0f,
                        tween(STEP_MILLIS, easing = FastOutSlowInEasing), label = "segment")
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(FluxoColors.Line)) {
                        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(FluxoColors.Petrol))
                    }
                }
            }
        }
    }
}

@Composable
private fun BenefitSection(
    code: String, badge: Color, title: String,
    rate: String, onRate: (String) -> Unit, rateInvalid: Boolean, rateTag: String,
    weekdays: Set<DayOfWeek>, onWeekdays: (Set<DayOfWeek>) -> Unit, dayTag: String, needsDays: Boolean,
    line: String?, lineTag: String, enabled: Boolean, help: String,
    imeAction: ImeAction, onDone: () -> Unit = {}, onNext: () -> Unit = {}, focusRequester: FocusRequester? = null,
) {
    FluxoTile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 6.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(badge), contentAlignment = Alignment.Center) {
                Text(code, color = FluxoColors.OnPetrol, maxLines = 1, style = FluxoType.number(14.sp, FontWeight.ExtraBold))
            }
            Text(title, modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        OutlinedTextField(
            value = rate, onValueChange = { onRate(it.take(40)) },
            modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).testTag(rateTag),
            label = { Text("Valor por dia") }, singleLine = true, enabled = enabled, isError = rateInvalid,
            prefix = { Text("R$") },
            supportingText = {
                Text(if (rateInvalid) "Valor inválido. Use vírgula para os centavos, como 39,00." else "$help Deixe vazio se não recebe.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onNext() }),
        )
        Text("Dias em que cai", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 4.dp))
        WeekdayChips(weekdays, onWeekdays, dayTag, enabled = enabled)
        if (needsDays) Text("Escolha ao menos um dia.", color = FluxoColors.Expense,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        if (line != null) SoftText(
            line, lineTag, MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
            FluxoColors.Ink, Modifier.padding(top = 2.dp)
        )
    }
}
