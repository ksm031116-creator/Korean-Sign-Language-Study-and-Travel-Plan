package app.signkorean.ui.map

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.route.RouteFailure
import app.signkorean.domain.route.TravelMode
import app.signkorean.ui.designsystem.AppBottomSheet
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.EmptyState
import app.signkorean.ui.designsystem.ErrorState
import app.signkorean.ui.designsystem.LockBadge
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.explore.text
import app.signkorean.ui.format.rememberAppFormats
import app.signkorean.ui.upgrade.UpgradeFeature
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.ZoneId

/** SC18 지도 (Phase 19 D2). */
@Composable
fun MapScreen(
    target: MapTarget,
    onClose: () -> Unit,
    onUpgrade: (UpgradeFeature) -> Unit,
    renderer: MapRenderer = SchematicMapRenderer,
    viewModel: MapViewModel = koinViewModel { parametersOf(target) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // SC21 에서 결제하고 돌아오면 잠금을 다시 확인한다. 처음 열 때는 뷰모델이 이미 읽는다.
    var resumedOnce by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(viewModel) {
        if (resumedOnce) viewModel.onAction(MapAction.Resume) else resumedOnce = true
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                is MapEffect.Upgrade -> onUpgrade(effect.feature)
                // 허용 목록을 통과한 https 주소만 온다. 지도 앱·브라우저가 없으면 아무 일도 하지 않는다.
                is MapEffect.OpenUrl -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)))
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
    }
    MapContent(state, viewModel::onAction, onClose, onUpgrade, renderer)
}

