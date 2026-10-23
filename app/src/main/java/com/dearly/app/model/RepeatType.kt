package com.dearly.app.model

enum class RepeatType(val code: Int) {
    NONE(0), DAILY(1), WEEKLY(2), MONTHLY(3);

    companion object {
        fun fromCode(code: Int): RepeatType = values().firstOrNull { it.code == code } ?: NONE
    }
}
