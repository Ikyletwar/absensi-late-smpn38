package com.osis.smkn1malteng.absensilate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(
    students: List<StudentEntity>,
    maxViolation: Int,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit
) {
    val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale("id", "ID"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rekap Pelanggaran (${students.size} Siswa)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = onExportCsv) {
                        Icon(Icons.Default.TableChart, contentDescription = "Export CSV")
                    }
                    IconButton(onClick = onExportPdf) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (students.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Tidak ada siswa dipilih", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(students, key = { it.id }) { student ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (student.violationCount >= maxViolation)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "${student.name}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Kelas: ${student.kelas.display}-${student.subKelas.display}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pelanggaran minggu ini: ${student.violationCount}x",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (student.violationCount >= maxViolation)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Total pelanggaran: ${student.timestamps.size}x",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (student.timestamps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Riwayat Timestamp:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                student.timestamps.reversed().forEach { ts ->
                                    Text(
                                        text = "• ${dateFormat.format(Date(ts))}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