@Composable
private fun MapPoint.name(): String =
    place?.name?.text() ?: label ?: if (number == null) stringResource(R.string.map_origin) else number.toString()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapContent(
    state: MapUiState,
    onAction: (MapAction) -> Unit,
    onClose: () -> Unit,
    onUpgrade: (UpgradeFeature) -> Unit,
    renderer: MapRenderer = SchematicMapRenderer,
) {
    val t = SignKoreanTheme.tokens
    val formats = rememberAppFormats()
    if (state.pickingOrigin) {
        AppBottomSheet(title = stringResource(R.string.origin_title), onDismiss = { onAction(MapAction.CloseOriginPicker) }) {
            OriginPicker(onPicked = { origin, _ -> onAction(MapAction.OriginChosen(origin)) })
        }
    }
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        // 문구는 고른 수단을 따른다. 대중교통인데 "도보"라고 읽히지 않게(리뷰 M4).
        val transit = state.mode == TravelMode.Transit
        val calculating = stringResource(if (transit) R.string.map_calculating_transit else R.string.map_calculating)
        Text(stringResource(if (transit) R.string.map_title_transit else R.string.map_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        val subtitle = when {
            state.tripTitle != null && state.date != null -> state.tripTitle + " · " + formats.date(state.date)
            state.points.lastOrNull()?.place != null ->
                stringResource(if (transit) R.string.map_route_to_transit else R.string.map_route_to, state.points.last().name())
            else -> null
        }
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = t.muted) }
        when {
            state.loading -> {
                Text(calculating, style = MaterialTheme.typography.bodyMedium, color = t.muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                return@Column
            }
            state.locked -> {
                LockBadge(stringResource(R.string.lock_route), onClick = { onUpgrade(UpgradeFeature.Route) })
                return@Column
            }
            state.loadFailed -> {
                ErrorState(stringResource(R.string.map_load_failed), onRetry = { onAction(MapAction.Refresh) })
                return@Column
            }
            state.missing -> {
                EmptyState(title = stringResource(R.string.map_missing), body = "")
                return@Column
            }
        }
        // 이동 수단. 도보가 기본이고, 대중교통은 이 지도에서만 본다(일정 시각은 도보 기준).
        // 둘 중 하나만 고르므로 라디오 묶음으로 읽힌다. 큰 글씨에서는 다음 줄로 넘어간다.
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(TravelMode.Walk to R.string.map_mode_walk, TravelMode.Transit to R.string.map_mode_transit).forEach { (mode, label) ->
                AppChip(
                    stringResource(label),
                    selected = state.mode == mode,
                    onClick = { onAction(MapAction.SetMode(mode)) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                )
            }
        }
        renderer.Render(state.points, state.legs, state.selected, { onAction(MapAction.SelectLeg(it)) }, Modifier)
        if (state.busy) Text(calculating, style = MaterialTheme.typography.labelMedium, color = t.muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Text(stringResource(R.string.map_schematic), style = MaterialTheme.typography.labelMedium, color = t.muted)
        // 경로 공급자 출처 표기 (구현 계획 Phase 19 D2).
        if (state.attributions.isNotEmpty()) {
            Text(stringResource(R.string.map_attribution, state.attributions.joinToString(", ")), style = MaterialTheme.typography.labelMedium, color = t.muted)
        }
        if (state.needsOrigin) {
            Text(stringResource(if (transit) R.string.map_needs_origin_transit else R.string.map_needs_origin), style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(stringResource(R.string.map_choose_origin), onClick = { onAction(MapAction.OpenOriginPicker) }, modifier = Modifier.fillMaxWidth())
            return@Column
        }
        state.selectedLeg?.let { leg ->
            val from = state.points.getOrNull(leg.from)
            val to = state.points.getOrNull(leg.to)
            val fromName = from?.name().orEmpty()
            val toName = to?.name().orEmpty()
            Column(
                Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.map_leg_from_to, fromName, toName), style = MaterialTheme.typography.titleSmall)
                val route = leg.route
                if (route != null) {
                    Text(legDetail(route, formats), style = MaterialTheme.typography.bodyLarge)
                    route.transit?.let { TransitDetail(it, route.durationSeconds, formats) }
                } else {
                    Text(
                        failureText(leg.failure, transit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = t.danger,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                state.calculatedAt?.let { at ->
                    Text(stringResource(R.string.map_calculated_at, formats.time(at.atZone(ZoneId.systemDefault()).toLocalTime())), style = MaterialTheme.typography.labelMedium, color = t.muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton(stringResource(R.string.map_refresh), onClick = { onAction(MapAction.Refresh) }, enabled = !state.busy)
                    if (from?.coordinate != null && to?.coordinate != null) {
                        QuietButton(stringResource(R.string.map_open_external), onClick = { onAction(MapAction.OpenExternal(fromName, toName)) })
                    }
                }
            }
        }
        if (state.origin != null) {
            SecondaryButton(stringResource(R.string.map_change_origin), onClick = { onAction(MapAction.OpenOriginPicker) }, modifier = Modifier.fillMaxWidth())
        }
        if (state.legs.size > 1) {
            Text(stringResource(R.string.map_legs), style = MaterialTheme.typography.titleSmall)
            state.legs.forEachIndexed { i, leg ->
                val label = stringResource(R.string.map_leg_from_to, state.points.getOrNull(leg.from)?.name().orEmpty(), state.points.getOrNull(leg.to)?.name().orEmpty())
                val chosen = i == state.selected
                Column(
                    Modifier.fillMaxWidth().background(if (chosen) t.primarySoft else t.surface, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab, onClickLabel = stringResource(R.string.map_select_leg, label)) { onAction(MapAction.SelectLeg(i)) }
                        .semantics { selected = chosen }
                        .padding(12.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    val route = leg.route
                    Text(
                        if (route != null) legDetail(route, formats)
                        else failureText(leg.failure, transit),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (route != null) t.muted else t.danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun failureText(failure: RouteFailure?, transit: Boolean): String = stringResource(
    when (failure) {
        RouteFailure.NoRoute -> if (transit) R.string.map_failure_no_route_transit else R.string.map_failure_no_route
        RouteFailure.Unsupported -> if (transit) R.string.map_failure_unsupported_transit else R.string.map_failure_unsupported
        RouteFailure.Timeout -> R.string.map_failure_timeout
        RouteFailure.Network -> R.string.map_failure_network
        null -> R.string.map_leg_unknown
    },
)

/** 거리·시간 한 줄. 대중교통이면 "대중교통", 아니면 "도보"로 읽힌다. 공급자 초를 분으로 올림한다(일정과 같은 규칙). */
@Composable
private fun legDetail(route: app.signkorean.domain.route.Route, formats: app.signkorean.ui.format.AppFormats): String {
    val minutes = formats.duration(roundUpMinutes(route.durationSeconds))
    val distance = formats.distance(route.distanceMeters)
    return if (route.transit != null) stringResource(R.string.map_leg_detail_transit, distance, minutes)
    else stringResource(R.string.map_leg_detail, distance, minutes)
}

/** 대중교통 단계: 환승·요금, 그리고 "걷기 4분 → 지하철 3호선 20분 → 버스 7212 12분". 요금이 없으면 쓰지 않는다. */
@Composable
private fun TransitDetail(transit: app.signkorean.domain.route.TransitSummary, totalSeconds: Long, formats: app.signkorean.ui.format.AppFormats) {
    val t = SignKoreanTheme.tokens
    val transfers = if (transit.transfers == 0) stringResource(R.string.map_no_transfers) else pluralStringResource(R.plurals.map_transfers, transit.transfers, transit.transfers)
    val fare = transit.fareWon?.let { stringResource(R.string.map_fare, java.text.NumberFormat.getIntegerInstance().format(it)) }
    Text(listOfNotNull(transfers, fare).joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    transit.steps.forEach { step ->
        // 공급자가 시간을 주지 않은 단계는 시간 없이 쓴다(0분으로 지어내지 않는다).
        val time = step.seconds?.let { formats.duration(stepMinutes(it)) }
        val lines = step.lines.joinToString(", ")
        Text(
            when (step.kind) {
                app.signkorean.domain.route.TransitKind.Walk ->
                    if (time != null) stringResource(R.string.map_step_walk, time) else stringResource(R.string.map_step_walk_untimed)
                app.signkorean.domain.route.TransitKind.Bus ->
                    if (time != null) stringResource(R.string.map_step_bus, lines, time) else stringResource(R.string.map_step_bus_untimed, lines)
                app.signkorean.domain.route.TransitKind.Subway ->
                    if (time != null) stringResource(R.string.map_step_subway, lines, time) else stringResource(R.string.map_step_subway_untimed, lines)
            },
            style = MaterialTheme.typography.labelMedium,
            color = t.muted,
        )
    }
    // 공급자가 단계로 주지 않는 시간(정류장까지 걷기·기다리기). 보이는 총 분에서 보이는 단계 분을 뺀다 — 화면의 분을
    // 더하면 총 분과 맞는다(리뷰 후속 L2). 시간을 모르는 단계가 있으면 차이를 알 수 없으므로 쓰지 않는다.
    if (transit.steps.all { it.seconds != null }) {
        val rest = roundUpMinutes(totalSeconds) - transit.steps.sumOf { stepMinutes(it.seconds!!) }
        if (rest > 0) Text(stringResource(R.string.map_step_other, formats.duration(rest)), style = MaterialTheme.typography.labelMedium, color = t.muted)
    }
}

/** 단계 시간은 가장 가까운 분으로(최소 1분). 단계마다 올림하면 합이 총 분을 넘는다. */
private fun stepMinutes(seconds: Long): Int = ((seconds + 30) / 60).toInt().coerceAtLeast(1)

/** 공급자 초를 분으로 올림한다. 일정(SC13)과 같은 규칙이다. */
private fun roundUpMinutes(seconds: Long): Int = ((seconds + 59) / 60).toInt()
