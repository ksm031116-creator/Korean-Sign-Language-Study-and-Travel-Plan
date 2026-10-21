package app.signkorean.adapter.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.signkorean.domain.content.CardId
import app.signkorean.domain.upgrade.PendingAction
import app.signkorean.domain.upgrade.PendingActionStore
import kotlinx.coroutines.flow.first

/**
 * 결제 뒤 이어서 할 동작 (설계서 §6.3, B-07). 결제 화면에 있는 동안 프로세스가 끝나도 남도록 파일에 둔다.
 * 값은 "종류:대상" 한 줄이다. 읽을 수 없는 값은 없는 것으로 본다(오래된 앱 버전이 남긴 값 등).
 */
class DataStorePendingActionStore(private val store: DataStore<Preferences>) : PendingActionStore {

    override suspend fun save(action: PendingAction) {
        store.edit { it[ACTION] = encode(action) }
    }

    override suspend fun peek(): PendingAction? = store.data.first()[ACTION]?.let(::decode)

    override suspend fun clear() {
        store.edit { it.remove(ACTION) }
    }

    companion object {
        private val ACTION = stringPreferencesKey("pending_action")
        private const val BOOKMARK = "bookmark:"

        fun encode(action: PendingAction): String = when (action) {
            is PendingAction.SaveBookmark -> BOOKMARK + action.card.value
        }

        fun decode(raw: String): PendingAction? = when {
            raw.startsWith(BOOKMARK) && raw.length > BOOKMARK.length ->
                PendingAction.SaveBookmark(CardId(raw.removePrefix(BOOKMARK)))
            else -> null
        }

        fun create(context: Context) = DataStorePendingActionStore(
            PreferenceDataStoreFactory.create(
                // 손상되면 보류 동작을 잃는다. 사용자는 결제 뒤 저장을 한 번 더 누르면 된다.
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            ) { context.preferencesDataStoreFile("pending_action") },
        )
    }
}
