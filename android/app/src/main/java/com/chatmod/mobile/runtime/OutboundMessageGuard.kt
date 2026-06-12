package com.chatmod.mobile.runtime

data class OutboundMessageValidation(
    val acceptedText: String?,
    val rejectionReason: String?
) {
    val accepted: Boolean = acceptedText != null
}

class OutboundMessageGuard(
    private val maxLength: Int = 200
) {
    init {
        require(maxLength > 0) { "maxLength must be positive" }
    }

    fun validate(text: String): OutboundMessageValidation {
        val trimmed = text.trim()
        return when {
            trimmed.isBlank() -> rejected("blank")
            trimmed.length > maxLength -> rejected("too_long")
            trimmed.any { character -> character.code in 0..31 || character.code == 127 } -> {
                rejected("control_characters")
            }
            else -> OutboundMessageValidation(trimmed, null)
        }
    }

    private fun rejected(reason: String): OutboundMessageValidation {
        return OutboundMessageValidation(
            acceptedText = null,
            rejectionReason = reason
        )
    }
}
