package com.dearly.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.ActivityOnboardingBinding
import com.dearly.app.security.Session
import com.dearly.app.ui.common.show
import com.dearly.app.utils.TextUtils

/**
 * First-launch flow: welcome, features, name, PIN, confirm PIN, recovery question.
 * Nothing is written until the final step, so quitting midway simply restarts onboarding.
 * [EXTRA_REPAIR] re-runs only the security steps when security data is missing.
 */
class OnboardingActivity : AppCompatActivity() {
    private lateinit var b: ActivityOnboardingBinding
    private lateinit var recovery: RecoveryFormController
    private lateinit var steps: List<View>

    private var repair = false
    private var step = 0
    private var name = ""
    private var firstPin: String? = null
    private var confirmedPin: String? = null
    private var question = ""
    private var answer = ""
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)
        repair = intent.getBooleanExtra(EXTRA_REPAIR, false)
        steps = listOf(b.stepWelcome, b.stepFeatures, b.stepName, b.stepPin, b.stepConfirm, b.stepRecovery)
        recovery = RecoveryFormController(b.recoveryForm, this)

        b.padCreate.onPinComplete = { pin ->
            firstPin = pin
            b.padConfirm.reset()
            b.tvConfirmError.text = getString(R.string.onb_confirm_body)
            goTo(STEP_CONFIRM)
        }
        b.padConfirm.onPinComplete = { pin ->
            if (pin == firstPin) {
                confirmedPin = pin
                goTo(STEP_RECOVERY)
            } else {
                b.tvConfirmError.text = getString(R.string.onb_pins_mismatch)
                b.padConfirm.showError()
            }
        }
        b.etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { onNext(); true } else false
        }
        b.btnNext.setOnClickListener { onNext() }
        b.btnBack.setOnClickListener { onBack() }

        if (repair) {
            name = ServiceLocator.settings.userName
            step = STEP_PIN
        }
        goTo(step)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (step > firstStep()) onBack() else super.onBackPressed()
    }

    private fun firstStep() = if (repair) STEP_PIN else STEP_WELCOME

    private fun onBack() {
        if (step <= firstStep()) return
        if (step == STEP_CONFIRM) b.padCreate.reset()
        goTo(step - 1)
    }

    private fun onNext() {
        when (step) {
            STEP_NAME -> {
                val cleaned = TextUtils.cleanName(b.etName.text.toString(), 30)
                if (cleaned.isEmpty()) {
                    b.tilName.error = getString(R.string.error_name_required)
                    return
                }
                b.tilName.error = null
                name = cleaned
                goTo(STEP_PIN)
            }
            STEP_RECOVERY -> {
                val qa = recovery.read() ?: return
                question = qa.first
                answer = qa.second
                finishOnboarding()
            }
            else -> goTo(step + 1)
        }
    }

    private fun goTo(target: Int) {
        step = target
        steps.forEachIndexed { i, v -> v.show(i == target) }
        b.progress.setProgressCompat(((target + 1) * 100) / steps.size, true)
        b.btnBack.show(target > firstStep())
        val pinStep = target == STEP_PIN || target == STEP_CONFIRM
        b.btnNext.show(!pinStep)
        b.btnNext.text = getString(
            when (target) {
                STEP_WELCOME -> R.string.onb_get_started
                STEP_RECOVERY -> R.string.finish
                else -> R.string.next
            }
        )
        if (target == STEP_PIN) b.padCreate.reset()
    }

    // ---------------------------------------------------------------- save

    private fun finishOnboarding() {
        val pin = confirmedPin
        if (saving || pin == null) return
        saving = true
        b.btnNext.isEnabled = false
        ServiceLocator.io.execute {
            val ok = try {
                if (!repair) ServiceLocator.settings.userName = name
                ServiceLocator.auth.setPin(pin)
                ServiceLocator.auth.setRecovery(question, answer)
                if (!repair) ServiceLocator.settings.isOnboarded = true
                true
            } catch (e: Exception) {
                android.util.Log.e("Onboarding", "Saving failed", e)
                false
            }
            ServiceLocator.runOnMain {
                if (isDestroyed) return@runOnMain
                if (ok) {
                    Session.unlock() // the user just proved knowledge of the PIN by creating it
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    finish()
                } else {
                    saving = false
                    b.btnNext.isEnabled = true
                    android.widget.Toast.makeText(this, R.string.error_save_setup, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_REPAIR = "repair"
        private const val STEP_WELCOME = 0
        private const val STEP_NAME = 2
        private const val STEP_PIN = 3
        private const val STEP_CONFIRM = 4
        private const val STEP_RECOVERY = 5
    }
}
