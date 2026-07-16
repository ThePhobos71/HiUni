package de.transio.hiuni.feature.email

import androidx.lifecycle.SavedStateHandle
import de.transio.hiuni.core.common.AppResult
import de.transio.hiuni.core.security.CredentialsManager
import de.transio.hiuni.feature.email.data.EmailContact
import de.transio.hiuni.feature.email.data.EmailRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fokus: Der Entwurf muss Prozess-Tod überleben (SavedStateHandle) und beim bewussten
 * Verwerfen / erfolgreichem Senden geräumt werden. Prozess-Tod simulieren wir, indem wir
 * ein zweites ViewModel mit DEMSELBEN SavedStateHandle erzeugen — genau das tut die
 * ViewModel-SavedState-Machinerie nach einem System-Kill.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmailComposeViewModelTest {

    /** Minimaler Fake, der nur die vom Compose-VM benutzten Methoden bedient. */
    private class FakeEmailRepository : EmailRepository {
        val contactsFlow = MutableStateFlow<List<EmailContact>>(emptyList())
        var sendResult: AppResult<Unit> = AppResult.Success(Unit)
        var sendCalls = 0
        var lastTo: List<String>? = null
        var lastSubject: String? = null
        var lastBody: String? = null

        override fun observeInbox() = MutableStateFlow(emptyList<de.transio.hiuni.feature.email.data.EmailEntity>())
        override fun observeSent() = MutableStateFlow(emptyList<de.transio.hiuni.feature.email.data.EmailEntity>())
        override fun observeArchived() = MutableStateFlow(emptyList<de.transio.hiuni.feature.email.data.EmailEntity>())
        override fun observeStarred() = MutableStateFlow(emptyList<de.transio.hiuni.feature.email.data.EmailEntity>())
        override fun observeSearch(folder: EmailFolder, query: String) =
            MutableStateFlow(emptyList<de.transio.hiuni.feature.email.data.EmailEntity>())

        override fun observeKnownContacts(): Flow<List<EmailContact>> = contactsFlow
        override suspend fun loadBody(rowId: Long) = null
        override suspend fun loadIcsInvite(
            email: de.transio.hiuni.feature.email.data.EmailEntity,
            attachment: de.transio.hiuni.feature.email.data.EmailAttachment
        ) = null

        override suspend fun markRead(rowId: Long, read: Boolean) {}
        override suspend fun toggleStar(email: de.transio.hiuni.feature.email.data.EmailEntity) {}
        override suspend fun downloadAttachment(
            email: de.transio.hiuni.feature.email.data.EmailEntity,
            attachment: de.transio.hiuni.feature.email.data.EmailAttachment
        ): java.io.File = throw UnsupportedOperationException()

        override suspend fun shareableUri(file: java.io.File): android.net.Uri =
            throw UnsupportedOperationException()

        override suspend fun refresh(force: Boolean): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun sendMail(
            to: List<String>,
            cc: List<String>,
            bcc: List<String>,
            subject: String,
            body: String,
            inReplyTo: String?,
            references: String?
        ): AppResult<Unit> {
            sendCalls += 1
            lastTo = to
            lastSubject = subject
            lastBody = body
            return sendResult
        }

        override suspend fun deleteEmail(email: de.transio.hiuni.feature.email.data.EmailEntity): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun archiveEmail(email: de.transio.hiuni.feature.email.data.EmailEntity): AppResult<Unit> =
            AppResult.Success(Unit)
    }

    private val repository = FakeEmailRepository()
    private val credentials = mockk<CredentialsManager>()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { credentials.hasCredentials() } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(
        handle: SavedStateHandle = SavedStateHandle(),
        holder: EmailComposePrefillHolder = EmailComposePrefillHolder()
    ) = EmailComposeViewModel(repository, credentials, handle, holder)

    @Test
    fun `Entwurf ueberlebt Prozess-Tod ueber gemeinsamen SavedStateHandle`() {
        val handle = SavedStateHandle()
        val vm1 = newVm(handle)
        vm1.updateToDraft("max@uni-hildesheim.de")
        vm1.commitToChip()
        vm1.updateSubject("Wichtig")
        vm1.updateBody("Hallo Welt")

        // Prozess-Tod: neues VM, gleicher Handle, frischer (leerer) Holder.
        val vm2 = newVm(handle, EmailComposePrefillHolder())
        val restored = vm2.state.value

        assertEquals(listOf("max@uni-hildesheim.de"), restored.toChips)
        assertEquals("Wichtig", restored.subject)
        assertEquals("Hallo Welt", restored.body)
    }

    @Test
    fun `CC BCC und noch-nicht-committeter Draft ueberleben Prozess-Tod`() {
        val handle = SavedStateHandle()
        val vm1 = newVm(handle)
        vm1.toggleCcBcc()
        vm1.updateCcDraft("cc@uni-hildesheim.de")
        vm1.commitCcChip()
        vm1.updateBccDraft("bcc@uni-hildesheim.de")
        vm1.commitBccChip()
        // Bewusst NICHT committen — Halb-getippte Adresse muss auch zurückkommen.
        vm1.updateToDraft("halb@getippt")

        val vm2 = newVm(handle, EmailComposePrefillHolder())
        val s = vm2.state.value

        assertTrue(s.showCcBcc)
        assertEquals(listOf("cc@uni-hildesheim.de"), s.ccChips)
        assertEquals(listOf("bcc@uni-hildesheim.de"), s.bccChips)
        assertEquals("halb@getippt", s.toDraft)
    }

    @Test
    fun `Reply-Prefill aus dem Holder ueberlebt Prozess-Tod im Handle`() {
        val holder = EmailComposePrefillHolder()
        holder.set(
            EmailComposePrefill(
                to = listOf("absender@uni-hildesheim.de"),
                subject = "Re: Frage",
                body = "\n\n\n> Original",
                inReplyTo = "<abc@server>",
                references = "<abc@server>"
            )
        )
        val handle = SavedStateHandle()
        val vm1 = newVm(handle, holder)
        assertEquals("Re: Frage", vm1.state.value.subject)
        assertEquals("<abc@server>", vm1.state.value.inReplyTo)

        // Prozess-Tod: Holder ist bereits konsumiert (leer), Handle trägt den Prefill.
        val vm2 = newVm(handle, holder)
        val s = vm2.state.value
        assertEquals(listOf("absender@uni-hildesheim.de"), s.toChips)
        assertEquals("Re: Frage", s.subject)
        assertEquals("<abc@server>", s.inReplyTo)
        assertEquals("<abc@server>", s.references)
    }

    @Test
    fun `Verwerfen raeumt Handle - neues VM startet leer`() {
        val handle = SavedStateHandle()
        val vm1 = newVm(handle)
        vm1.updateSubject("Verwerfen mich")
        vm1.discardDraft()

        assertEquals("", vm1.state.value.subject)
        assertFalse(vm1.state.value.isDirty)

        val vm2 = newVm(handle, EmailComposePrefillHolder())
        assertFalse(vm2.state.value.isDirty)
        assertEquals("", vm2.state.value.subject)
    }

    @Test
    fun `erfolgreiches Senden raeumt den gesicherten Entwurf`() = runTest {
        val handle = SavedStateHandle()
        val vm1 = newVm(handle)
        vm1.updateToDraft("max@uni-hildesheim.de")
        vm1.commitToChip()
        vm1.updateSubject("Hallo")
        vm1.updateBody("Body")

        vm1.send()
        advanceUntilIdle()

        assertEquals(1, repository.sendCalls)
        assertEquals("Mail gesendet.", vm1.state.value.sentMessage)

        // Nach Senden Prozess-Tod → darf die gesendete Mail NICHT wiederbeleben.
        val vm2 = newVm(handle, EmailComposePrefillHolder())
        assertFalse(vm2.state.value.isDirty)
        assertEquals("", vm2.state.value.subject)
        assertNull(vm2.state.value.sentMessage)
    }

    @Test
    fun `fehlgeschlagenes Senden behaelt den Entwurf im Handle`() = runTest {
        repository.sendResult = AppResult.Failure(RuntimeException("SMTP weg"))
        val handle = SavedStateHandle()
        val vm1 = newVm(handle)
        vm1.updateToDraft("max@uni-hildesheim.de")
        vm1.commitToChip()
        vm1.updateSubject("Behalt mich")

        vm1.send()
        advanceUntilIdle()

        assertFalse(vm1.state.value.isSending)
        assertTrue(vm1.state.value.errorMessage!!.contains("SMTP weg"))

        // Entwurf ist im Handle → übersteht Prozess-Tod, Fehler-/Sende-Flags NICHT.
        val vm2 = newVm(handle, EmailComposePrefillHolder())
        val s = vm2.state.value
        assertEquals("Behalt mich", s.subject)
        assertEquals(listOf("max@uni-hildesheim.de"), s.toChips)
        assertNull(s.errorMessage)
        assertFalse(s.isSending)
    }

    @Test
    fun `transiente Sende-Meldung landet nicht im Handle`() = runTest {
        val handle = SavedStateHandle()
        val vm1 = newVm(handle)
        vm1.updateToDraft("max@uni-hildesheim.de")
        vm1.commitToChip()
        vm1.send()
        advanceUntilIdle()
        // sentMessage ist gesetzt, aber der Handle wurde beim Erfolg geräumt.
        assertEquals("Mail gesendet.", vm1.state.value.sentMessage)
        val vm2 = newVm(handle, EmailComposePrefillHolder())
        assertNull(vm2.state.value.sentMessage)
    }
}
