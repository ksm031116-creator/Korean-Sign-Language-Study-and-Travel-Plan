package app.signkorean.domain.onboarding

/** 첫 실행 안내(SC01)와 스와이프 시연(UX11 ③)을 봤는지. 구현은 DataStore. 학습 기록 초기화와 무관하다. */
interface OnboardingStore {
    suspend fun firstRunDone(): Boolean
    suspend fun markFirstRunDone()
    suspend fun coachSeen(): Boolean
    suspend fun markCoachSeen()

    /** 설정의 "다시 보기". */
    suspend fun resetCoach()
}
