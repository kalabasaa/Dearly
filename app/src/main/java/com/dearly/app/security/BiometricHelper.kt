package com.dearly.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Optional convenience unlock. Success of the system prompt unlocks the app; it is not bound to a
 * Keystore key, and the app PIN always remains available as the fallback.
 */
object BiometricHelper {
    enum class Status { AVAILABLE, NOT_ENROLLED, UNAVAILABLE }

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun status(context: Context): Status =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Status.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Status.NOT_ENROLLED
            else -> Status.UNAVAILABLE
        }

    /**
     * [onDismissed] receives (cancelledByUser, message). Cancelling or errors never unlock; the caller
     * simply keeps showing the PIN pad.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        negativeText: String,
        onSuccess: () -> Unit,
        onDismissed: (Boolean, CharSequence?) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onDismissed(cancelled, errString)
            }
            // onAuthenticationFailed: a single bad scan; the system prompt stays open and retries.
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .build()
        prompt.authenticate(info)
    }
}
