package app.signkorean

import android.app.Application
import app.signkorean.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SignKoreanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SignKoreanApplication)
            modules(appModules)
        }
    }
}
