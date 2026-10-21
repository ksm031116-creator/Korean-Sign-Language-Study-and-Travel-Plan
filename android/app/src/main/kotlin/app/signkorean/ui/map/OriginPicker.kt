package app.signkorean.ui.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.location.StartLocationProblem
import app.signkorean.domain.trip.Origin
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.ConfirmDialog
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.explore.text
import app.signkorean.ui.text.KoreanText
import org.koin.androidx.compose.koinViewModel

/** 추천 출발점 key → 화면 이름. */
@Composable
fun suggestedStartName(key: String): String = when (key) {
    "seoul-station" -> stringResource(R.string.origin_seoul_station)
    "city-hall" -> stringResource(R.string.origin_city_hall)
    else -> key
}

/**
 * SC19 출발지 고르기 (FR14). 시트 안에 넣는 내용이다(시트를 겹치지 않는다). 고르면 [onPicked] 에 출발지와 보일 이름을 준다.
 * 현재 위치는 설명 → OS 권한(대략·정확 위치, 앞에서만) → 한 번 읽기. 거부·해외·실패면 이유를 보이고 아래 수동 선택을 그대로 둔다(M-02).
 */
@Composable
fun OriginPicker(onPicked: (Origin, String) -> Unit, viewModel: OriginPickerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val t = SignKoreanTheme.tokens
    val currentLabel = stringResource(R.string.origin_current)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // 요청이 취소돼 결과가 비면(구성 변경 등) 거부로 보지 않는다.
        if (result.isNotEmpty()) viewModel.onAction(OriginPickerAction.PermissionResult(granted = result.values.any { it }))
    }
    // 닫히면 위치 읽기를 멈춘다. 다시 열었을 때 고르지 않은 현재 위치가 저절로 들어가지 않게(ORG-M6).
    DisposableEffect(viewModel) { onDispose { viewModel.onAction(OriginPickerAction.Reset) } }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                OriginPickerEffect.RequestPermission ->
                    permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                // 장소는 아래 목록이 이름과 함께 바로 준다. 효과로 오는 것은 좌표 출발지(추천·현재 위치)다.
                is OriginPickerEffect.Picked -> when (val o = effect.origin) {
                    is Origin.AtPlace -> onPicked(o, o.place.value)
                    is Origin.AtCoordinate -> onPicked(o, o.label ?: currentLabel)
                }
            }
        }
    }
    if (state.explaining) {
        ConfirmDialog(
            title = stringResource(R.string.origin_explain_title), body = stringResource(R.string.origin_explain_body),
            confirm = stringResource(R.string.origin_explain_continue), dismiss = stringResource(R.string.upgrade_not_now),
            onConfirm = { viewModel.onAction(OriginPickerAction.ConfirmExplain) }, onDismiss = { viewModel.onAction(OriginPickerAction.DismissExplain) },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(
            stringResource(R.string.origin_use_current),
            onClick = { viewModel.onAction(OriginPickerAction.UseCurrentLocation) },
            enabled = !state.locating,
            modifier = Modifier.fillMaxWidth(),
        )
        val status = when {
            state.locating -> stringResource(R.string.origin_locating)
            else -> when (state.problem) {
                StartLocationProblem.Denied -> stringResource(R.string.origin_denied)
                StartLocationProblem.OutsideSeoul -> stringResource(R.string.origin_outside)
                StartLocationProblem.Failed -> stringResource(R.string.origin_failed)
                null -> null
            }
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.problem != null) t.danger else t.muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(stringResource(R.string.origin_suggested), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.suggestions.forEach { s ->
                val name = suggestedStartName(s.key)
                AppChip(name, selected = false, onClick = { viewModel.onAction(OriginPickerAction.PickSuggested(s.key, name)) })
            }
        }
        Text(stringResource(R.string.origin_places), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = state.query,
            onValueChange = { viewModel.onAction(OriginPickerAction.Query(it)) },
            label = { Text(stringResource(R.string.origin_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.places.isEmpty()) Text(stringResource(R.string.origin_empty), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        state.places.forEach { place ->
            val name = place.name.text()
            Column(
                Modifier.fillMaxWidth().background(t.paper, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onPicked(Origin.AtPlace(place.id), name) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                KoreanText(place.koName, style = MaterialTheme.typography.labelMedium.copy(color = t.muted))
            }
        }
        if (state.loadFailed) Text(stringResource(R.string.common_load_failed), color = t.danger)
    }
}
