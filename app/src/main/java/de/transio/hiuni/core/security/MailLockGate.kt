package de.transio.hiuni.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

/**
 * Status der Geräte-Biometrie aus User-Sicht. Wir machen kein PIN-only-Fallback
 * obligatorisch — wenn der User keine Biometrie eingerichtet hat (`NONE_ENROLLED`),
 * disablen wir das Setting; sonst könnte er sich selbst aussperren.
 */
enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED;

    val canUse: Boolean get() = this == AVAILABLE
}

fun deviceBiometricAvailability(context: Context): BiometricAvailability {
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    return when (BiometricManager.from(context).canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
        else -> BiometricAvailability.HARDWARE_UNAVAILABLE
    }
}

/**
 * Composable-Hook: liefert einen Trigger zurück, der den System-BiometricPrompt
 * öffnet. Erfolgreiche Auth ruft `onSuccess`, Cancel/Fehler `onError`.
 *
 * MainActivity ist FragmentActivity, sonst hätte BiometricPrompt keine
 * lifecycle-fähige Host-Activity. Falls der Context-Lookup scheitert
 * (Preview, sehr ungewöhnliche Composable-Hierarchie) → log + skip-success
 * statt App-Crash.
 */
@Composable
fun rememberMailUnlockPrompt(
    onSuccess: () -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val successState = rememberUpdatedState(onSuccess)
    val errorState = rememberUpdatedState(onError)
    return remember(context) {
        {
            val activity = context.findFragmentActivity()
            if (activity == null) {
                Timber.w("MailLockGate: kein FragmentActivity — Auth übersprungen")
                successState.value()
            } else {
                val executor = ContextCompat.getMainExecutor(activity)
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        successState.value()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        errorState.value(errString.toString())
                    }
                    // onAuthenticationFailed (single wrong finger) bewusst ignoriert —
                    // erst onAuthenticationError beendet die Prompt-Session.
                }
                val prompt = BiometricPrompt(activity, executor, callback)
                val info = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Mail entsperren")
                    .setSubtitle("Bestätige mit Fingerabdruck oder Gerätesperre")
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()
                prompt.authenticate(info)
            }
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is FragmentActivity) return ctx
        ctx = (ctx as? ContextWrapper)?.baseContext
    }
    return null
}
