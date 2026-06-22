package de.transio.hiuni.feature.email

import de.transio.hiuni.feature.email.data.EmailAttachment
import de.transio.hiuni.feature.email.data.EmailEntity

enum class EmailFolder { INBOX, STARRED }

data class EmailUiState(
    val folder: EmailFolder = EmailFolder.INBOX,
    val emails: List<EmailEntity> = emptyList(),
    val selectedEmail: EmailEntity? = null,
    val selectedBodyPlain: String? = null,
    val selectedBodyHtml: String? = null,
    val selectedAttachments: List<EmailAttachment> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingBody: Boolean = false,
    val downloadingPartIndex: Int? = null,
    val errorMessage: String? = null,
    val hasCredentials: Boolean = false
) {
    val unreadCount: Int get() = emails.count { !it.isRead }
}
