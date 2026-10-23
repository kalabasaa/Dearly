package com.dearly.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.ActivityLockBinding
import com.dearly.app.security.AuthResult
import com.dearly.app.security.Session
import com.dearly.app.ui.common.lockedMessage
import com.dearly.app.ui.common.show

/**
 * The app lock. It sits on top of whatever screen needs protecting and finishes itself after a
 * successful PIN or recovery-based PIN reset, revealing the screen underneath.
 */
class LockActivity : AppCompatActivity() {
    private lateinit var b: ActivityLockBinding
    private var countdown: CountDownTimer? = null
    private var recoveryVerified = false
    private var newPinFirst: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLockBinding.inflate(layoutInflater)
        setContentView(b.root)

        val name = ServiceLocator.settings.userName
        b.tvGreeting.text = if (name.isBlank()) getString(R.string.lock_welcome_back) else getString(R.string.lock_welcome_back_name, name)

        b.pinPad.onPinComplete = { pin -> verifyPin(pin) }
        b.btnForgot.setOnClickListener { showRecovery() }
        b.btnVerifyAnswer.setOnClickListener { verifyAnswer() }
        b.btnRecoveryCancel.setOnClickListener { showMain() }
        b.padNew.onPinComplete = { pin -> onNewPinEntered(pin) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (b.panelMain.visibility == View.VISIBLE) moveTaskToBack(true) else showMain()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        applyLockoutState()
    }

    override fun onStop() {
        super.onStop()
        countdown?.cancel()
        countdown = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Session.lockScreenShowing = false
    }

    private fun showMain() {
        recoveryVerified = false
        newPinFirst = null
        b.panelRecovery.show(false)
        b.panelNewPin.show(false)
        b.panelMain.show(true)
        b.pinPad.reset()
        applyLockoutState()
    }

    // ---------------------------------------------------------------- PIN

    private fun applyLockoutState() {
        val remaining = ServiceLocator.auth.pinLockRemainingMs()
        countdown?.cancel()
        countdown = null
        if (remaining <= 0) {
            b.pinPad.setInputLocked(false)
            if (b.tvMessage.text.isNullOrEmpty()) b.tvMessage.text = getString(R.string.lock_enter_pin)
            return
        }
        b.pinPad.setInputLocked(true)
        countdown = object : CountDownTimer(remaining, 1000) {
            override fun onTick(ms: Long) {
                b.tvMessage.text = lockedMessage(this@LockActivity, ms)
            }

            override fun onFinish() {
                countdown = null
                b.tvMessage.text = getString(R.string.lock_enter_pin)
                b.pinPad.setInputLocked(false)
                b.pinPad.reset()
            }
        }.start()
    }

    private fun verifyPin(pin: String) {
        ServiceLocator.io.execute {
            val result = try {
                ServiceLocator.auth.verifyPin(pin)
            } catch (e: Exception) {
                null
            }
            ServiceLocator.runOnMain { if (!isDestroyed) onPinResult(result) }
        }
    }

    private fun onPinResult(result: AuthResult?) {
        when (result) {
            AuthResult.Success -> unlockAndFinish()
            is AuthResult.Wrong -> {
                b.tvMessage.text = getString(R.string.pin_wrong_attempts, result.attemptsLeftBeforeLock)
                b.pinPad.showError()
            }
            is AuthResult.LockedOut -> {
                b.pinPad.reset()
                applyLockoutState()
            }
            AuthResult.NotConfigured -> {
                // Security data is gone: send the user through PIN setup again instead of leaving the app unusable.
                startActivity(Intent(this, OnboardingActivity::class.java).putExtra(OnboardingActivity.EXTRA_REPAIR, true))
                finish()
            }
            null -> {
                b.tvMessage.text = getString(R.string.error_generic)
                b.pinPad.showError()
            }
        }
    }

    private fun unlockAndFinish() {
        Session.unlock()
        Session.lockScreenShowing = false
        ServiceLocator.io.execute { runCatching { ServiceLocator.trash.purgeExpired() } }
        finish()
    }

    // ---------------------------------------------------------------- recovery

    private fun showRecovery() {
        val question = ServiceLocator.auth.recoveryQuestion()
        if (question == null) {
            b.tvMessage.text = getString(R.string.recovery_not_set)
            return
        }
        b.panelMain.show(false)
        b.panelRecovery.show(true)
        b.tvQuestion.text = question
        b.etAnswer.text?.clear()
        b.tvRecoveryError.text = ""
    }

    private fun verifyAnswer() {
        val answer = b.etAnswer.text.toString()
        if (answer.isBlank()) {
            b.tvRecoveryError.text = getString(R.string.error_answer_required)
            return
        }
        b.btnVerifyAnswer.isEnabled = false
        ServiceLocator.io.execute {
            val result = try {
                ServiceLocator.auth.verifyRecoveryAnswer(answer)
            } catch (e: Exception) {
                null
            }
            ServiceLocator.runOnMain {
                if (isDestroyed) return@runOnMain
                b.btnVerifyAnswer.isEnabled = true
                when (result) {
                    AuthResult.Success -> {
                        recoveryVerified = true
                        newPinFirst = null
                        b.etAnswer.text?.clear()
                        b.panelRecovery.show(false)
                        b.panelNewPin.show(true)
                        b.tvNewPinTitle.text = getString(R.string.recovery_new_pin)
                        b.padNew.reset()
                    }
                    is AuthResult.Wrong -> b.tvRecoveryError.text = getString(R.string.recovery_wrong_answer)
                    is AuthResult.LockedOut -> b.tvRecoveryError.text = lockedMessage(this, result.remainingMs)
                    else -> b.tvRecoveryError.text = getString(R.string.error_generic)
                }
            }
        }
    }

    private fun onNewPinEntered(pin: String) {
        if (!recoveryVerified) { // can only be reached after the answer was verified
            showMain()
            return
        }
        val first = newPinFirst
        if (first == null) {
            newPinFirst = pin
            b.tvNewPinTitle.text = getString(R.string.recovery_confirm_pin)
            b.padNew.reset()
            return
        }
        if (first != pin) {
            newPinFirst = null
            b.tvNewPinTitle.text = getString(R.string.recovery_pins_mismatch)
            b.padNew.showError()
            return
        }
        ServiceLocator.io.execute {
            val ok = try {
                ServiceLocator.auth.setPin(pin)
                true
            } catch (e: Exception) {
                false
            }
            ServiceLocator.runOnMain {
                if (isDestroyed) return@runOnMain
                if (ok) unlockAndFinish() else {
                    b.tvNewPinTitle.text = getString(R.string.error_generic)
                    b.padNew.reset()
                    newPinFirst = null
                }
            }
        }
    }
}
