package br.com.fluxo.app

import br.com.fluxo.core.BenefitDays
import br.com.fluxo.core.BenefitSettings
import java.time.YearMonth
import kotlinx.coroutines.runBlocking

/**
 * Test-only helper. The UI tests drive the real application repository on a disposable emulator; onboarding is
 * a normal first-run screen, so they mark it done through the public repository API before they need the tabs
 * and put the stored values back afterwards. Production code has no test flag.
 */
internal class ProfileSnapshot(val done: Boolean, val name: String, val benefits: BenefitSettings, val salaryCents: Long = 0)

internal object OnboardingTestSupport {
    fun snapshot(repository: LedgerRepository) =
        ProfileSnapshot(repository.onboardingDone.value, repository.profileName.value, repository.benefits.value,
            repository.salaryMonthlyCents.value)

    /** Marks onboarding as done and returns what was stored before so it can be restored. */
    fun skip(repository: LedgerRepository): ProfileSnapshot = snapshot(repository).also {
        if (!it.done) runBlocking { repository.setOnboardingDone(true) }
    }

    fun restore(repository: LedgerRepository, snapshot: ProfileSnapshot) = runBlocking {
        val month = YearMonth.now()
        val stored = snapshot.benefits
        repository.saveBenefits(month, stored.vrDailyCents, stored.vtDailyCents,
            stored.overrides[month] ?: BenefitDays(), stored.vrWeekdays, stored.vtWeekdays)
        repository.setProfileName(snapshot.name)
        repository.setSalaryMonthlyCents(snapshot.salaryCents)
        repository.setOnboardingDone(snapshot.done)
    }
}
