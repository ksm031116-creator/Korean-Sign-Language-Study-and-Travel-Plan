package app.signkorean.domain.content

/**
 * 앱이 그릴 수 있는 표지판 픽토그램 ID 목록 (PRD §8.3, v2.6).
 * 콘텐츠 팩이 쓸 수 있는 어휘이며, 그림(벡터)은 UI 가 ID 마다 하나씩 가진다.
 * ID 를 더하면 UI 의 그림 매핑 테스트가 빠진 그림을 잡는다.
 */
object Pictograms {
    val ALL: Set<PictogramId> = listOf(
        "restroom", "men", "women", "accessible", "exit", "elevator", "escalator",
        "stairs", "subway", "bus", "noSmoking", "noPhoto", "wifi", "pharmacy",
    ).map(::PictogramId).toSet()
}
