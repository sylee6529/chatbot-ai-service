package com.example.aichatbot.feedback

import com.example.aichatbot.common.BadRequestException
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

enum class FeedbackStatus(val value: String) {
    PENDING("pending"),
    RESOLVED("resolved"),
    ;

    companion object {
        fun parse(value: String): FeedbackStatus =
            entries.firstOrNull { it.value == value.lowercase() }
                ?: throw BadRequestException("status must be pending or resolved")
    }
}

@Converter(autoApply = true)
class FeedbackStatusConverter : AttributeConverter<FeedbackStatus, String> {
    override fun convertToDatabaseColumn(attribute: FeedbackStatus?): String? = attribute?.value

    override fun convertToEntityAttribute(dbData: String?): FeedbackStatus? =
        dbData?.let { FeedbackStatus.parse(it) }
}
