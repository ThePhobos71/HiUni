package de.transio.hiuni.feature.email

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.transio.hiuni.core.security.CredentialsManager
import javax.inject.Inject

@HiltViewModel
class EmailViewModel @Inject constructor(
    @Suppress("unused") private val credentialsManager: CredentialsManager
) : ViewModel()
