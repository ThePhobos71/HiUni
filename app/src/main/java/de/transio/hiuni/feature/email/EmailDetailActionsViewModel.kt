package de.transio.hiuni.feature.email

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.feature.email.data.EmailEntity
import javax.inject.Inject

/**
 * Schmaler Adapter, damit das `EmailDetail`-Composable den Singleton
 * [EmailComposePrefillHolder] über Hilt erreicht, ohne dass der bestehende
 * `EmailViewModel` (Inbox/Detail-Mixed) angefasst werden muss.
 *
 * Wir teilen die Detail-Aktionen (Reply/Forward) bewusst vom Inbox-VM ab —
 * Reply/Forward sind one-shot Side-Effects, brauchen keinen StateFlow.
 */
@HiltViewModel
class EmailDetailActionsViewModel @Inject constructor(
    private val prefillHolder: EmailComposePrefillHolder
) : ViewModel() {

    /** Lege einen Reply-Prefill ab. Aufrufer navigiert anschließend zu Compose. */
    fun stageReply(email: EmailEntity, bodyPlain: String?) {
        prefillHolder.set(buildReplyPrefill(email, bodyPlain))
    }

    /** Lege einen Forward-Prefill ab. Aufrufer navigiert anschließend zu Compose. */
    fun stageForward(email: EmailEntity, bodyPlain: String?) {
        prefillHolder.set(buildForwardPrefill(email, bodyPlain))
    }
}
