package app.signkorean.testing

import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.ContentRepository

/** 고정된 콘텐츠 팩. [loads] 로 몇 번 읽었는지 본다. */
class InMemoryContentRepository(private val pack: ContentPack) : ContentRepository {
    var loads = 0
        private set

    override suspend fun load(): ContentPack {
        loads++
        return pack
    }
}
