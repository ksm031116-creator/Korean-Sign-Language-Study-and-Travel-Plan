package app.signkorean.domain.common

/**
 * 새 엔티티(세션·여행·일정 등)의 ID 포트. 무작위 값을 도메인 안에서 만들지 않는다 (설계서 원칙 5).
 * 콘텐츠 ID(카드·장소)는 콘텐츠 팩에 고정돼 있으므로 이 포트를 쓰지 않는다.
 */
fun interface IdGenerator {
    fun next(): String
}
