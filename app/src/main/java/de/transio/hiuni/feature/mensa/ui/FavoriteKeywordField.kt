package de.transio.hiuni.feature.mensa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.transio.hiuni.core.design.HiUniColors

/**
 * Eingabezeile für ein Lieblingsgericht-Stichwort: handgestyltes Textfeld plus
 * Aktions-Pill. Genutzt im Gerichtsdetail (Stern) und in den Mensa-Einstellungen.
 *
 * @param rejected zeigt den Hinweis, warum das letzte Stichwort nicht angenommen wurde.
 */
@Composable
internal fun FavoriteKeywordField(
    value: String,
    onValueChange: (String) -> Unit,
    actionLabel: String,
    onSubmit: () -> Unit,
    rejected: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "Stichwort, z.B. Currywurst"
) {
    val colors = MaterialTheme.colorScheme
    val semantics = HiUniColors.semantics
    val shape = RoundedCornerShape(10.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(color = semantics.surfaceAlt, shape = shape, modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    decorationBox = { inner ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = semantics.onSurfaceMuted
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            Surface(
                color = colors.primary,
                shape = shape,
                modifier = Modifier
                    .clip(shape)
                    .clickable(role = Role.Button, onClick = onSubmit)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (rejected) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Mindestens 3 Zeichen, noch nicht vorhanden",
                style = MaterialTheme.typography.bodySmall,
                color = semantics.red
            )
        }
    }
}
