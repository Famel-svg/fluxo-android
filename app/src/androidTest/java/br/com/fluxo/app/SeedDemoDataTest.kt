package br.com.fluxo.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.fluxo.core.Budget
import br.com.fluxo.core.Category
import br.com.fluxo.core.Direction
import br.com.fluxo.core.PaymentMethod
import br.com.fluxo.core.ReviewStatus
import br.com.fluxo.core.Transaction
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test of behaviour: an opt-in fixture loader for visual checks, run by tools/check.ps1.
 * It REPLACES the ledger of the installed app with synthetic rows through the public repository API,
 * so it keeps working across schema changes. Runs only with seedDemo=true on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class SeedDemoDataTest {
    @Test fun replaceLedgerWithSyntheticDemoData() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in only: pass seedDemo=true", arguments.getString("seedDemo") == "true")
        assumeTrue("Demo data is only loaded on an emulator", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val repository = (context as FluxoApplication).repository
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun at(day: LocalDate, hour: Int, minute: Int = 0) =
            day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
        // Keep everything inside the current month so monthly totals do not depend on the run date.
        fun daysAgo(days: Long) = today.minusDays(days).let { if (YearMonth.from(it) == YearMonth.from(today)) it else today.withDayOfMonth(1) }

        repository.clear()
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        repository.completeOnboarding("Rafael", 3900, 1080, weekdays, weekdays - DayOfWeek.FRIDAY, 350_000)

        val nubank = "com.nu.production"; val c6 = "com.c6bank.app"; val itau = "com.itau"
        var index = 0
        suspend fun add(
            description: String, cents: Long, direction: Direction, method: PaymentMethod, bank: String,
            category: Category, time: Long, automatic: Boolean = true,
            status: ReviewStatus = ReviewStatus.CONFIRMED, reason: String = "",
        ) {
            val id = index++
            repository.save(Transaction("demo-$id", if (automatic) "demo-source-$id" else null, bank, cents, direction,
                method, status, time, description, reason, category))
        }
        add("Padaria Do Zé", 2590, Direction.EXPENSE, PaymentMethod.MEAL_VOUCHER, nubank, Category.FOOD, at(today, 8, 12))
        add("Pix recebido Maria Silva", 15000, Direction.INCOME, PaymentMethod.PIX, c6, Category.UNCATEGORIZED, at(today, 7, 40))
        add("Farmácia Z", 1890, Direction.EXPENSE, PaymentMethod.DEBIT_CARD, nubank, Category.HEALTH, at(today, 8, 40), automatic = false)
        add("Uber", 1840, Direction.EXPENSE, PaymentMethod.CREDIT_CARD, itau, Category.TRANSPORT, at(daysAgo(1), 19, 3))
        add("Mercado X", 15720, Direction.EXPENSE, PaymentMethod.DEBIT_CARD, c6, Category.FOOD, at(daysAgo(1), 11))
        add("Restaurante", 12000, Direction.EXPENSE, PaymentMethod.MEAL_VOUCHER, nubank, Category.FOOD, at(daysAgo(2), 20))
        add("Cinema", 13000, Direction.EXPENSE, PaymentMethod.DEBIT_CARD, nubank, Category.LEISURE, at(daysAgo(3), 21))
        add("Posto", 4480, Direction.EXPENSE, PaymentMethod.DEBIT_CARD, itau, Category.TRANSPORT, at(daysAgo(4), 18))
        add("Feira", 21235, Direction.EXPENSE, PaymentMethod.PIX, c6, Category.FOOD, at(daysAgo(5), 10))
        add("Lanche", 8640, Direction.EXPENSE, PaymentMethod.DEBIT_CARD, nubank, Category.FOOD, at(daysAgo(6), 13))
        add("Salário", 350_000, Direction.INCOME, PaymentMethod.PIX, itau, Category.SALARY, at(daysAgo(6), 9))
        add("Aluguel", 120_000, Direction.EXPENSE, PaymentMethod.PIX, itau, Category.HOUSING, at(daysAgo(9), 10))
        add("Metrô e ônibus", 25550, Direction.EXPENSE, PaymentMethod.DEBIT_CARD, nubank, Category.TRANSPORT, at(daysAgo(10), 8))
        add("Internet e luz", 126_345, Direction.EXPENSE, PaymentMethod.DEBIT_CARD, c6, Category.SERVICES, at(daysAgo(7), 10))
        add("Movimentação detectada", 12000, Direction.EXPENSE, PaymentMethod.UNKNOWN, itau, Category.UNCATEGORIZED,
            at(today, 6, 30), status = ReviewStatus.REVIEW, reason = "Meio de pagamento não identificado; confirme a classificação.")
        add("Movimentação detectada", 90000, Direction.UNKNOWN, PaymentMethod.UNKNOWN, nubank, Category.UNCATEGORIZED,
            at(daysAgo(1), 22), status = ReviewStatus.REVIEW, reason = "Fatura, estorno ou devolução exige classificação manual.")
        add("Movimentação detectada", 7500, Direction.EXPENSE, PaymentMethod.PIX, c6, Category.UNCATEGORIZED,
            at(daysAgo(1), 16), status = ReviewStatus.REVIEW, reason = "Notificação atualizada; confira a movimentação.")
        listOf(364_000L, 391_000L, 448_000L, 372_000L, 421_000L).reversed().forEachIndexed { back, total ->
            val month = YearMonth.from(today).minusMonths(back + 1L)
            add("Gastos do mês", total, Direction.EXPENSE, PaymentMethod.DEBIT_CARD, nubank, Category.OTHER, at(month.atDay(15), 12))
        }
        val month = YearMonth.from(today).toString()
        repository.saveBudget(Budget(month, Category.FOOD, 100_000))
        repository.saveBudget(Budget(month, Category.TRANSPORT, 30_000))
        repository.saveBudget(Budget(month, Category.LEISURE, 40_000))
        repository.saveBudget(Budget(month, Category.HOUSING, 120_000))

        // Optional appearance for screenshots: seedTheme=light|dark|system.
        when (arguments.getString("seedTheme")) {
            "dark" -> repository.setThemeMode(ThemeMode.DARK)
            "light" -> repository.setThemeMode(ThemeMode.LIGHT)
            "system" -> repository.setThemeMode(ThemeMode.SYSTEM)
        }

        repository.refresh()
        assertEquals(index, repository.transactions.value.size)
    }
}
