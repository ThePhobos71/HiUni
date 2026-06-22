package de.transio.hiuni.feature.email.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EmailAttachment(
    val partIndex: Int,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long
)

object EmailAttachments {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(list: List<EmailAttachment>): String? =
        if (list.isEmpty()) null else json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(EmailAttachment.serializer()),
            list
        )

    fun decode(stored: String?): List<EmailAttachment> =
        if (stored.isNullOrBlank()) emptyList()
        else runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(EmailAttachment.serializer()),
                stored
            )
        }.getOrDefault(emptyList())
}
