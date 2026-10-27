package dev.yaelrdf.splitvolume

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies a [Profile] to the phone: STREAM_MUSIC volume follows the louder side, and the system's master
 * balance (applied by AudioFlinger after every app, effect and the system mono downmix) attenuates the other.
 */
class SplitController(context: Context, private val store: ProfileStore) {
    sealed interface State {
        data object Idle : State
        data class Waiting(val deviceName: String) : State
        data class Active(val deviceName: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val audio = context.getSystemService(AudioManager::class.java)
    private val mutex = Mutex()

    // Null when the phone blocks the hidden calls.
    private val systemBalance by lazy { SystemBalance.create() }

    val systemBalanceAvailable: Boolean get() = systemBalance != null

    /** The phone's own "Mono audio" switch; null if it can't be read. */
    val systemMonoOn: Boolean? get() = systemBalance?.let { runCatching { it.monoOn }.getOrNull() }

    // Our own setStreamVolume calls echo back as volume-change broadcasts; those must not rescale the levels.
    @Volatile private var lastOwnVolumeWrite = 0L

    fun setState(state: State) {
        _state.value = state
    }

    suspend fun activate(profile: Profile) = mutex.withLock {
        val sb = systemBalance ?: throw IllegalStateException("This phone doesn't let apps change the audio balance.")
        if (store.savedSystemState() == null) store.setSavedSystemState(SavedSystemState(sb.balance.toString()))
        applyLocked(profile)
    }

    suspend fun apply(profile: Profile) = mutex.withLock { applyLocked(profile) }

    private suspend fun applyLocked(profile: Profile) {
        val sb = systemBalance ?: return
        val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val out = BalanceMapper.map(profile.left, profile.right, min, max)
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != out.volumeIndex) {
            lastOwnVolumeWrite = SystemClock.elapsedRealtime()
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, out.volumeIndex, 0)
        }
        sb.balance = out.balance
    }

    /** Puts back the balance the phone had before we took over, so its speaker isn't left lopsided. */
    suspend fun restore() = mutex.withLock {
        val saved = store.savedSystemState() ?: return@withLock
        systemBalance?.balance = saved.balance.toFloatOrNull() ?: 0f
        store.setSavedSystemState(null)
    }

    suspend fun hasPendingRestore() = store.savedSystemState() != null

    /** STREAM_MUSIC changed outside the app (volume keys): move both sides together, keeping their ratio. */
    suspend fun onExternalVolumeChange() {
        if (SystemClock.elapsedRealtime() - lastOwnVolumeWrite < VOLUME_ECHO_WINDOW_MS) return
        val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val index = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        store.update { p ->
            if (BalanceMapper.indexFor(max(p.left, p.right), min, max) == index) {
                p
            } else {
                val (l, r) = BalanceMapper.rescale(p.left, p.right, BalanceMapper.levelFor(index, min, max))
                p.copy(left = l, right = r)
            }
        }
    }

    /** Plays a short beep in one earbud so you can tell which is which (both, if system mono is on). */
    suspend fun ping(side: Side) = TonePlayer.beep(side)

    private companion object {
        const val VOLUME_ECHO_WINDOW_MS = 800L
    }
}
