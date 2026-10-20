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

class AndroidAssetContentPackSource(
    private val assets: AssetManager,
    private val path: String = "content/content-pack.json",
) : ContentPackSource {
    override fun open(): InputStream = assets.open(path)
}

/** 검사 결과를 남기는 곳. 실제 앱은 로그, 테스트는 목록. */
fun interface ContentIssueReporter {
    fun report(issues: List<ContentIssue>)
}

/** 팩에 출시를 막는 오류가 있다. 테스트(BundledContentPackTest)가 먼저 잡아야 하는 상황이다. */
class InvalidContentPackException(val issues: List<ContentIssue>) :
    RuntimeException("콘텐츠 팩 오류 ${issues.count { it.isError }}건: ${issues.filter { it.isError }.take(3)}")

/**
 * 앱에 내장된 콘텐츠 팩을 읽어 검사한 뒤 메모리에 둔다. 팩은 앱과 함께 바뀌므로 한 번만 읽는다.
 * 실패는 캐시하지 않아 다음 호출에서 다시 시도한다.
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
    private var cached: ContentPack? = null

    override suspend fun load(): ContentPack {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(dispatchers.io) { read() }.also { cached = it }
        }
    }

    private fun read(): ContentPack {
        val text = source.open().use { it.readBytes().toString(Charsets.UTF_8) }
        val result = validator.validate(parser.parse(text))
        if (result.issues.isNotEmpty()) reporter.report(result.issues)
        if (result.hasErrors) throw InvalidContentPackException(result.issues)
        return result.pack
    }
}
