package br.com.fluxo.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

enum class AlertKind { REVIEW_DIGEST, BUDGET_80, BUDGET_OVER, BILL_DUE, UNUSUAL, CAPTURE_DOWN, WEEKLY_SUMMARY }

data class AlertSettings(
    val enabled: Set<AlertKind> = AlertKind.entries.toSet(), val quietStartHour: Int = 22,
    val quietEndHour: Int = 8, val maxPerDay: Int = 3,
)

data class SmartAlert(val key: String, val kind: AlertKind, val title: String, val body: String, val priority: Int)

data class AlertInput(
    val now: LocalDateTime, val zone: ZoneId, val rows: List<Transaction>, val budgets: List<Budget>,
    val recurring: List<RecurringCharge>, val captureEnabled: Boolean, val captureHealthy: Boolean,
    val lastCaptureAt: Long?, val settings: AlertSettings, val sentKeys: Set<String>, val sentToday: Int,
)

object SmartAlerts {
    private const val UNUSUAL_MIN_CENTS = 5_000L
    private const val UNUSUAL_MIN_SAMPLES = 3
    private const val CAPTURE_STALE_MS = 48L * 3_600_000

    private fun brl(cents: Long): String {
        val whole = (cents / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        return "R$ $whole,${(cents % 100).toString().padStart(2, '0')}"
    }

    private fun inQuiet(hour: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /**
     * Pure decision: which alerts to post now. Highest priority first, trimmed to the daily budget left.
     * Capture-down needs a known last capture time (a fresh install has no baseline, so it stays quiet).
     */
    fun evaluate(input: AlertInput): List<SmartAlert> {
        val s = input.settings
        val now = input.now
        if (inQuiet(now.hour, s.quietStartHour, s.quietEndHour)) return emptyList()
        val left = s.maxPerDay - input.sentToday
        if (left <= 0) return emptyList()
        val today = now.toLocalDate()
        val month = YearMonth.from(today)
        val out = mutableListOf<SmartAlert>()
        fun add(key: String, kind: AlertKind, title: String, body: String, priority: Int) {
            if (kind in s.enabled && key !in input.sentKeys) out += SmartAlert(key, kind, title, body, priority)
        }

        val pending = input.rows.count { it.status == ReviewStatus.REVIEW && it.transferId == null }
        if (now.hour >= 19 && pending > 0) {
            add("review:$today", AlertKind.REVIEW_DIGEST, "Revisão pendente",
                if (pending == 1) "1 lançamento aguarda revisão." else "$pending lançamentos aguardam revisão.", 40)
        }

        BudgetPace.overruns(input.rows, input.budgets, month, input.zone).forEach {
            val over = it.spentCents >= it.limitCents
            add("${if (over) "budgetover" else "budget80"}:$month:${it.category.name}",
                if (over) AlertKind.BUDGET_OVER else AlertKind.BUDGET_80,
                if (over) "Orçamento estourado" else "Orçamento em 80%",
                "${it.category.label}: ${brl(it.spentCents)} de ${brl(it.limitCents)}.", if (over) 90 else 70)
        }

        input.recurring.filter { it.nextDue == today.plusDays(1) }.forEach {
            add("bill:${it.key}:${it.nextDue}", AlertKind.BILL_DUE, "Conta vence amanhã", "${it.label}: ${brl(it.amountCents)}.", 80)
        }

        val since = today.minusDays(90)
        val history = input.rows.filter { it.countableExpense() && it.category != Category.UNCATEGORIZED }
        history.filter { it.localDate(input.zone) == today && it.amountCents!! >= UNUSUAL_MIN_CENTS }.forEach { row ->
            val prior = history.filter {
                it.id != row.id && it.category == row.category && it.occurredAt < row.occurredAt && it.localDate(input.zone) >= since
            }.map { it.amountCents!! }
            if (prior.size >= UNUSUAL_MIN_SAMPLES && row.amountCents!! > median(prior) * 3) {
                add("unusual:${row.id}", AlertKind.UNUSUAL, "Gasto incomum",
                    "${row.category.label}: ${brl(row.amountCents!!)}, bem acima do habitual.", 50)
            }
        }

        val last = input.lastCaptureAt
        val nowMs = now.atZone(input.zone).toInstant().toEpochMilli()
        if (input.captureEnabled && !input.captureHealthy && last != null && nowMs - last >= CAPTURE_STALE_MS) {
            add("capturedown:$today", AlertKind.CAPTURE_DOWN, "Captura parada",
                "Nenhuma notificação capturada há mais de 48 horas. Verifique o acesso.", 60)
        }

        if (today.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= 18) {
            val week = input.rows.filter { it.localDate(input.zone) in today.minusDays(6)..today }
            val spent = week.filter { it.countableExpense() }.fold(0L) { a, r -> Math.addExact(a, r.amountCents!!) }
            val earned = week.filter { it.countableIncome() }.fold(0L) { a, r -> Math.addExact(a, r.amountCents!!) }
            add("weekly:$today", AlertKind.WEEKLY_SUMMARY, "Resumo da semana",
                "Saiu ${brl(spent)} e entrou ${brl(earned)} nos últimos 7 dias.", 30)
        }

        return out.sortedWith(compareByDescending<SmartAlert> { it.priority }.thenBy { it.key }).take(left)
    }
}
