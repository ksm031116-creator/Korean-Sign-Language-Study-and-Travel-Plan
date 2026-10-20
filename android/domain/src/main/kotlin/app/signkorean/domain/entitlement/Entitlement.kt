package app.signkorean.domain.entitlement

/** 구매 권한. 두 가지뿐이다 (PRD §3.4). 결제 진행 상태와 따로 둔다 (설계서 §6.4). */
enum class Entitlement { Free, Paid }

/** 지금의 권한. 결제 어댑터(Phase 12)가 마지막으로 확인된 값을 돌려준다. */
fun interface EntitlementSource {
    suspend fun current(): Entitlement
}
