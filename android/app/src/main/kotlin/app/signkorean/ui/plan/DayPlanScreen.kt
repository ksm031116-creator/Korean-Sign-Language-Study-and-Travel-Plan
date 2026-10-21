package app.signkorean.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.trip.Origin
import app.signkorean.domain.trip.TripId
import app.signkorean.ui.designsystem.AppBottomSheet
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.ConfirmDialog
import app.signkorean.ui.designsystem.EmptyState
import app.signkorean.ui.designsystem.ErrorState
import app.signkorean.ui.designsystem.LockBadge
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.designsystem.StatusBadge
import app.signkorean.ui.designsystem.StatusTone
import app.signkorean.ui.explore.text
import app.signkorean.ui.format.AppFormats
import app.signkorean.ui.format.rememberAppFormats
import app.signkorean.ui.places.PlacePhotoView
import app.signkorean.ui.text.KoreanText
import app.signkorean.ui.text.asString
import app.signkorean.ui.upgrade.UpgradeFeature
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate
import java.time.LocalTime

/** SC13 날짜별 일정 (Phase 18). */
@Composable
fun DayPlanScreen(
    trip: TripId,
    date: LocalDate?,
    onClose: () -> Unit,
    onOpenPicker: (TripId, LocalDate) -> Unit,
    onEditTrip: (TripId) -> Unit,
    onUpgrade: (UpgradeFeature) -> Unit,
    onOpenMap: (TripId, LocalDate) -> Unit = { _, _ -> },
    onOpenLearn: () -> Unit = {},
    viewModel: DayPlanViewModel = koinViewModel { parametersOf(trip, date) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // SC14·SC12·SC21 에서 돌아오면 다시 읽는다. 처음 열 때는 뷰모델이 이미 읽으므로 건너뛴다(DPV-L4).
    var resumedOnce by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(viewModel) {
        if (resumedOnce) viewModel.onAction(DayPlanAction.Refresh) else resumedOnce = true
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                is DayPlanEffect.OpenPicker -> onOpenPicker(effect.trip, effect.date)
                is DayPlanEffect.EditTrip -> onEditTrip(effect.trip)
                is DayPlanEffect.OpenMap -> onOpenMap(effect.trip, effect.date)
                is DayPlanEffect.Upgrade -> onUpgrade(effect.feature)
            }
        }
    }
    DayPlanContent(state, viewModel::onAction, onClose, studySection = { d, revision -> DayStudySection(trip, d, revision, onOpenLearn, onUpgrade) })
}

