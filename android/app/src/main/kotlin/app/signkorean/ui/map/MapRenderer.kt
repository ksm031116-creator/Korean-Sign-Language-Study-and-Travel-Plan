package app.signkorean.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signkorean.R
import app.signkorean.domain.location.Coordinate
import app.signkorean.ui.designsystem.SignKoreanTheme
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * 지도 그리기 (설계서 §3: 지도는 UI 관심사라 `:app` 에 둔다). 실제 지도 SDK(Kakao 후보)는 키·약관 확정 뒤에 이 자리에 넣는다.
 * 그때까지는 [SchematicMapRenderer] — 경로선·번호 마커·구간 선택·확대/축소·전체 맞춤만 그린다. 거리·시간은 늘 공급자 값이다.
 */
interface MapRenderer {
    @Composable
    fun Render(points: List<MapPoint>, legs: List<MapLegUi>, selected: Int, onSelectLeg: (Int) -> Unit, modifier: Modifier)
}

/** 도로 없이 경로선과 점만 그리는 간이 지도. 투영은 등장방형(서울 범위에서는 왜곡이 작다). */
object SchematicMapRenderer : MapRenderer {

    private const val MAX_ZOOM = 8f

    @Composable
    override fun Render(points: List<MapPoint>, legs: List<MapLegUi>, selected: Int, onSelectLeg: (Int) -> Unit, modifier: Modifier) {
        val t = SignKoreanTheme.tokens
        val measurer = rememberTextMeasurer()
        val onMarker = MaterialTheme.colorScheme.onPrimary
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        val coords = points.mapNotNull { it.coordinate } + legs.flatMap { it.route?.path.orEmpty() }
        val description = stringResource(R.string.map_schematic)
        Box(modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(14.dp)).background(t.surface)) {
            if (coords.isNotEmpty()) {
                val bounds = Bounds.of(coords)
                Canvas(
                    Modifier.fillMaxSize()
                        .semantics { contentDescription = description }
                        .pointerInput(legs, zoom, pan) {
                            detectTapGestures { tap ->
                                val project = bounds.projector(size.width.toFloat(), size.height.toFloat(), zoom, pan)
                                nearestLeg(legs, points, project, tap)?.let(onSelectLeg)
                            }
                        }
                        .pointerInput(zoom) { detectDragGestures { change, drag -> change.consume(); if (zoom > 1f) pan += drag } },
                ) {
                    val project = bounds.projector(size.width, size.height, zoom, pan)
                    legs.forEachIndexed { i, leg ->
                        val path = leg.route?.path ?: return@forEachIndexed // 모르는 구간은 선을 그리지 않는다(FR15).
                        if (path.size < 2) return@forEachIndexed
                        val line = Path().apply {
                            path.forEachIndexed { j, c -> project(c).let { p -> if (j == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                        }
                        val chosen = i == selected
                        drawPath(line, if (chosen) t.primary else t.muted.copy(alpha = 0.6f), style = Stroke(width = if (chosen) 10f else 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    points.forEach { point ->
                        val c = point.coordinate ?: return@forEach
                        val p = project(c)
                        val origin = point.number == null
                        drawCircle(if (origin) t.accent else t.primary, radius = 30f, center = p)
                        drawCircle(t.surface, radius = 30f, center = p, style = Stroke(width = 5f))
                        val label = measurer.measure(if (origin) "S" else point.number.toString(), TextStyle(color = onMarker, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y - label.size.height / 2f))
                    }
                }
            }
            Column(Modifier.align(Alignment.TopEnd).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MapControl("+", stringResource(R.string.map_zoom_in)) { zoom = (zoom * 1.6f).coerceAtMost(MAX_ZOOM) }
                MapControl("−", stringResource(R.string.map_zoom_out)) { zoom = (zoom / 1.6f).coerceAtLeast(1f); if (zoom == 1f) pan = Offset.Zero }
                MapControl("⤢", stringResource(R.string.map_fit)) { zoom = 1f; pan = Offset.Zero }
            }
        }
    }

    @Composable
    private fun MapControl(symbol: String, label: String, onClick: () -> Unit) {
        val t = SignKoreanTheme.tokens
        IconButton(onClick = onClick, modifier = Modifier.background(t.paper, RoundedCornerShape(10.dp)).semantics { contentDescription = label }) {
            Text(symbol, style = MaterialTheme.typography.titleMedium, color = t.ink)
        }
    }

    private fun nearestLeg(legs: List<MapLegUi>, points: List<MapPoint>, project: (Coordinate) -> Offset, tap: Offset): Int? {
        var best: Int? = null
        var bestDistance = 48f
        legs.forEachIndexed { i, leg ->
            val path = leg.route?.path ?: listOfNotNull(points.getOrNull(leg.from)?.coordinate, points.getOrNull(leg.to)?.coordinate)
            path.map(project).zipWithNext { a, b -> segmentDistance(tap, a, b) }.minOrNull()?.let { d ->
                if (d < bestDistance) {
                    bestDistance = d
                    best = i
                }
            }
        }
        return best
    }

    private fun segmentDistance(p: Offset, a: Offset, b: Offset): Float {
        val ab = b - a
        val len2 = ab.x * ab.x + ab.y * ab.y
        val tt = if (len2 == 0f) 0f else (((p - a).x * ab.x + (p - a).y * ab.y) / len2).coerceIn(0f, 1f)
        val q = a + ab * tt
        return hypot(p.x - q.x, p.y - q.y)
    }

    private class Bounds(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double) {
        private val kx = cos(Math.toRadians((minLat + maxLat) / 2))

        fun projector(width: Float, height: Float, zoom: Float, pan: Offset): (Coordinate) -> Offset {
            val pad = 48f
            val spanX = max((maxLng - minLng) * kx, 1e-5)
            val spanY = max(maxLat - minLat, 1e-5)
            val scale = minOf((width - 2 * pad) / spanX, (height - 2 * pad) / spanY) * zoom
            val cx = width / 2f
            val cy = height / 2f
            val midX = (minLng + maxLng) / 2 * kx
            val midY = (minLat + maxLat) / 2
            return { c -> Offset((cx + (c.lng * kx - midX) * scale).toFloat() + pan.x, (cy - (c.lat - midY) * scale).toFloat() + pan.y) }
        }

        companion object {
            fun of(cs: List<Coordinate>) = Bounds(cs.minOf { it.lat }, cs.maxOf { it.lat }, cs.minOf { it.lng }, cs.maxOf { it.lng })
        }
    }
}
