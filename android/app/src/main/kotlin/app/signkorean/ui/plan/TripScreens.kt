package app.signkorean.ui.plan

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.trip.TripId
import app.signkorean.ui.designsystem.AppBottomSheet
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.ConfirmDialog
import app.signkorean.ui.designsystem.EmptyState
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.designsystem.StatusBadge
import app.signkorean.ui.designsystem.StatusTone
import app.signkorean.ui.explore.text
import app.signkorean.ui.format.rememberAppFormats
import app.signkorean.ui.text.KoreanText
import app.signkorean.ui.text.asString
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object TripTags {
    const val TITLE = "trip-title"
    const val TITLE_ERROR = "trip-title-error"
    const val END_ERROR = "trip-end-error"
    fun trip(id: TripId) = "trip-${id.value}"
}

// ---------------------------------------------------------------- SC11

/** SC11 여행 목록 (유료일 때 Plan 탭 루트). */
@Composable
fun TripListScreen(
    onNewTrip: () -> Unit,
    onOpenTrip: (TripId) -> Unit,
    /** 무료 상태: 만들지 못하고 보기만 한다(E-12). 여행이 없으면 잠금 안내다(U-09). */
    readOnly: Boolean = false,
    onUpgrade: () -> Unit = {},
    viewModel: TripListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sampleTitle = stringResource(R.string.plan_sample_title)
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(TripListAction.Refresh)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                is TripListEffect.OpenTrip -> onOpenTrip(effect.id)
            }
        }
    }
    if (readOnly && !state.loading && state.trips.isEmpty()) {
        PlanLockedContent(onUpgrade)
        return
    }
    TripListContent(state, onNewTrip, onOpenTrip, onSample = { viewModel.onAction(TripListAction.CreateSample(sampleTitle)) }, readOnly = readOnly, onUpgrade = onUpgrade)
}

@Composable
fun TripListContent(
    state: TripListUiState,
    onNewTrip: () -> Unit,
    onOpenTrip: (TripId) -> Unit,
    onSample: () -> Unit,
    readOnly: Boolean = false,
    onUpgrade: () -> Unit = {},
) {
    val t = SignKoreanTheme.tokens
    val formats = rememberAppFormats()
    if (state.loading) return
    LazyColumn(Modifier.fillMaxSize().background(t.paper), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
        item { Text(stringResource(R.string.plan_subtitle), style = MaterialTheme.typography.bodyMedium, color = t.muted) }
        if (readOnly) {
            item { Text(stringResource(R.string.day_readonly), style = MaterialTheme.typography.bodyMedium) }
            item { app.signkorean.ui.designsystem.LockBadge(stringResource(R.string.lock_trip_planning), onClick = onUpgrade) }
        } else {
            item { PrimaryButton(stringResource(R.string.plan_new_trip), onClick = onNewTrip, modifier = Modifier.fillMaxWidth()) }
        }
        if (state.trips.isEmpty() && !readOnly) {
            item {
                EmptyState(title = stringResource(R.string.plan_empty_title), body = stringResource(R.string.plan_empty_body)) {
                    SecondaryButton(stringResource(R.string.plan_sample), onClick = onSample, enabled = !state.busy)
                }
            }
        }
        items(state.trips, key = { it.id.value }) { trip ->
            Column(
                Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { onOpenTrip(trip.id) }.padding(16.dp).testTag(TripTags.trip(trip.id)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(trip.title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.trip_dates, formats.date(trip.start), formats.date(trip.end)), style = MaterialTheme.typography.bodyMedium, color = t.muted)
                Text(pluralStringResource(R.plurals.places_count, trip.placeCount, trip.placeCount), style = MaterialTheme.typography.labelMedium, color = t.muted)
            }
        }
        state.message?.let { item { Text(it.asString(), color = t.danger) } }
        item { Text(stringResource(R.string.plan_storage_note), style = MaterialTheme.typography.labelSmall, color = t.muted) }
    }
}

// ---------------------------------------------------------------- SC12

