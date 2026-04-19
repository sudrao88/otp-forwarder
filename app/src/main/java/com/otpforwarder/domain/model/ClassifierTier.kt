package com.otpforwarder.domain.model

enum class ClassifierTier {
    /** Pre-classification stamp — detection ran but classification hasn't. */
    NONE,
    GEMINI_NANO,
    KEYWORD
}
