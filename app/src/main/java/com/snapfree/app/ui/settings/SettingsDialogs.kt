package com.snapfree.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.snapfree.app.R
import kotlin.math.roundToInt

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = option == selected, role = Role.RadioButton) {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(label(option), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String = "",
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    monospace: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                supportingText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(placeholder) },
                    singleLine = singleLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (singleLine) Modifier else Modifier.heightIn(min = 160.dp, max = 320.dp)),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = if (monospace) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value); onDismiss() }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
fun SliderDialog(
    title: String,
    initial: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var value by remember { mutableFloatStateOf(initial.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value.roundToInt().toString(), style = MaterialTheme.typography.displaySmall)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = (range.last - range.first - 1).coerceAtLeast(0),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.roundToInt()); onDismiss() }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        shape = MaterialTheme.shapes.extraLarge,
    )
}
