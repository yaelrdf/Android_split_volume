package dev.yaelrdf.splitvolume

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Named after the markings on the earbuds, not after who is wearing them. */
enum class Side { L, R }

data class Profile(
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val left: Int = DEFAULT_LEVEL,
    val right: Int = DEFAULT_LEVEL,
) {
    fun level(side: Side) = if (side == Side.L) left else right

    fun withLevel(side: Side, value: Int): Profile {
        val v = value.coerceIn(0, 100)
        return if (side == Side.L) copy(left = v) else copy(right = v)
    }

    fun swapped() = copy(left = right, right = left)

    companion object {
        const val DEFAULT_LEVEL = 50
    }
}

/** The phone's own balance from before we took over, kept on disk so a crash can't lose it. */
data class SavedSystemState(val balance: String)

private val Context.dataStore by preferencesDataStore(name = "split_volume")

/** Levels are stored per headphone address, so picking a different pair starts from its own last levels. */
class ProfileStore(context: Context) {
    private val dataStore = context.dataStore

    val profile: Flow<Profile> = dataStore.data.map(::read).distinctUntilChanged()

    suspend fun current(): Profile = profile.first()

    suspend fun update(transform: (Profile) -> Profile) {
        dataStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    suspend fun setDevice(address: String, name: String?) {
        dataStore.edit { prefs ->
            prefs[ADDRESS] = address.uppercase()
            if (name != null) prefs[NAME] = name else prefs.remove(NAME)
        }
    }

    suspend fun savedSystemState(): SavedSystemState? {
        val prefs = dataStore.data.first()
        return SavedSystemState(prefs[ORIG_BALANCE] ?: return null)
    }

    suspend fun setSavedSystemState(state: SavedSystemState?) {
        dataStore.edit { prefs ->
            if (state == null) {
                prefs.remove(ORIG_BALANCE)
            } else {
                prefs[ORIG_BALANCE] = state.balance
            }
        }
    }

    private fun read(prefs: Preferences): Profile {
        val address = prefs[ADDRESS]
        return Profile(
            deviceAddress = address,
            deviceName = prefs[NAME],
            left = prefs[leftKey(address)] ?: Profile.DEFAULT_LEVEL,
            right = prefs[rightKey(address)] ?: Profile.DEFAULT_LEVEL,
        )
    }

    private fun write(prefs: MutablePreferences, profile: Profile) {
        prefs[leftKey(profile.deviceAddress)] = profile.left
        prefs[rightKey(profile.deviceAddress)] = profile.right
    }

    private companion object {
        val ADDRESS = stringPreferencesKey("device_address")
        val NAME = stringPreferencesKey("device_name")
        val ORIG_BALANCE = stringPreferencesKey("original_master_balance")

        fun leftKey(address: String?) = intPreferencesKey("left_${address ?: "none"}")
        fun rightKey(address: String?) = intPreferencesKey("right_${address ?: "none"}")
    }
}
