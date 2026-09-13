package com.osis.smkn1malteng.absensilate.ui.screens.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import com.osis.smkn1malteng.absensilate.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudentDetailDialog(
    student: StudentEntity,
    maxViolation: Int,
    onDismiss: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm:ss", Locale("id", "ID"))
    var showResetConfirmation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Detail Siswa", style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Nama: ${student.name}", style = MaterialTheme.typography.titleMedium)
                Text("Kelas: ${student.kelas.display}-${student.subKelas.display}", style = MaterialTheme.typography.bodyMedium)
                Text("No. HP: ${student.phone}", style = MaterialTheme.typography.bodyMedium)
                Text("No. HP Wali Kelas: ${student.waliKelasPhone ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Pelanggaran minggu ini: ${student.violationCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (student.violationCount >= maxViolation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text("Total pelanggaran: ${student.timestamps.size}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))

                // ============================================
                // 🔥 TOMBOL AKSI PELANGGARAN
                // ============================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tombol Kurangi Pelanggaran
                    OutlinedButton(
                        onClick = {
                            viewModel.decrementViolation(student.id, context)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = student.violationCount > 0
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kurangi")
                    }

                    // Tombol Reset Pelanggaran (per siswa)
                    OutlinedButton(
                        onClick = { showResetConfirmation = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // ============================================
                // 🔥 DAFTAR TIMESTAMP
                // ============================================
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Riwayat Waktu Kejadian:", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (student.timestamps.isEmpty()) {
                    Text(
                        "Belum ada catatan pelanggaran.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    ) {
                        items(student.timestamps.reversed()) { timestamp ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "• ${dateFormat.format(Date(timestamp))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                // Tombol hapus timestamp
                                IconButton(
                                    onClick = {
                                        viewModel.deleteViolationTimestamp(student.id, timestamp, context)
                                        onDismiss()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus catatan",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ============================================
                // 🔥 DIALOG KONFIRMASI RESET
                // ============================================
                if (showResetConfirmation) {
                    AlertDialog(
                        onDismissRequest = { showResetConfirmation = false },
                        title = { Text("Reset Pelanggaran Siswa?") },
                        text = {
                            Text(
                                "Semua catatan pelanggaran untuk ${student.name} akan dihapus. Tindakan ini tidak dapat dibatalkan."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.resetSingleStudent(student.id, context)
                                    showResetConfirmation = false
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Reset")
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showResetConfirmation = false }) {
                                Text("Batal")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Tutup")
            }
        }
    )
}
