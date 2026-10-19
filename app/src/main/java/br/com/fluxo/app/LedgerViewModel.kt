package br.com.fluxo.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.fluxo.core.BenefitDays
import br.com.fluxo.core.Budget
import br.com.fluxo.core.Account
import br.com.fluxo.core.Category
import br.com.fluxo.core.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.YearMonth

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as FluxoApplication).repository
    val transactions = repository.transactions
    val accounts = repository.accounts
    val budgets = repository.budgets
    val benefits = repository.benefits
    val profileName = repository.profileName
    val onboardingDone = repository.onboardingDone
    val themeMode = repository.themeMode
    val salaryMonthlyCents = repository.salaryMonthlyCents
    val captureEnabled = repository.captureEnabled
    val allowOtherApps = repository.allowOtherApps
    val enabledBanks = repository.enabledBanks
    val captureError = repository.captureError
    val triageEnabled = repository.triageEnabled
    val disclosureAccepted = repository.disclosureAccepted
    val triage = repository.triage
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
    fun saveBenefits(
        month: YearMonth, vr: Long, vt: Long, days: BenefitDays,
        vrWeekdays: Set<DayOfWeek>, vtWeekdays: Set<DayOfWeek>, onSuccess: () -> Unit,
    ) = edit(onSuccess) { repository.saveBenefits(month, vr, vt, days, vrWeekdays, vtWeekdays) }
    fun completeOnboarding(
        name: String, salaryMonthlyCents: Long, vr: Long, vt: Long, vrWeekdays: Set<DayOfWeek>, vtWeekdays: Set<DayOfWeek>,
        onSuccess: () -> Unit = {},
    ) = edit(onSuccess) { repository.completeOnboarding(name, vr, vt, vrWeekdays, vtWeekdays, salaryMonthlyCents) }
    fun reopenOnboarding() = edit({}) { repository.setOnboardingDone(false) }
    fun saveProfile(name: String, salaryMonthlyCents: Long, onSuccess: () -> Unit) =
        edit(onSuccess) { repository.saveProfile(name, salaryMonthlyCents) }
    fun setThemeMode(mode: ThemeMode) = execute { repository.setThemeMode(mode) }
    fun resetEditError() { _editError.value = null }
    fun clear() = execute { repository.clear() }
    fun capture(enabled: Boolean) = execute { repository.setCaptureEnabled(enabled) }
    fun acceptDisclosure() = execute { repository.setDisclosureAccepted(true) }
    fun allowOtherApps(enabled: Boolean) = execute { repository.setAllowOtherApps(enabled) }
    fun bank(packageName: String, enabled: Boolean) = execute { repository.setBankEnabled(packageName, enabled) }
}
