package app.signkorean.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signkorean.domain.content.PictogramId
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 픽토그램 세트 한눈에 보기 (Phase 20 D3). 그림을 바꾸면 이 스크린샷으로 검토한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h1000dp")
class PictogramSheetScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun sheet() {
        compose.setContent {
            FlowRow(Modifier.background(Color.White).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PictogramArt.drawnIds.sorted().forEach { id ->
                    Column(Modifier.background(Color(0xFF2F6FB0)).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(PictogramArt.drawable(PictogramId(id))!!), contentDescription = null, tint = Color.White, modifier = Modifier.size(width = 84.dp, height = 70.dp))
                        Text(id, color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/pictograms.png")
    }
}
