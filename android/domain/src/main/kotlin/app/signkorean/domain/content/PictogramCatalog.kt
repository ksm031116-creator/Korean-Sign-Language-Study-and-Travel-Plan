package app.signkorean.domain.content

/**
 * 앱이 그릴 수 있는 표지판 픽토그램 ID 목록 (PRD §8.3, v2.6).
 * 콘텐츠 팩이 쓸 수 있는 어휘이며, 그림(벡터)은 UI 가 ID 마다 하나씩 가진다.
 * 날것의 Set 대신 이 타입으로 주입해서, 조립부 검사가 다른 Set 의존성을 놓치지 않게 한다.
 */
class PictogramCatalog(val ids: Set<PictogramId>) {

    operator fun contains(id: PictogramId): Boolean = id in ids

    companion object {
        /** 앱에 들어간 픽토그램. ID 를 더하면 UI 의 그림 매핑 테스트가 빠진 그림을 잡는다. */
        val BUNDLED = PictogramCatalog(
            listOf(
                "restroom", "men", "women", "accessible", "nursing", "babyChange", "wasteBin",
                "exit", "elevator", "escalator", "stairs", "info", "lockers", "drinkingWater",
                "subway", "bus", "taxi", "airport", "parking", "crossing", "transfer",
                "noSmoking", "noPhoto", "noEntry", "noPets", "noFood", "caution", "fireExtinguisher",
                "wifi", "pharmacy", "atm", "phoneCharging", "smokingRoom", "recycling", "police",
            ).map(::PictogramId).toSet(),
        )
    }
}
