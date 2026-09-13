package com.osis.smkn1malteng.absensilate.ui.screens.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions  // <-- PERBAIKAN
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType  // <-- PERBAIKAN
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentPhoneBottomSheet(
    studentName: String,
    onSave: (String, Context) -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    var parentPhone by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Nomor Wali Kelas Wajib",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Untuk siswa: $studentName",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Masukkan nomor WhatsApp wali kelas untuk mengirim notifikasi:",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = parentPhone,
                onValueChange = {
                    if (it.all { char -> char.isDigit() }) {
                        parentPhone = it
                    }
                },
                label = { Text("Nomor WhatsApp Wali Kelas") },
                placeholder = { Text("Contoh: 081234567890") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (parentPhone.isNotBlank()) {
                        onSave(parentPhone, context)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = parentPhone.isNotBlank()
            ) {
                Text("Kirim WhatsApp & Simpan")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Nomor akan disimpan untuk digunakan di kemudian hari",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}