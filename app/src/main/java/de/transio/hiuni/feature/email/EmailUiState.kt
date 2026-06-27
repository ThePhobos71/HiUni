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
    val hasCredentials: Boolean = false,
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val swipeRightAction: MailSwipeAction = MailSwipeAction.DEFAULT_RIGHT,
    val swipeLeftAction: MailSwipeAction = MailSwipeAction.DEFAULT_LEFT,
    val requiresBiometric: Boolean = false,
    val isUnlocked: Boolean = true
) {
    /** Lock-Wall zeigen sobald Setting an UND noch nicht entsperrt. */
    val isLocked: Boolean get() = requiresBiometric && !isUnlocked
    val unreadCount: Int get() = emails.count { !it.isRead }
    val isSearchActive: Boolean get() = isSearchOpen && searchQuery.isNotBlank()
}
