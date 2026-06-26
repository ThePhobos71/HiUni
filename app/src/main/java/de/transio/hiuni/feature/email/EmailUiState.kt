package de.transio.hiuni.feature.email

import de.transio.hiuni.feature.email.data.EmailAttachment
import de.transio.hiuni.feature.email.data.EmailEntity
import de.transio.hiuni.feature.email.data.IcsInvite

enum class EmailFolder { INBOX, SENT, STARRED }

data class EmailUiState(
    val folder: EmailFolder = EmailFolder.INBOX,
    val emails: List<EmailEntity> = emptyList(),
    val selectedEmail: EmailEntity? = null,
    val selectedBodyPlain: String? = null,
    val selectedBodyHtml: String? = null,
    val selectedAttachments: List<EmailAttachment> = emptyList(),
    val selectedInvite: IcsInvite? = null,
    val isRefreshing: Boolean = false,
    val isLoadingBody: Boolean = false,
    val downloadingPartIndex: Int? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val hasCredentials: Boolean = false
) {
    val unreadCount: Int get() = emails.count { !it.isRead }
}
