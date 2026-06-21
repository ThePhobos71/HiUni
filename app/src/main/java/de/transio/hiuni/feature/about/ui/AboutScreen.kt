package de.transio.hiuni.feature.about.ui

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

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("HiUni", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Version 0.1.0-foundation",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "Begleit-App für die Uni Hildesheim.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
