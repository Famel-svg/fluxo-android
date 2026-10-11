package br.com.fluxo.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.fluxo.core.BenefitCalculator
import br.com.fluxo.core.BenefitSettings
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.YearMonth

private const val MAX_NAME = 40

private fun rateText(cents: Long) = if (cents == 0L) "" else BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')

/** Blank means R$ 0,00; anything else must be a strict pt-BR decimal (null = invalid). */
private fun parseRate(text: String): Long? = if (text.isBlank()) 0L else BenefitCalculator.parseDailyCents(text)

/**
 * First-run (and "Rever configuração inicial") page: name, VR and VT value per day with the weekdays they
 * are deposited, and a live estimate for [month]. Everything is computed with integer cents.
 */
@Composable
internal fun OnboardingScreen(
    initialName: String,
    initialSettings: BenefitSettings,
    saving: Boolean,
    error: String?,
    onStart: (name: String, vrCents: Long, vtCents: Long, vrWeekdays: Set<DayOfWeek>, vtWeekdays: Set<DayOfWeek>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    month: YearMonth = YearMonth.now(),
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var vr by rememberSaveable { mutableStateOf(rateText(initialSettings.vrDailyCents)) }
    var vt by rememberSaveable { mutableStateOf(rateText(initialSettings.vtDailyCents)) }
    var vrMask by rememberSaveable { mutableIntStateOf(weekdayMask(initialSettings.vrWeekdays)) }
    var vtMask by rememberSaveable { mutableIntStateOf(weekdayMask(initialSettings.vtWeekdays)) }
    val vrDays = weekdaysOfMask(vrMask)
    val vtDays = weekdaysOfMask(vtMask)
    val vrCents = parseRate(vr)
    val vtCents = parseRate(vt)
    val valid = vrCents != null && vtCents != null
    val total = if (vrCents != null && vtCents != null)
        BenefitCalculator.estimate(month, BenefitSettings(vrCents, vtCents, emptyMap(), vrDays, vtDays)).totalCents
    else null

    Box(modifier.fillMaxSize().background(FluxoColors.Paper).systemBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().clip(FluxoShapes.Hero).background(FluxoColors.Petrol)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Configuração inicial", color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                Text("Vamos começar", color = Color.White, modifier = Modifier.semantics { heading() },
                    style = FluxoType.number(28.sp, FontWeight.ExtraBold).copy(letterSpacing = (-0.02).sp))
                Text("Diga como te chamar e quanto você recebe de VR e VT. O Fluxo faz a conta do mês.",
                    color = FluxoColors.OnPetrolSoft, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp))
            }

            FluxoTile(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(MAX_NAME) },
                    modifier = Modifier.fillMaxWidth().testTag("onboarding.name"),
                    label = { Text("Como podemos te chamar?") }, singleLine = true, enabled = !saving,
                    supportingText = { Text("Opcional. O nome fica só neste aparelho.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }

            BenefitSection(
                code = "VR", badge = FluxoColors.Petrol, title = "Vale-refeição (VR)",
                rate = vr, onRate = { vr = it }, rateInvalid = vrCents == null, rateTag = "onboarding.vrRate",
                weekdays = vrDays, onWeekdays = { vrMask = weekdayMask(it) }, dayTag = "onboarding.vrDay",
                line = if (vrCents != null) benefitLine(month, vrDays, vrCents) else null, lineTag = "onboarding.vrLine",
                enabled = !saving,
                help = "Quanto cai por dia útil."
            )
            BenefitSection(
                code = "VT", badge = FluxoColors.PetrolMid, title = "Vale-transporte (VT)",
                rate = vt, onRate = { vt = it }, rateInvalid = vtCents == null, rateTag = "onboarding.vtRate",
                weekdays = vtDays, onWeekdays = { vtMask = weekdayMask(it) }, dayTag = "onboarding.vtDay",
                line = if (vtCents != null) benefitLine(month, vtDays, vtCents) else null, lineTag = "onboarding.vtLine",
                enabled = !saving,
                help = "Some ida e volta no valor diário, como 10,80."
            )

            FluxoTile(Modifier.fillMaxWidth(), role = FluxoTileRole.Accent) {
                Text(
                    buildAnnotatedString {
                        append("Estimativa do mês: ")
                        withStyle(SpanStyle(fontFamily = FluxoType.Display, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)) {
                            append(total?.let { planningMoney(it) } ?: "—")
                        }
                    },
                    modifier = Modifier.testTag("onboarding.preview"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = FluxoColors.Ink
                )
                Text("É só uma estimativa: feriados e ausências não são considerados.",
                    color = FluxoColors.Mute, style = FluxoType.Caption.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                    modifier = Modifier.padding(top = 2.dp))
            }

            if (error != null) Text(error, color = FluxoColors.Expense, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })

            FluxoPillButton(
                if (saving) "Salvando…" else "Começar",
                onClick = {
                    if (vrCents != null && vtCents != null) onStart(name.trim(), vrCents, vtCents, vrDays, vtDays)
                },
                enabled = valid && !saving, modifier = Modifier.fillMaxWidth().testTag("onboarding.start")
            )
            FluxoPillButton(
                "Pular por enquanto", onClick = onSkip, style = FluxoButtonStyle.Quiet,
                enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("onboarding.skip")
            )
        }
    }
}

@Composable
private fun BenefitSection(
    code: String, badge: Color, title: String,
    rate: String, onRate: (String) -> Unit, rateInvalid: Boolean, rateTag: String,
    weekdays: Set<DayOfWeek>, onWeekdays: (Set<DayOfWeek>) -> Unit, dayTag: String,
    line: String?, lineTag: String, enabled: Boolean, help: String,
) {
    FluxoTile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 6.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(badge), contentAlignment = Alignment.Center) {
                Text(code, color = Color.White, maxLines = 1, style = FluxoType.number(14.sp, FontWeight.ExtraBold))
            }
            Text(title, modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        OutlinedTextField(
            value = rate, onValueChange = { onRate(it.take(40)) },
            modifier = Modifier.fillMaxWidth().testTag(rateTag),
            label = { Text("Valor por dia") }, singleLine = true, enabled = enabled, isError = rateInvalid,
            prefix = { Text("R$") },
            supportingText = {
                Text(if (rateInvalid) "Valor inválido. Use vírgula para os centavos, como 39,00." else "$help Deixe vazio para R$ 0,00.")
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Text("Dias em que cai", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), modifier = Modifier.padding(top = 4.dp))
        WeekdayChips(weekdays, onWeekdays, dayTag, enabled = enabled)
        if (line != null) Text(
            line, modifier = Modifier.padding(top = 2.dp).testTag(lineTag),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
            color = FluxoColors.Ink
        )
    }
}
