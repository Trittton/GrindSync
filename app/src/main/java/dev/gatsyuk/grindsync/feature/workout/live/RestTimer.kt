package dev.gatsyuk.grindsync.feature.workout.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-scoped countdown so the rest timer survives navigation between tabs
 * (SPEC §6.1 — switching tabs mid-session must not lose live-session state).
 * Deliberately in-memory: a rest timer has no value across process death.
 */
@Singleton
class RestTimer @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _secondsRemaining = MutableStateFlow<Int?>(null)
    val secondsRemaining: StateFlow<Int?> = _secondsRemaining

    fun start(totalSeconds: Int) {
        job?.cancel()
        job = scope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                _secondsRemaining.value = remaining
                delay(1_000)
                remaining--
            }
            _secondsRemaining.value = null
        }
    }

    fun stop() {
        job?.cancel()
        _secondsRemaining.value = null
    }
}
