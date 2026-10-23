package com.dearly.app.ui

import android.content.Context
import android.widget.ArrayAdapter
import com.dearly.app.R
import com.dearly.app.databinding.LayoutRecoveryFormBinding

/** Shared question/answer form (onboarding and Settings). */
class RecoveryFormController(private val b: LayoutRecoveryFormBinding, context: Context) {
    private val presets = context.resources.getStringArray(R.array.recovery_questions)
    private val customIndex = presets.size - 1 // last entry is "Write my own question"
    private var selected = 0

    init {
        b.actvQuestion.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, presets))
        b.actvQuestion.setText(presets[0], false)
        b.actvQuestion.setOnItemClickListener { _, _, position, _ ->
            selected = position
            b.tilCustomQuestion.visibility = if (position == customIndex) android.view.View.VISIBLE else android.view.View.GONE
        }
        b.tilCustomQuestion.visibility = android.view.View.GONE
    }

    /** Returns (question, answer) or null after showing validation errors. */
    fun read(): Pair<String, String>? {
        val ctx = b.root.context
        b.tilCustomQuestion.error = null
        b.tilAnswer.error = null
        val question = if (selected == customIndex) b.etCustomQuestion.text.toString().trim() else presets[selected]
        val answer = b.etAnswer.text.toString().trim()
        var ok = true
        if (question.length < 3) {
            b.tilCustomQuestion.error = ctx.getString(R.string.error_question_required)
            ok = false
        }
        if (answer.length < 2) {
            b.tilAnswer.error = ctx.getString(R.string.error_answer_required)
            ok = false
        }
        return if (ok) question to answer else null
    }
}
