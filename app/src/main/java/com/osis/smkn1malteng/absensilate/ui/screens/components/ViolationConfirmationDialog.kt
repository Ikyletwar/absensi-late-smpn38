package com.osis.smkn1malteng.absensilate.ui.screens.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity

@Composable
fun ViolationConfirmationDialog(
    student: StudentEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Peringatan Pelanggaran",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Siswa ${student.name} telah terlambat ${student.violationCount} kali dalam minggu ini!",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Apakah Anda ingin menghubungi wali kelas via WhatsApp?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "(Jika pilih Tolak, data akan tersimpan sebagai Pending WA Call)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm("Terima") },
                modifier = Modifier.fillMaxWidth(0.4f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Terima")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onConfirm("Tolak") },
                modifier = Modifier.fillMaxWidth(0.4f)
            ) {
                Text("Tolak")
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}