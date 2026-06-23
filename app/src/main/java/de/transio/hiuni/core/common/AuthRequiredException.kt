package de.transio.hiuni.core.common

/**
 * Signalisiert, dass eine Operation gegen LSF/CAS abbricht, weil keine gültige
 * Session vorliegt (TGT abgelaufen, kein Cookie, Silent-Renewal gescheitert).
 *
 * Wird von Workern und Repos verwendet, um Auth-Probleme von transienten
 * Netzwerk-Fehlern zu trennen — Auth-Fehler sind nicht durch Retries lösbar,
 * der User muss aktiv erneut einloggen.
 */
class AuthRequiredException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
