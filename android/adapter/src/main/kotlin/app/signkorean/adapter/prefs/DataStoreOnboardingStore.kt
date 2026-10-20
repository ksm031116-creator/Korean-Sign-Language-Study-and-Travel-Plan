package app.signkorean.adapter.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import app.signkorean.domain.onboarding.OnboardingStore
import kotlinx.coroutines.flow.first

/** 안내를 봤는지 기록 (설계서 §6.6). 앱 언어는 여기에 두지 않는다(AppCompat 이 단일 출처, §8.2). */
class DataStoreOnboardingStore(private val store: DataStore<Preferences>) : OnboardingStore {

    override suspend fun firstRunDone() = store.data.first()[FIRST_RUN] ?: false
    override suspend fun markFirstRunDone() { store.edit { it[FIRST_RUN] = true } }
    override suspend fun coachSeen() = store.data.first()[COACH] ?: false
    override suspend fun markCoachSeen() { store.edit { it[COACH] = true } }
    override suspend fun resetCoach() { store.edit { it.remove(COACH) } }

    companion object {
        private val FIRST_RUN = booleanPreferencesKey("first_run_done")
        private val COACH = booleanPreferencesKey("coach_seen")

        /** DataStore 는 파일 하나에 하나만 만들어야 한다. 앱 조립부에서 single 로 한 번만 부른다. */
        fun create(context: Context) = DataStoreOnboardingStore(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("onboarding") },
        )
    }
}
