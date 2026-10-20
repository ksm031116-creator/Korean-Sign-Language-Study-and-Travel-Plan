package app.signkorean.testing

import app.signkorean.domain.onboarding.OnboardingStore

class FakeOnboardingStore(
    @Volatile private var firstRun: Boolean = false,
    @Volatile private var coach: Boolean = false,
) : OnboardingStore {
    override suspend fun firstRunDone() = firstRun
    override suspend fun markFirstRunDone() { firstRun = true }
    override suspend fun coachSeen() = coach
    override suspend fun markCoachSeen() { coach = true }
    override suspend fun resetCoach() { coach = false }
}
