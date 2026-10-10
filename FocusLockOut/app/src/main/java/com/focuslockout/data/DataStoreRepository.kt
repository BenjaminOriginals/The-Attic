package com.focuslockout.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_lock_state")

/**
 * Single source of truth for the lock session, the block list and the chosen challenge.
 *
 * Every mutation that depends on current state happens inside one [edit] transaction, so the
 * accessibility service, the foreground service and the UI can write concurrently without races.
 */
class DataStoreRepository(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.focusDataStore)

    val lockState: Flow<LockState> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                Log.e(TAG, "Unreadable preferences, falling back to defaults", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { it.toLockState() }
        .distinctUntilChanged()

    suspend fun snapshot(): LockState = lockState.first()

    suspend fun armSession(
        durationMinutes: Int,
        challengeType: ChallengeType,
        now: Long = System.currentTimeMillis(),
    ): LockState {
        val minutes = LockPolicy.clampDuration(durationMinutes)
        val updated = dataStore.edit { prefs ->
            prefs[Keys.SESSION_ACTIVE] = true
            prefs[Keys.SESSION_START] = now
            prefs[Keys.SESSION_END] = now + minutes * 60_000L
            prefs[Keys.CHALLENGE] = challengeType.key
            prefs[Keys.DURATION] = minutes
            prefs.remove(Keys.GRACE)
        }
        return updated.toLockState()
    }

    /** Ends the session immediately (emergency unlock). */
    suspend fun endSessionNow() {
        dataStore.edit { it.clearSession() }
    }

    /**
     * Ends the session only if its end time has passed. Timers in several components may fire for
     * the same session; this keeps a late timer from killing a session that was re-armed since.
     */
    suspend fun endSessionIfExpired(now: Long = System.currentTimeMillis()): Boolean {
        var ended = false
        dataStore.edit { prefs ->
            val active = prefs[Keys.SESSION_ACTIVE] ?: false
            val end = prefs[Keys.SESSION_END] ?: 0L
            if (active && now >= end) {
                prefs.clearSession()
                ended = true
            }
        }
        return ended
    }

    /**
     * Adds or removes an app from the block list. Removing an app while a session is engaged is
     * refused (returns false): un-blocking mid-session would make the lock trivially bypassable.
     */
    suspend fun setPackageBlocked(
        packageName: String,
        blocked: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        var applied = true
        dataStore.edit { prefs ->
            val current = prefs[Keys.BLOCKED] ?: emptySet()
            if (!blocked && prefs.isSessionEngaged(now) && packageName in current) {
                applied = false
                return@edit
            }
            prefs[Keys.BLOCKED] = if (blocked) current + packageName else current - packageName
        }
        return applied
    }

    /** The challenge is fixed for the lifetime of an engaged session. */
    suspend fun setChallengeType(type: ChallengeType, now: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            if (!prefs.isSessionEngaged(now)) prefs[Keys.CHALLENGE] = type.key
        }
    }

    suspend fun setDurationMinutes(minutes: Int) {
        dataStore.edit { it[Keys.DURATION] = LockPolicy.clampDuration(minutes) }
    }

    /** Lets [packageName] through for [durationMillis]; expired entries are pruned on every write. */
    suspend fun grantGrace(
        packageName: String,
        durationMillis: Long = LockPolicy.GRACE_PERIOD_MILLIS,
        now: Long = System.currentTimeMillis(),
    ) {
        dataStore.edit { prefs ->
            val stillValid = GraceCodec.decode(prefs[Keys.GRACE] ?: emptySet()).filterValues { it > now }
            prefs[Keys.GRACE] = GraceCodec.encode(stillValid + (packageName to now + durationMillis))
        }
    }

    private fun Preferences.isSessionEngaged(now: Long): Boolean =
        (this[Keys.SESSION_ACTIVE] ?: false) && now < (this[Keys.SESSION_END] ?: 0L)

    private fun MutablePreferences.clearSession() {
        this[Keys.SESSION_ACTIVE] = false
        remove(Keys.SESSION_START)
        remove(Keys.SESSION_END)
        remove(Keys.GRACE)
    }

    private fun Preferences.toLockState() = LockState(
        isSessionActive = this[Keys.SESSION_ACTIVE] ?: false,
        sessionStartTime = this[Keys.SESSION_START] ?: 0L,
        sessionEndTime = this[Keys.SESSION_END] ?: 0L,
        blockedPackages = this[Keys.BLOCKED] ?: emptySet(),
        selectedChallengeType = ChallengeType.fromKey(this[Keys.CHALLENGE]),
        durationMinutes = this[Keys.DURATION] ?: LockPolicy.DEFAULT_DURATION_MINUTES,
        graceExpiries = GraceCodec.decode(this[Keys.GRACE] ?: emptySet()),
    )

    private object Keys {
        val SESSION_ACTIVE = booleanPreferencesKey("is_session_active")
        val SESSION_START = longPreferencesKey("session_start_time")
        val SESSION_END = longPreferencesKey("session_end_time")
        val BLOCKED = stringSetPreferencesKey("blocked_packages")
        val CHALLENGE = stringPreferencesKey("selected_challenge_type")
        val DURATION = intPreferencesKey("session_duration_minutes")
        val GRACE = stringSetPreferencesKey("grace_entries")
    }

    private companion object {
        const val TAG = "DataStoreRepository"
    }
}
