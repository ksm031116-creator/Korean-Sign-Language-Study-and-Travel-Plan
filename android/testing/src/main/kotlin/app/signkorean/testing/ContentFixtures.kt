package app.signkorean.testing

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.common.LocalizedText
import app.signkorean.domain.content.Area
import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.PictogramId
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.content.ReviewStatus
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.location.Coordinate

/** 테스트용 콘텐츠. 기본값은 "활성 카드가 되는 최소 조건"을 채운다. */
object ContentFixtures {

    val EN = LocaleTag("en")
    val JA = LocaleTag("ja")

    fun en(text: String) = LocalizedText(mapOf(EN to text))

    fun card(
        id: String,
        koText: String = "단어-$id",
        kind: CardKind = CardKind.PlaceSign("market"),
        romanization: String = "roman",
        meaning: LocalizedText = en("meaning"),
        note: LocalizedText = en("note"),
        pictogram: PictogramId? = null,
        reviewed: Boolean = true,
    ) = SignCard(
        id = CardId(id),
        kind = kind,
        koText = koText,
        romanization = romanization,
        reading = LocalizedText.EMPTY,
        meaning = meaning,
        note = note,
        pictogram = pictogram,
        plateColor = "#2f6fb0",
        audio = null,
        reviewed = reviewed,
    )

    fun basic(id: String, koText: String = "단어-$id", group: String = "restroom") =
        card(id, koText, kind = CardKind.Basic(group))

    fun place(
        id: String,
        typeId: String = "market",
        signWords: Set<String>? = null,
        areaId: String = "myeongdong",
        name: String = "Place $id",
        isExample: Boolean = false,
    ) = Place(
        id = PlaceId(id),
        areaId = AreaId(areaId),
        typeId = typeId,
        koName = "장소-$id",
        name = en(name),
        address = en("1 Street, Seoul"),
        coordinate = Coordinate(37.56, 126.98),
        signWords = signWords,
        photo = null,
        isExample = isExample,
    )

    fun area(id: String = "myeongdong") = Area(AreaId(id), "명동", en("Myeongdong"), active = true)

    fun pack(
        cards: List<SignCard> = emptyList(),
        places: List<Place> = emptyList(),
        areas: List<Area> = listOf(area()),
        locales: Map<LocaleTag, ReviewStatus> = mapOf(EN to ReviewStatus.Reviewed),
    ) = ContentPack(version = "test", locales = locales, areas = areas, cards = cards, places = places)
}
