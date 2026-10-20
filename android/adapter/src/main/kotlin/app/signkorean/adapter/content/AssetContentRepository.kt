package app.signkorean.adapter.content

import android.content.res.AssetManager
import app.signkorean.domain.common.AppDispatchers
import app.signkorean.domain.content.ContentIssue
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.ContentValidator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream

/** 콘텐츠 팩 바이트를 여는 곳. 실제 앱은 assets, 테스트는 문자열. */
fun interface ContentPackSource {
    fun open(): InputStream
}

class AndroidAssetContentPackSource(private val assets: AssetManager) : ContentPackSource {
    override fun open(): InputStream = assets.open(PATH)

    private companion object {
        const val PATH = "content/content-pack.json"
    }
}

/** 검사 결과를 남기는 곳. 실제 앱은 로그, 테스트는 목록. */
fun interface ContentIssueReporter {
    fun report(issues: List<ContentIssue>)
}

/**
 * 앱에 내장된 콘텐츠 팩을 읽어 검사한 뒤 메모리에 둔다. 팩은 앱과 함께 바뀌므로 한 번만 읽는다.
 *
 * 항목 하나의 문제는 그 항목만 뺀 팩으로 계속 동작한다(설계서 §7.1). 출시 전 오류는 BundledContentPackTest 가 막는다.
 * 형식 오류(JSON·스키마·라이선스)는 팩 전체를 쓸 수 없다는 뜻이라 예외로 알린다. 내장 파일이라 다시 읽어도
 * 결과가 같으므로 실패도 기억해 두고 같은 예외를 다시 던진다.
 */
class AssetContentRepository(
    private val source: ContentPackSource,
    private val parser: ContentPackParser,
    private val validator: ContentValidator,
    private val reporter: ContentIssueReporter,
    private val dispatchers: AppDispatchers,
) : ContentRepository {

    private val mutex = Mutex()

    @Volatile
    private var cached: Result<ContentPack>? = null

    override suspend fun load(): ContentPack {
        val result = cached ?: mutex.withLock {
            cached ?: withContext(dispatchers.io) { readOrFormatFailure() }.also { cached = it }
        }
        return result.getOrThrow()
    }

    /** 형식 오류만 결과로 기억한다. 취소·입출력 오류는 일시적일 수 있어 그대로 던지고 다음 호출에서 다시 읽는다. */
    private fun readOrFormatFailure(): Result<ContentPack> = try {
        Result.success(read())
    } catch (e: ContentPackFormatException) {
        Result.failure(e)
    }

    private fun read(): ContentPack {
        val text = source.open().use { it.readBytes().toString(Charsets.UTF_8) }
        val result = validator.validate(parser.parse(text))
        if (result.issues.isNotEmpty()) reporter.report(result.issues)
        return result.pack
    }
}
