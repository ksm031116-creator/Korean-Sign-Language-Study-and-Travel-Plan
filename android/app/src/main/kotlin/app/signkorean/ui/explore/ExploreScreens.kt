package app.signkorean.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.common.LocalizedText
import app.signkorean.domain.content.AreaId
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.SessionRules
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.locale.LanguagePolicy
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.ConfirmDialog
import app.signkorean.ui.designsystem.EmptyState
import app.signkorean.ui.designsystem.ErrorState
import app.signkorean.ui.designsystem.MinTouch
import app.signkorean.ui.designsystem.PlateFace
import app.signkorean.ui.designsystem.PlateSize
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.designsystem.SignPlate
import app.signkorean.ui.designsystem.StatusBadge
import app.signkorean.ui.designsystem.StatusTone
import app.signkorean.ui.text.KoreanText
import app.signkorean.ui.text.asString
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

object ExploreTags {
    const val BASICS = "explore-basics"
    const val RESUME = "explore-resume"
    const val SEARCH = "explore-search"
    fun area(id: AreaId) = "explore-area-${id.value}"
}

/** 지금 UI 문구 폴더의 언어로 콘텐츠 문구를 고른다(L10N-01·04). */
@Composable
fun LocalizedText.text(): String {
    val chain = LanguagePolicy.fallbackChain(LocaleTag(stringResource(R.string.format_locale)))
    return resolve(chain) ?: ""
}

private fun parse(hex: String) = runCatching { Color(android.graphics.Color.parseColor(hex)).copy(alpha = 1f) }.getOrDefault(Color(0xFF2F6F5E))

/** SC02 Explore. */
@Composable
fun ExploreScreen(
    onOpenBasics: () -> Unit,
    onOpenArea: (AreaId) -> Unit,
    onResume: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ExploreViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 학습에서 돌아오면 진행률과 Resume 배너를 다시 읽는다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(ExploreAction.Refresh)
        onPauseOrDispose { }
    }
    ExploreContent(state, viewModel::onAction, onOpenBasics, onOpenArea, onResume, onOpenSettings)
}

@Composable
fun ExploreContent(
    state: ExploreUiState,
    onAction: (ExploreAction) -> Unit,
    onOpenBasics: () -> Unit,
    onOpenArea: (AreaId) -> Unit,
    onResume: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.explore_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.explore_subtitle), style = MaterialTheme.typography.bodyMedium, color = t.muted)
            }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.explore_settings)) }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = { onAction(ExploreAction.Search(it)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onAction(ExploreAction.Search("")) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.explore_search_clear))
                    }
                }
            },
            placeholder = { Text(stringResource(R.string.explore_search)) },
            modifier = Modifier.fillMaxWidth().testTag(ExploreTags.SEARCH),
        )
        if (state.failed) ErrorState(stringResource(R.string.explore_load_failed), onRetry = { onAction(ExploreAction.Refresh) })
        state.resume?.let { ResumeBanner(it, onResume) }
        if (state.query.isBlank()) {
            SectionHead(stringResource(R.string.explore_basics_section), stringResource(R.string.explore_basics_hint))
            BasicsCard(state, onOpenBasics)
        }
        SectionHead(stringResource(R.string.explore_areas_section), null)
        if (state.areas.isEmpty() && state.query.isNotBlank()) {
            Text(stringResource(R.string.explore_no_results, state.query.trim()), color = t.muted, style = MaterialTheme.typography.bodyMedium)
        }
        state.areas.forEach { area -> AreaCard(area, onOpenArea) }
    }
}

@Composable
private fun SectionHead(title: String, hint: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        hint?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = SignKoreanTheme.tokens.muted) }
    }
}

@Composable
fun sourceLabel(source: SessionSource, areaName: LocalizedText?): String = when (source) {
    SessionSource.Basics -> stringResource(R.string.basics_name)
    is SessionSource.Area -> areaName?.text() ?: source.area.value
    SessionSource.Saved -> stringResource(R.string.source_saved)
    is SessionSource.TripDay -> stringResource(R.string.source_trip_day)
    is SessionSource.Place -> stringResource(R.string.source_place)
}

@Composable
private fun ResumeBanner(resume: ResumeUi, onResume: () -> Unit) {
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.fillMaxWidth().background(t.primarySoft, RoundedCornerShape(14.dp)).padding(16.dp).testTag(ExploreTags.RESUME),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.explore_resume_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.explore_resume_detail, sourceLabel(resume.source, resume.areaName), resume.decisions, SessionRules.MAX_DECISIONS),
            style = MaterialTheme.typography.bodyMedium,
            color = t.muted,
        )
        PrimaryButton(stringResource(R.string.explore_resume_action), onClick = onResume)
    }
}

@Composable
private fun BasicsCard(state: ExploreUiState, onOpen: () -> Unit) {
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(16.dp)).border(1.dp, t.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onOpen).padding(16.dp).testTag(ExploreTags.BASICS),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 앞면 표지판만 보인다(그림 없이 단어만, U-18).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.basicsPreview.forEach { SignPlate(it, PlateFace.Front, Modifier.weight(1f), PlateSize.Mini) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.basics_name) + " · ", style = MaterialTheme.typography.titleMedium)
            KoreanText(stringResource(R.string.ko_basics_name), style = MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(R.string.basics_description), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        ProgressLine(state.basics)
    }
}

@Composable
private fun ProgressLine(progress: Progress) {
    Row {
        Text(pluralStringResource(R.plurals.signs_count, progress.total, progress.total), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.progress_known, progress.known, progress.total), style = MaterialTheme.typography.labelMedium, color = SignKoreanTheme.tokens.knowInk)
    }
}

