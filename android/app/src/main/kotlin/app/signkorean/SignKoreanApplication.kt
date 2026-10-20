package app.signkorean

import android.app.Application
import android.util.Log
import app.signkorean.di.appModules
import app.signkorean.domain.content.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SignKoreanApplication : Application() {

    /** 앱 프로세스와 수명이 같은 작업용. 화면 수명과 무관한 미리 읽기에만 쓴다. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@SignKoreanApplication)
            modules(appModules)
        }.koin

        // 콘텐츠 팩을 미리 읽어 첫 화면의 대기를 줄이고, 읽은 결과를 로그로 남긴다.
        val content = koin.get<ContentRepository>()
        appScope.launch {
            runCatching { content.load() }
                .onSuccess { Log.i(TAG, "content ${it.version}: cards=${it.cards.size} places=${it.places.size} areas=${it.areas.size}") }
                .onFailure { Log.e(TAG, "content pack failed to load", it) }
        }
    }

    private companion object {
        const val TAG = "SignKorean"
    }
}
