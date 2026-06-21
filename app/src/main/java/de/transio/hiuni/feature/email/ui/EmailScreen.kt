package de.transio.hiuni.feature.email.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import de.transio.hiuni.feature.email.EmailViewModel

@Composable
fun EmailScreen(viewModel: EmailViewModel = hiltViewModel()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("E-Mail", style = MaterialTheme.typography.headlineLarge)
        Text(
            "IMAP via Jakarta Mail in Phase 3.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
