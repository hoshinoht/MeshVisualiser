package com.example.meshvisualiser.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.meshvisualiser.models.TransmissionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSegmentedButton(
    selectedMode: TransmissionMode,
    onModeSelected: (TransmissionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        SegmentedButton(
            selected = selectedMode == TransmissionMode.DIRECT,
            onClick = { onModeSelected(TransmissionMode.DIRECT) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) {
            Text("Direct")
        }
        SegmentedButton(
            selected = selectedMode == TransmissionMode.CSMA_CD,
            onClick = { onModeSelected(TransmissionMode.CSMA_CD) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) {
            Text("CSMA/CD")
        }
    }
}