/** SC12 여행 만들기·고치기. */
@Composable
fun TripFormScreen(
    tripId: TripId?,
    onSaved: (TripId, created: Boolean) -> Unit,
    onDeleted: () -> Unit,
    onClose: () -> Unit,
    viewModel: TripFormViewModel = koinViewModel { parametersOf(tripId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 편집 중 Back 은 이탈 확인 (UX02, U-14).
    BackHandler { viewModel.onAction(TripFormAction.Back) }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                is TripFormEffect.Saved -> onSaved(effect.id, effect.created)
                TripFormEffect.Deleted -> onDeleted()
                TripFormEffect.Close -> onClose()
            }
        }
    }
    TripFormContent(state, viewModel::onAction)
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
private fun Long.toPickerDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripFormContent(state: TripFormUiState, onAction: (TripFormAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    val formats = rememberAppFormats()
    if (state.loading) return
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    val titleFocus = remember { FocusRequester() }
    // 저장 실패 시 첫 오류 칸으로 (UX07). 제목은 입력 칸에 초점, 날짜 오류는 알림 영역이 읽는다.
    LaunchedEffect(state.focusError, state.titleError) {
        if (state.focusError == TripField.Title && state.titleError != null) runCatching { titleFocus.requestFocus() }
    }
    if (state.confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.trip_discard_title), body = stringResource(R.string.trip_discard_body),
            confirm = stringResource(R.string.trip_discard), dismiss = stringResource(R.string.trip_keep),
            onConfirm = { onAction(TripFormAction.ConfirmDiscard) }, onDismiss = { onAction(TripFormAction.KeepEditing) },
        )
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.trip_delete_title), body = stringResource(R.string.trip_delete_body),
            confirm = stringResource(R.string.trip_delete_confirm), dismiss = stringResource(R.string.trip_keep),
            onConfirm = { onAction(TripFormAction.ConfirmDelete) }, onDismiss = { onAction(TripFormAction.CancelDelete) },
        )
    }
    picking?.let { which ->
        val initial = if (which == "start") state.start else state.end
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.toPickerDate()?.let { onAction(if (which == "start") TripFormAction.Start(it) else TripFormAction.End(it)) }
                    picking = null
                }) { Text(stringResource(R.string.trip_save)) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.trip_keep)) } },
        ) { DatePicker(picker) }
    }
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconButton(onClick = { onAction(TripFormAction.Back) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        Text(
            stringResource(if (state.editing) R.string.trip_form_edit else R.string.trip_form_new),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        val titleError = state.titleError?.asString()
        OutlinedTextField(
            value = state.title,
            onValueChange = { onAction(TripFormAction.Title(it)) },
            label = { Text(stringResource(R.string.trip_name)) },
            singleLine = true,
            isError = titleError != null,
            modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).testTag(TripTags.TITLE)
                .semantics { if (titleError != null) error(titleError) },
        )
        titleError?.let { FieldError(it, TripTags.TITLE_ERROR) }
        DateRow(stringResource(R.string.trip_start), formats.date(state.start)) { picking = "start" }
        DateRow(stringResource(R.string.trip_end), formats.date(state.end)) { picking = "end" }
        state.endError?.let { FieldError(it.asString(), TripTags.END_ERROR) }
        state.message?.let { Text(it.asString(), color = t.danger) }
        PrimaryButton(stringResource(R.string.trip_save), onClick = { onAction(TripFormAction.Save) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
        if (state.editing) QuietButton(stringResource(R.string.trip_delete), onClick = { onAction(TripFormAction.Delete) })
    }
}

/** 필드 바로 아래 오류. 스크린리더가 바로 읽도록 알림 영역이다 (UX07, U-15). */
@Composable
private fun FieldError(text: String, tag: String) {
    Text(
        text,
        color = SignKoreanTheme.tokens.danger,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    val t = SignKoreanTheme.tokens
    Row(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = t.primary)
    }
}

// ---------------------------------------------------------------- SC15

/** SC15 여행·날짜 고르기 시트. 여행이 없으면 [onCreateTrip] 으로 SC12 를 열고, 돌아오면 다시 읽는다. */
@Composable
fun AddToPlanSheet(place: PlaceId, onDismiss: () -> Unit, onCreateTrip: () -> Unit) {
    val viewModel: AddToPlanViewModel = koinViewModel(key = "add-${place.value}") { parametersOf(place) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = rememberAppFormats()
    var added by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(Unit) { viewModel.onAction(AddToPlanAction.Refresh) }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                is AddToPlanEffect.Added -> added = effect.date
            }
        }
    }
    val t = SignKoreanTheme.tokens
    AppBottomSheet(title = stringResource(R.string.add_title), onDismiss = onDismiss) {
        when {
            state.loading -> Unit
            state.noTrips -> {
                Text(stringResource(R.string.add_no_trips), style = MaterialTheme.typography.bodyMedium)
                PrimaryButton(stringResource(R.string.add_create_trip), onClick = onCreateTrip, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                Text(stringResource(R.string.add_choose_trip), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.trips.forEach { trip ->
                        AppChip(trip.title, selected = trip.id == state.selectedTrip, onClick = { viewModel.onAction(AddToPlanAction.SelectTrip(trip.id)) })
                    }
                }
                state.trip?.let { trip ->
                    Text(stringResource(R.string.add_choose_day), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        trip.days.forEach { day ->
                            AppChip(formats.date(day.date), selected = day.date == state.selectedDate, onClick = { viewModel.onAction(AddToPlanAction.SelectDate(day.date)) })
                        }
                    }
                }
                state.message?.let { Text(it.asString(), color = t.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                added?.let {
                    Text(
                        stringResource(R.string.add_added, formats.date(it)),
                        color = t.knowInk,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                PrimaryButton(stringResource(R.string.add_button), onClick = {
                    added = null
                    viewModel.onAction(AddToPlanAction.Add)
                }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.plan_new_trip), onClick = onCreateTrip, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------- SC14

/** SC14 하루에 넣을 등록 장소 고르기. */
@Composable
fun PlacePickerScreen(trip: TripId, date: LocalDate, onClose: () -> Unit, viewModel: PlacePickerViewModel = koinViewModel { parametersOf(trip, date) }) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val t = SignKoreanTheme.tokens
    LazyColumn(Modifier.fillMaxSize().background(t.paper), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.picker_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onAction(PlacePickerAction.Query(it)) },
                label = { Text(stringResource(R.string.picker_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.message?.let { item { Text(it.asString(), color = t.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
        items(state.results, key = { it.place.id.value }) { r ->
            Row(
                Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.place.name.text(), style = MaterialTheme.typography.bodyLarge)
                    KoreanText(r.place.koName, style = MaterialTheme.typography.labelMedium.copy(color = t.muted))
                    if (r.place.isExample) StatusBadge(stringResource(R.string.collection_example), StatusTone.Neutral)
                }
                if (r.added) StatusBadge(stringResource(R.string.picker_added), StatusTone.Known)
                else SecondaryButton(stringResource(R.string.picker_add), onClick = { viewModel.onAction(PlacePickerAction.Add(r.place.id)) })
            }
        }
    }
}
