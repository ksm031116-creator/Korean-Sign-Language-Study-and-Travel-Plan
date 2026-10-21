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
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementCache
import kotlinx.coroutines.flow.first

/**
 * 마지막으로 확인된 권한 (설계서 §6.4, E-01). 학습 DB 와 다른 파일이라 학습 초기화(FR16)가 닿지 않는다.
 * 저장 값은 enum 이름이 아니라 고정 문자열이다. enum 이름을 바꿔도 저장된 권한이 사라지지 않게 한다.
 */
class DataStoreEntitlementCache(private val store: DataStore<Preferences>) : EntitlementCache {

    override suspend fun load(): Entitlement = when (store.data.first()[STATUS]) {
        PAID -> Entitlement.Paid
        else -> Entitlement.Free
    }

    override suspend fun save(entitlement: Entitlement) {
        store.edit {
            it[STATUS] = when (entitlement) {
                Entitlement.Paid -> PAID
                Entitlement.Free -> FREE
            }
        }
    }

    companion object {
        private val STATUS = stringPreferencesKey("status")
        private const val PAID = "paid"
        private const val FREE = "free"

        fun create(context: Context) = DataStoreEntitlementCache(
            PreferenceDataStoreFactory.create(
                // 손상되면 Free 로 시작한다. 앱 시작 때 스토어 확인이 Paid 를 되살린다.
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            ) { context.preferencesDataStoreFile("entitlement") },
        )
    }
}
