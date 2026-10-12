package com.github.kr328.clash.ui.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Runs [block] one at a time for requests that come in bursts: at startup the screen, the
 * service's "created" message and the tab next door all ask within half a second, and each
 * used to make its own full round of service calls.
 *
 * A request that arrives while a run still waits for its first answer restarts that run:
 * nothing has been read yet, so one run serves both. Once [block] has called `answered`, what
 * it read may already be out of date — it finishes, and one more run follows, however many
 * requests came in meanwhile. Either way every request is followed by a run that began after it.
 *
 * Not thread-safe: [request] and `answered` are called on the main thread only.
 */
class RefreshGate(
    private val scope: CoroutineScope,
    private val block: suspend (answered: () -> Unit) -> Unit,
) {
    private var job: Job? = null
    private var run = 0
    private var answered = false
    private var again = false

    fun request() {
        if (job?.isActive == true) {
            if (answered) {
                again = true

                return
            }

            job?.cancel()
        }

        // A cancelled run may still report an answer on its way out; the number tells it apart.
        val mine = ++run

        answered = false

        job = scope.launch {
            do {
                again = false

                block { if (run == mine) answered = true }

                // A cancelled run that got this far must not touch what belongs to the next one.
                ensureActive()

                answered = false
            } while (again)
        }
    }
}
