package app.signkorean

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import app.signkorean.ui.AppEntry

/** API 30~32 에서 앱별 언어를 적용하려면 AppCompatActivity 여야 한다 (설계서 §8.2). */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppEntry() }
    }
}
