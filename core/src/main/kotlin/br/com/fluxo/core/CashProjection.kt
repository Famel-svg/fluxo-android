package br.com.fluxo.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class ProjectedItem(val date: LocalDate, val label: String, val amountCents: Long, val direction: Direction)
data class Projection(val startNetCents: Long, val items: List<ProjectedItem>, val endNetCents: Long)

object CashProjection {
    /**
     * Starts from this month's confirmed net (income minus bank-leaving expenses: credit and vouchers excluded), then adds
     * recurring charges due within [days] (overdue ones land on today) and the expected salary unless this month's was received.
     */
    fun project(
        rows: List<Transaction>, recurring: List<RecurringCharge>, salaryMonthlyCents: Long,
        salaryDay: Int = 5, today: LocalDate, zone: ZoneId, days: Int = 30,
    ): Projection {
        val month = YearMonth.from(today)
        val limit = today.plusDays(days.toLong())
        var net = 0L
        var salaryReceived = false
        rows.forEach {
            if (YearMonth.from(it.localDate(zone)) != month) return@forEach
            if (it.countableIncome()) {
                net = Math.addExact(net, it.amountCents!!)
                if (it.category == Category.SALARY) salaryReceived = true
            } else if (it.countableExpense() && it.method.affectsCash) net = Math.subtractExact(net, it.amountCents!!)
        }
        val items = mutableListOf<ProjectedItem>()
        recurring.filter { it.affectsCash }.forEach {
            if (!it.nextDue.isAfter(limit)) items += ProjectedItem(maxOf(it.nextDue, today), it.label, it.amountCents, Direction.EXPENSE)
        }
        if (salaryMonthlyCents > 0) {
            listOf(month, month.plusMonths(1)).forEach { m ->
                val date = m.atDay(salaryDay.coerceIn(1, m.lengthOfMonth()))
                if (date >= today && !date.isAfter(limit) && !(m == month && salaryReceived)) {
                    items += ProjectedItem(date, "Salário previsto", salaryMonthlyCents, Direction.INCOME)
                }
            }
        }
        items.sortWith(compareBy({ it.date }, { it.direction.ordinal }, { it.label }))
        val end = items.fold(net) { acc, item ->
            if (item.direction == Direction.INCOME) Math.addExact(acc, item.amountCents) else Math.subtractExact(acc, item.amountCents)
        }
        return Projection(net, items, end)
    }
}