@Composable
fun DayPlanContent(
    state: DayPlanUiState,
    onAction: (DayPlanAction) -> Unit,
    onClose: () -> Unit,
    studySection: @Composable (LocalDate, Int) -> Unit = { _, _ -> },
) {
    val t = SignKoreanTheme.tokens
    val formats = rememberAppFormats()
    if (state.loading) return
    val trip = state.trip
    if (state.loadFailed || trip == null && !state.missing) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
            ErrorState(stringResource(R.string.day_load_failed), onRetry = { onAction(DayPlanAction.Refresh) })
        }
        return
    }
    if (state.missing || trip == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
            EmptyState(title = stringResource(R.string.trip_error_missing), body = "")
        }
        return
    }
    state.confirmRemove?.let {
        ConfirmDialog(
            title = stringResource(R.string.stop_remove_title), body = stringResource(R.string.stop_remove_body),
            confirm = stringResource(R.string.stop_remove), dismiss = stringResource(R.string.trip_keep),
            onConfirm = { onAction(DayPlanAction.ConfirmRemove) }, onDismiss = { onAction(DayPlanAction.CancelRemove) },
        )
    }
    state.editing?.let { stopId -> state.rows.firstOrNull { it.stop.id == stopId }?.let { StopSheet(it, state, onAction) } }
    if (state.startSheet) StartSheet(state, onAction)

    Column(Modifier.fillMaxSize().background(t.paper)) {
    // 저장 실패 등 안내는 목록 밖에 고정한다. 아래로 스크롤해 있어도 보인다(DPV-M2).
    state.message?.takeIf { state.editing == null && !state.startSheet }?.let {
        Text(
            it.asString(),
            color = t.danger,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().background(t.dangerSoft).padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(trip.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.trip_dates, formats.date(trip.start), formats.date(trip.end)), style = MaterialTheme.typography.bodyMedium, color = t.muted)
                }
                if (!state.readOnly) QuietButton(stringResource(R.string.day_edit_trip), onClick = { onAction(DayPlanAction.EditTrip) })
            }
        }
        if (state.readOnly) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.day_readonly), style = MaterialTheme.typography.bodyMedium)
                    // 편집은 자물쇠 + 기능명 (UX09). 누르면 SC21.
                    LockBadge(stringResource(R.string.lock_change_trip), onClick = { onAction(DayPlanAction.EditTrip) })
                }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.dates.forEachIndexed { i, d ->
                    AppChip(stringResource(R.string.day_tab, i + 1) + " · " + formats.date(d), selected = d == state.date, onClick = { onAction(DayPlanAction.SelectDate(d)) })
                }
            }
        }
        state.day?.let { day ->
            item {
                Row(
                    Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.day_starts, formats.time(day.startTime)), style = MaterialTheme.typography.titleSmall)
                        val origin = when (val o = day.origin) {
                            null -> stringResource(R.string.day_no_origin)
                            is Origin.AtPlace -> stringResource(R.string.day_from, state.originPlace?.name?.text() ?: o.place.value)
                            is Origin.AtCoordinate -> stringResource(R.string.day_from, o.label ?: stringResource(R.string.origin_current))
                        }
                        Text(origin, style = MaterialTheme.typography.bodyMedium, color = t.muted)
                    }
                    if (!state.readOnly) QuietButton(stringResource(R.string.day_edit_start), onClick = { onAction(DayPlanAction.OpenStart) })
                }
            }
        }
        // 하루 경로 지도(SC18). 경로는 유료라 무료면 자물쇠 + 기능명 (UX09).
        if (state.rows.isNotEmpty()) {
            item {
                if (state.readOnly) LockBadge(stringResource(R.string.lock_route), onClick = { onAction(DayPlanAction.OpenMap) })
                else SecondaryButton(stringResource(R.string.map_open), onClick = { onAction(DayPlanAction.OpenMap) }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (state.needsRecalculation) item { Notice(stringResource(R.string.day_recalc)) }
        if (state.rows.isEmpty()) item { EmptyState(title = stringResource(R.string.day_empty), body = "") }
        itemsIndexed(state.rows, key = { _, r -> r.stop.id.value }) { i, row ->
            StopRow(i, row, state.rows.size, state.readOnly, state.busy, formats, onAction)
        }
        if (!state.readOnly) item { SecondaryButton(stringResource(R.string.day_add_place), onClick = { onAction(DayPlanAction.AddPlace) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) }
        state.schedule?.takeIf { state.rows.isNotEmpty() }?.let { s ->
            item {
                Column(
                    Modifier.fillMaxWidth().background(t.primarySoft, RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(pluralStringResource(R.plurals.places_count, state.rows.size, state.rows.size), style = MaterialTheme.typography.titleSmall)
                    val stay = formats.duration(s.stayMinutes)
                    val end = s.end
                    Text(
                        if (s.incomplete || end == null) stringResource(R.string.day_summary_incomplete, stay)
                        else stringResource(R.string.day_summary, formats.duration(s.travelMinutes ?: 0), stay, formats.time(end)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // 허위 합계 대신 이유를 말한다 (FR20, UX04). 다음 날 도착·반올림 안내 (PRD §9.2).
                    if (s.incomplete) Text(stringResource(R.string.day_incomplete), style = MaterialTheme.typography.bodySmall, color = t.muted)
                    state.rows.lastOrNull()?.timing?.leaveDay?.takeIf { it > 0 && end != null }?.let { d ->
                        StatusBadge(stringResource(R.string.day_end_offset, pluralStringResource(R.plurals.day_plus_days, d, d)), StatusTone.Learning)
                    }
                    if (s.nextDay) Text(stringResource(R.string.day_next_day), style = MaterialTheme.typography.bodySmall, color = t.danger)
                    if (s.roundingDiffers) Text(stringResource(R.string.day_rounding), style = MaterialTheme.typography.bodySmall, color = t.muted)
                }
            }
        }
        // Study this day (FR21). 그날 장소의 간판을 방문 순서대로.
        if (state.day != null) item(key = "study-" + state.day.date) { studySection(state.day.date, state.day.revision) }
    }
    }
}

@Composable
private fun StopRow(index: Int, row: StopRowUi, count: Int, readOnly: Boolean, busy: Boolean, formats: AppFormats, onAction: (DayPlanAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    val name = row.place?.name?.text() ?: row.stop.place.value
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (row.hasLegBefore) {
            Text(
                row.legBefore?.let { stringResource(R.string.day_leg, formats.duration(it)) } ?: stringResource(R.string.day_leg_unknown),
                style = MaterialTheme.typography.labelMedium,
                color = t.muted,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(14.dp))
                .then(if (readOnly) Modifier else Modifier.clickable(enabled = !busy, role = Role.Button, onClickLabel = stringResource(R.string.stop_open, name)) { onAction(DayPlanAction.OpenStop(row.stop.id)) })
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text((index + 1).toString(), style = MaterialTheme.typography.titleMedium, color = t.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                row.place?.let { KoreanText(it.koName, style = MaterialTheme.typography.labelMedium.copy(color = t.muted)) }
                val timing = row.timing
                val arrive = timing?.arrive
                val leave = timing?.leave
                if (timing != null && arrive != null && leave != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.day_times, formats.time(arrive), formats.time(leave)), style = MaterialTheme.typography.bodyMedium)
                        // 자정을 넘기면 도착·출발 각각 며칠 뒤인지 표시한다(DPV-M3).
                        if (timing.arriveDay > 0) StatusBadge(stringResource(R.string.day_arrive_offset, pluralStringResource(R.plurals.day_plus_days, timing.arriveDay, timing.arriveDay)), StatusTone.Learning)
                        if (timing.leaveDay > timing.arriveDay) StatusBadge(stringResource(R.string.day_leave_offset, pluralStringResource(R.plurals.day_plus_days, timing.leaveDay, timing.leaveDay)), StatusTone.Learning)
                    }
                }
                Text(
                    stringResource(R.string.day_stay, formats.duration(row.stop.stayMinutes)) + " · " +
                        pluralStringResource(R.plurals.signs_count, row.signCount, row.signCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = t.muted,
                )
            }
            // 끌기 대신 위·아래 버튼으로 순서를 바꾼다. 스크린리더·키보드로도 된다(UX06).
            if (!readOnly) {
                Column {
                    IconButton(onClick = { onAction(DayPlanAction.MoveUp(row.stop.id)) }, enabled = !busy && index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.day_move_up, name))
                    }
                    IconButton(onClick = { onAction(DayPlanAction.MoveDown(row.stop.id)) }, enabled = !busy && index < count - 1) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.day_move_down, name))
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().background(SignKoreanTheme.tokens.accentSoft, RoundedCornerShape(12.dp)).padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** SC16 방문 장소 편집 시트: 체류 시간·다른 날로 옮기기·삭제. */
@Composable
private fun StopSheet(row: StopRowUi, state: DayPlanUiState, onAction: (DayPlanAction) -> Unit) {
    val formats = rememberAppFormats()
    var stay by rememberSaveable(row.stop.id.value) { mutableStateOf(row.stop.stayMinutes.toString()) }
    AppBottomSheet(title = stringResource(R.string.stop_title), onDismiss = { onAction(DayPlanAction.CloseSheet) }) {
        row.place?.let { PlacePhotoView(it.photo, "#2f6f5e") }
        Text(row.place?.name?.text() ?: row.stop.place.value, style = MaterialTheme.typography.titleMedium)
        val error = state.stayError?.asString()
        OutlinedTextField(
            value = stay,
            onValueChange = { stay = it; onAction(DayPlanAction.StayEdited) },
            label = { Text(stringResource(R.string.stop_stay_label)) },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().semantics { if (error != null) error(error) },
        )
        error?.let { Text(it, color = SignKoreanTheme.tokens.danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }) }
        PrimaryButton(stringResource(R.string.stop_save), onClick = { onAction(DayPlanAction.SaveStay(row.stop.id, stay)) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        val others = state.dates.filter { it != state.date }
        if (others.isNotEmpty()) {
            Text(stringResource(R.string.stop_move), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { d -> AppChip(formats.date(d), selected = false, onClick = { if (!state.busy) onAction(DayPlanAction.MoveTo(row.stop.id, d)) }) }
            }
        }
        state.message?.let { Text(it.asString(), color = SignKoreanTheme.tokens.danger) }
        QuietButton(stringResource(R.string.stop_remove), onClick = { onAction(DayPlanAction.Remove(row.stop.id)) }, enabled = !state.busy)
    }
}
/**
 * SC17 하루 출발 시트: 출발 시각(OS 시간 선택기)과 출발지. 출발지는 없음·지금 출발지·그날 일정에 없는 등록 장소에서
 * 고르거나(DPV-H2), SC19(검색·추천 출발점·현재 위치)로 찾는다. 고른 출발지는 Apply 할 때만 저장한다(PRD §10.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartSheet(state: DayPlanUiState, onAction: (DayPlanAction) -> Unit) {
    val day = state.day ?: return
    val picker = rememberTimePickerState(initialHour = day.startTime.hour, initialMinute = day.startTime.minute)
    // 고른 것: NONE, CURRENT, PICKED(SC19 에서 찾은 것), 또는 등록 장소 id.
    var choice by rememberSaveable { mutableStateOf(if (day.origin == null) NONE else CURRENT) }
    var picked by remember { mutableStateOf<Pair<Origin, String>?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    AppBottomSheet(title = stringResource(if (searching) R.string.origin_title else R.string.start_title), onDismiss = { onAction(DayPlanAction.CloseSheet) }) {
        if (searching) {
            app.signkorean.ui.map.OriginPicker(onPicked = { origin, label ->
                picked = origin to label
                choice = PICKED
                searching = false
            })
            QuietButton(stringResource(R.string.action_back), onClick = { searching = false })
            return@AppBottomSheet
        }
        Text(stringResource(R.string.start_time), style = MaterialTheme.typography.labelLarge)
        TimePicker(state = picker)
        Text(stringResource(R.string.start_origin), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip(stringResource(R.string.day_no_origin), selected = choice == NONE, onClick = { choice = NONE })
            day.origin?.let { o ->
                val label = when (o) {
                    is Origin.AtPlace -> state.originPlace?.name?.text() ?: o.place.value
                    is Origin.AtCoordinate -> o.label ?: stringResource(R.string.origin_current)
                }
                AppChip(label, selected = choice == CURRENT, onClick = { choice = CURRENT })
            }
            picked?.let { (_, label) -> AppChip(label, selected = choice == PICKED, onClick = { choice = PICKED }) }
        }
        val candidates = state.originCandidates.filter { p -> (day.origin as? Origin.AtPlace)?.place != p.id }
        if (candidates.isNotEmpty()) {
            Text(stringResource(R.string.start_suggest_places), style = MaterialTheme.typography.labelMedium, color = SignKoreanTheme.tokens.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { p -> AppChip(p.name.text(), selected = choice == p.id.value, onClick = { choice = p.id.value }) }
            }
        }
        QuietButton(stringResource(R.string.start_find), onClick = { searching = true })
        PrimaryButton(stringResource(R.string.start_apply), onClick = {
            val origin = when (choice) {
                NONE -> null
                CURRENT -> day.origin
                PICKED -> picked?.first ?: day.origin
                else -> Origin.AtPlace(app.signkorean.domain.content.PlaceId(choice))
            }
            onAction(DayPlanAction.ApplyStart(LocalTime.of(picker.hour, picker.minute), origin))
        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
    }
}

private const val NONE = "\u0000none"
private const val CURRENT = "\u0000current"
private const val PICKED = "\u0000picked"
