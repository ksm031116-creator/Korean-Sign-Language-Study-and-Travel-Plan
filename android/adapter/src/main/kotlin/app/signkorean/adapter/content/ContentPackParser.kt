package app.signkorean.adapter.content

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.common.LocalizedText
import app.signkorean.domain.content.Area
import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.AudioClip
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.PhotoLicense
import app.signkorean.domain.content.PictogramId
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.content.PlacePhoto
import app.signkorean.domain.content.ReviewStatus
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.location.Coordinate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** 콘텐츠 팩 형식이 틀렸다. [message] 에 문제가 있는 위치(예: cards[3].romanization)를 담는다. */
class ContentPackFormatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * content-pack.json (스키마 1) → [ContentPack]. 형식만 검사하고 내용 규칙은 ContentValidator 가 본다.
 * 라이선스는 허용 목록만 읽는다. 그 밖의 값은 팩 전체를 거부한다 (C-10).
 */
class ContentPackParser {

    fun parse(json: String): ContentPack {
        val root = try {
            Json.parseToJsonElement(json)
        } catch (e: SerializationException) {
            throw ContentPackFormatException("JSON 이 아니다: ${e.message}", e)
        }
        return Node(root, "").run {
            val schema = field("schemaVersion").int()
            if (schema != SCHEMA_VERSION) fail("schemaVersion", "지원하지 않는 스키마 $schema (지원: $SCHEMA_VERSION)")
            ContentPack(
                version = field("version").string(),
                locales = field("locales").reviewStatuses(),
                areas = field("areas").list { area() },
                cards = field("cards").list { card() },
                places = field("places").list { place() },
            )
        }
    }

    private fun Node.reviewStatuses(): Map<LocaleTag, ReviewStatus> {
        val statuses = entries { tag, value ->
            when (val v = value.string()) {
                "reviewed" -> ReviewStatus.Reviewed
                "draft" -> ReviewStatus.Draft
                else -> fail(tag.value, "검수 상태는 reviewed 또는 draft 다: '$v'")
            }
        }
        if (statuses.isEmpty()) fail("", "언어가 하나도 없다")
        return statuses
    }

    private fun Node.area() = Area(
        id = AreaId(field("id").string()),
        koName = field("koName").string(),
        name = field("name").localized(),
        active = field("active").boolean(),
    )

    private fun Node.card(): SignCard {
        val group = field("group").string()
        val kind = when (val k = field("kind").string()) {
            "basic" -> CardKind.Basic(group)
            "place" -> CardKind.PlaceSign(group)
            else -> fail("kind", "알 수 없는 카드 종류 '$k'")
        }
        return SignCard(
            id = CardId(field("id").string()),
            kind = kind,
            koText = field("koText").string(),
            romanization = field("romanization").string(),
            reading = field("reading").localized(),
            meaning = field("meaning").localized(),
            note = field("note").localized(),
            pictogram = field("pictogram").stringOrNull()?.let(::PictogramId),
            plateColor = field("plateColor").string().also {
                if (!HEX_COLOR.matches(it)) fail("plateColor", "#rrggbb 형식이 아니다: '$it'")
            },
            audio = field("audio").stringOrNull()?.let(::AudioClip),
            reviewed = field("reviewed").boolean(),
        )
    }

    private fun Node.place() = Place(
        id = PlaceId(field("id").string()),
        areaId = AreaId(field("areaId").string()),
        typeId = field("typeId").string(),
        koName = field("koName").string(),
        name = field("name").localized(),
        address = field("address").localized(),
        coordinate = Coordinate(
            lat = field("lat").double().also { if (it !in -90.0..90.0) fail("lat", "위도 범위 밖: $it") },
            lng = field("lng").double().also { if (it !in -180.0..180.0) fail("lng", "경도 범위 밖: $it") },
        ),
        signWords = field("signWords").orNull()?.list { string() }?.toSet(),
        photo = field("photo").orNull()?.photo(),
        isExample = field("isExample").boolean(),
    )

    private fun Node.photo(): PlacePhoto {
        val license = field("license")
        return PlacePhoto(
            url = field("url").string(),
            license = when (val type = license.field("type").string()) {
                "KOGL_1" -> PhotoLicense.KoglType1
                "KOGL_3" -> PhotoLicense.KoglType3
                "CC0" -> PhotoLicense.Cc0
                "CC_BY" -> PhotoLicense.CcBy(
                    author = license.field("author").nonBlank(),
                    version = license.field("version").string().also {
                        if (!CC_VERSION.matches(it)) license.fail("version", "CC BY 버전 형식(예: 4.0)이 아니다: '$it'")
                    },
                )
                else -> license.fail("type", "허용되지 않는 사진 라이선스 '$type'")
            },
            source = field("source").nonBlank(),
            sourceUrl = field("sourceUrl").stringOrNull(),
        )
    }

    /** JSON 값과 그 위치. 오류 메시지에 위치를 붙이기 위해 감싼다. */
    private class Node(val element: JsonElement, val path: String) {

        fun fail(field: String, reason: String): Nothing =
            throw ContentPackFormatException("${child(field)}: $reason")

        private fun child(name: String) = if (path.isEmpty()) name else "$path.$name"

        private fun failHere(reason: String): Nothing = throw ContentPackFormatException("$path: $reason")

        fun field(name: String): Node {
            val obj = element as? JsonObject ?: failHere("객체가 아니다")
            return Node(obj[name] ?: fail(name, "필드가 없다"), child(name))
        }

        fun orNull(): Node? = if (element is JsonNull) null else this

        private fun primitive(): JsonPrimitive = element as? JsonPrimitive ?: failHere("값이 아니다")

        fun string(): String = primitive().takeIf { it.isString }?.content ?: failHere("문자열이 아니다")

        fun nonBlank(): String = string().ifBlank { failHere("비어 있다") }

        fun stringOrNull(): String? = orNull()?.string()

        fun int(): Int = primitive().takeUnless { it.isString }?.intOrNull ?: failHere("정수가 아니다")

        fun double(): Double = primitive().takeUnless { it.isString }?.doubleOrNull ?: failHere("숫자가 아니다")

        fun boolean(): Boolean = primitive().takeUnless { it.isString }?.booleanOrNull ?: failHere("true/false 가 아니다")

        fun <T> list(item: Node.() -> T): List<T> {
            val array = element as? JsonArray ?: failHere("배열이 아니다")
            return array.mapIndexed { i, e -> Node(e, "$path[$i]").item() }
        }

        /** 언어 태그를 키로 하는 객체. 키가 BCP 47 이 아니면 거부한다. */
        fun <T> entries(value: (LocaleTag, Node) -> T): Map<LocaleTag, T> {
            val obj = element as? JsonObject ?: failHere("언어별 객체가 아니다")
            return obj.entries.associate { (key, element) ->
                val tag = try {
                    LocaleTag(key)
                } catch (e: IllegalArgumentException) {
                    failHere("언어 태그가 아니다: '$key'")
                }
                tag to value(tag, Node(element, "$path.$key"))
            }
        }

        fun localized(): LocalizedText = LocalizedText(entries { _, node -> node.string() })
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        val HEX_COLOR = Regex("#[0-9a-fA-F]{6}")
        val CC_VERSION = Regex("[0-9][.][0-9]")
    }
}
