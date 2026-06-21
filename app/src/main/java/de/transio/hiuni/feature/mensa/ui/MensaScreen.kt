package de.transio.hiuni.feature.mensa.ui

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
import de.transio.hiuni.feature.mensa.MensaViewModel

@Composable
fun MensaScreen(viewModel: MensaViewModel = hiltViewModel()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mensa", style = MaterialTheme.typography.headlineLarge)
        Text(
            "STW-ON-API + Room-Cache in Phase 2.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
