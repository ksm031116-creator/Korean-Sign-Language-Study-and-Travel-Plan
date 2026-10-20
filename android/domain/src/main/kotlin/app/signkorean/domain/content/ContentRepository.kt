package app.signkorean.domain.content

/** 콘텐츠 팩을 읽는 포트. 구현은 앱에 내장된 팩을 읽고 검사한 뒤 같은 결과를 계속 돌려준다. */
interface ContentRepository {
    suspend fun load(): ContentPack
}