@Composable
private fun AreaCard(area: AreaUi, onOpen: (AreaId) -> Unit) {
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(16.dp)).border(1.dp, t.line, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { onOpen(area.id) }.testTag(ExploreTags.area(area.id)),
    ) {
        Box(
            Modifier.fillMaxWidth().height(92.dp).background(parse(area.color), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            KoreanText(area.koName, style = MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(area.tag.text(), style = MaterialTheme.typography.labelMedium, color = t.muted)
            Text(area.name.text(), style = MaterialTheme.typography.titleMedium)
            Row {
                Text(pluralStringResource(R.plurals.places_count, area.placeCount, area.placeCount), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.progress_known, area.progress.known, area.progress.total), style = MaterialTheme.typography.labelMedium, color = t.knowInk)
            }
        }
    }
}

/** SC03 지역·Basics 상세. */
@Composable
fun CollectionScreen(
    source: SessionSource,
    onOpenLearn: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CollectionViewModel = koinViewModel(parameters = { parametersOf(source) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                CollectionEffect.OpenLearn -> onOpenLearn()
            }
        }
    }
    CollectionContent(state, viewModel::onAction, onOpenPlace, onBack)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionContent(state: CollectionUiState, onAction: (CollectionAction) -> Unit, onOpenPlace: (String) -> Unit, onBack: () -> Unit) {
    val t = SignKoreanTheme.tokens
    if (state.missing) {
        EmptyState(title = stringResource(R.string.collection_missing), body = "") { PrimaryButton(stringResource(R.string.learn_back_to_explore), onClick = onBack) }
        return
    }
    if (state.confirmReplace) {
        ConfirmDialog(
            title = stringResource(R.string.replace_title),
            body = stringResource(R.string.replace_body),
            confirm = stringResource(R.string.replace_end),
            dismiss = stringResource(R.string.replace_keep),
            onConfirm = { onAction(CollectionAction.ConfirmReplace) },
            onDismiss = { onAction(CollectionAction.KeepSession) },
        )
    }
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.isBasics) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.basics_name) + " · ", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                KoreanText(stringResource(R.string.ko_basics_name), style = MaterialTheme.typography.headlineSmall)
            }
            Text(stringResource(R.string.basics_description), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        } else {
            Box(Modifier.fillMaxWidth().height(110.dp).background(parse(state.color), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                KoreanText(state.koName, style = MaterialTheme.typography.headlineSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
            }
            Text(state.name.text(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(state.description.text(), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(stringResource(R.string.collection_new), state.progress.new, Modifier.weight(1f))
            Stat(stringResource(R.string.collection_learning), state.progress.learning, Modifier.weight(1f))
            Stat(stringResource(R.string.collection_known), state.progress.known, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip(stringResource(R.string.collection_mode_all), selected = state.mode == Mode.All, onClick = { onAction(CollectionAction.SetMode(Mode.All)) })
            if (state.canReview) {
                AppChip(stringResource(R.string.collection_mode_review), selected = state.mode == Mode.Review, onClick = { onAction(CollectionAction.SetMode(Mode.Review)) })
            }
        }
        PrimaryButton(stringResource(R.string.collection_start), onClick = { onAction(CollectionAction.Start) }, enabled = state.canStart && !state.busy, modifier = Modifier.fillMaxWidth())
        state.message?.let { Text(it.asString(), color = t.muted, style = MaterialTheme.typography.bodyMedium) }
        if (state.isBasics) {
            // 장소가 없어 리뷰·경로·일정 버튼을 그리지 않는다. 잠금 표시와 다른 이유라 따로 알린다(UX09).
            Column(Modifier.fillMaxWidth().background(t.primarySoft, RoundedCornerShape(12.dp)).padding(14.dp)) {
                Text(stringResource(R.string.collection_basics_notice_title), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.collection_basics_notice_body), style = MaterialTheme.typography.bodyMedium, color = t.muted)
            }
        }
        Text(stringResource(R.string.collection_words), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        state.groups.forEach { g ->
            g.group?.let { group ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.label.text() + " · ", style = MaterialTheme.typography.labelLarge)
                    KoreanText(group.koLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                g.words.forEach { WordChipView(it) { onAction(CollectionAction.StartFrom(it.id)) } }
            }
        }
        if (!state.isBasics && state.places.isNotEmpty()) {
            Text(stringResource(R.string.collection_places), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            state.places.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onOpenPlace(p.place.id.value) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.place.name.text(), style = MaterialTheme.typography.bodyLarge)
                        KoreanText(p.place.koName, style = MaterialTheme.typography.labelMedium.copy(color = t.muted))
                    }
                    if (p.place.isExample) StatusBadge(stringResource(R.string.collection_example), StatusTone.Neutral)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    val t = SignKoreanTheme.tokens
    Column(modifier.background(t.surface, RoundedCornerShape(12.dp)).padding(12.dp).semantics(mergeDescendants = true) {}) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = t.muted)
    }
}

@Composable
private fun WordChipView(chip: WordChip, onClick: () -> Unit) {
    val t = SignKoreanTheme.tokens
    val (fill, ink) = when (chip.status) {
        CardStatus.Known -> t.knowSoft to t.knowInk
        CardStatus.Learning -> t.accentSoft to t.accentInk
        CardStatus.Unseen -> t.surface to t.ink
    }
    Box(
        Modifier.background(fill, RoundedCornerShape(10.dp)).border(1.dp, t.line, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp).height(MinTouch),
        contentAlignment = Alignment.Center,
    ) {
        KoreanText(chip.koText, style = MaterialTheme.typography.bodyLarge.copy(color = ink))
    }
}

