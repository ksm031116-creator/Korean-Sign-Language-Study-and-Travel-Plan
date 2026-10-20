package app.signkorean.domain.content

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.common.LocalizedText
import app.signkorean.domain.location.Coordinate

/** 학습 기록·북마크가 이 값으로 저장된다. 콘텐츠 팩에 명시된 값이며 배열 순서로 계산하지 않는다 (C-08). */
@JvmInline value class CardId(val value: String)

@JvmInline value class PlaceId(val value: String)

@JvmInline value class AreaId(val value: String)

/** 표지판 픽토그램 세트의 ID. 그림 자체는 앱(UI) 이 가진다. */
@JvmInline value class PictogramId(val value: String)

/** 발음 음성 파일. [path] 는 콘텐츠 팩 기준 상대 경로. */
@JvmInline value class AudioClip(val path: String)

sealed interface CardKind {
    /** 기초 표지판. 장소와 연결되지 않는다 (FR31). */
    data class Basic(val groupId: String) : CardKind

    /** 장소 유형의 간판. 같은 유형의 장소와 연결된다. */
    data class PlaceSign(val typeId: String) : CardKind
}

data class SignCard(
    val id: CardId,
    val kind: CardKind,
    /** 학습 대상. 번역하지 않는다. */
    val koText: String,
    /** 국립국어원 로마자 표기 (FR28). */
    val romanization: String,
    /** 언어별 발음 보조. ja = 카타카나. */
    val reading: LocalizedText,
    /** 언어별 뜻. 직역하지 않는다 (설계서 §8.3). */
    val meaning: LocalizedText,
    /** 언어별 문화 설명. */
    val note: LocalizedText,
    /** 뒷면에만 그린다. 앞면에는 그리지 않는다 (PRD FR02·§8.3). */
    val pictogram: PictogramId?,
    /** 표지판 판 색 (#rrggbb). 실제 표지판 색 관례라 학습 내용의 일부다. */
    val plateColor: String,
    /** null 이면 음성 준비 중. */
    val audio: AudioClip?,
    /** 원문·표기·기준 언어 문구의 검수 완료 여부. */
    val reviewed: Boolean,
) {
    /** 출제 가능 여부. 언어와 무관하다 ([CardActivation]). */
    val active: Boolean get() = CardActivation.reasons(this).isEmpty()
}

data class Area(
    val id: AreaId,
    val koName: String,
    val name: LocalizedText,
    val active: Boolean,
    /** 한 줄 성격(예: History & quiet lanes). */
    val tag: LocalizedText = LocalizedText.EMPTY,
    val description: LocalizedText = LocalizedText.EMPTY,
    /** 사진이 없을 때 쓰는 지역 색 (#rrggbb). */
    val color: String = "#2f6f5e",
)

enum class GroupKind { Basic, PlaceType }

/** 카드 묶음: 기초 표지판 분류 또는 장소 유형. 카드의 CardKind 가 이 id 를 가리킨다. */
data class SignGroup(
    val id: String,
    val kind: GroupKind,
    val koLabel: String,
    val label: LocalizedText,
    val color: String,
)

data class Place(
    val id: PlaceId,
    val areaId: AreaId,
    val typeId: String,
    val koName: String,
    val name: LocalizedText,
    val address: LocalizedText,
    val coordinate: Coordinate,
    /** null 이면 유형의 모든 간판. 값이 있으면 그 단어의 카드에만 연결한다. */
    val signWords: Set<String>?,
    /** null 이면 자리 표시. */
    val photo: PlacePhoto?,
    /** 개별 상점을 대신하는 예시 장소. 실제 상호처럼 보이지 않게 "Example" 로 표시한다 (PRD §2.2). */
    val isExample: Boolean,
)

data class PlacePhoto(
    /**
     * https:// 원격 사진, 또는 asset://<assets 기준 경로> 앱 내장 사진.
     * asset:// 은 콘텐츠 팩의 약속이며, 이미지 로더에 넘기기 전에 UI 쪽에서 file:///android_asset/ 으로 바꾼다(Phase 14).
     */
    val url: String,
    val license: PhotoLicense,
    /** 사람이 읽는 출처 (예: "한국관광공사", "Wikimedia Commons"). */
    val source: String,
    val sourceUrl: String?,
)

/** 허용 라이선스만 타입으로 둔다. 변경 금지·비영리·동일조건 라이선스는 콘텐츠 팩에 들어올 수 없다 (C-10). */
sealed interface PhotoLicense {
    /** 공공누리 제1유형: 출처 표시. */
    data object KoglType1 : PhotoLicense

    /** 공공누리 제3유형: 출처 표시 + 변경 금지. 자르거나 필터를 씌우지 않는다. */
    data object KoglType3 : PhotoLicense

    data object Cc0 : PhotoLicense

    /** CC BY: 저작자와 라이선스 버전을 함께 표시해야 한다. */
    data class CcBy(val author: String, val version: String) : PhotoLicense

    /** 변경 금지 여부. UI 가 사진을 자르지 않고 원본 비율로 보여 줘야 하는지 판단한다. */
    val noDerivatives: Boolean get() = this == KoglType3
}

/** 언어별 콘텐츠 검수 상태. 원어민 검수 전에는 Draft 이고 출시 빌드에 들어갈 수 없다 (설계서 §8.8). */
enum class ReviewStatus { Draft, Reviewed }

/**
 * 앱에 들어가는 콘텐츠 전체. [version] 은 내용 해시.
 * [locales] 는 이 팩이 설명 언어로 제공하는 언어와 검수 상태다. 앱이 고를 수 있는 언어 목록은
 * 코드가 아니라 이 값에서 온다(L10N-08: 새 언어를 코드 변경 없이 추가).
 */
data class ContentPack(
    val version: String,
    val locales: Map<LocaleTag, ReviewStatus>,
    val areas: List<Area>,
    val groups: List<SignGroup> = emptyList(),
    val cards: List<SignCard>,
    val places: List<Place>,
) {
    /** 언어 선택 화면에 보여 줄 언어. 출시 빌드는 검수 완료 언어만, 디버그는 초안도 포함한다. */
    fun selectableLocales(includeDrafts: Boolean): List<LocaleTag> =
        locales.filter { (_, status) -> includeDrafts || status == ReviewStatus.Reviewed }.keys.toList()

    /** 카드가 속한 묶음. */
    fun groupOf(card: SignCard): SignGroup? = when (val k = card.kind) {
        is CardKind.Basic -> groups.firstOrNull { it.kind == GroupKind.Basic && it.id == k.groupId }
        is CardKind.PlaceSign -> groups.firstOrNull { it.kind == GroupKind.PlaceType && it.id == k.typeId }
    }
}
