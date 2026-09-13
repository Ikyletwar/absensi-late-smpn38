package com.osis.smkn1malteng.absensilate.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import com.osis.smkn1malteng.absensilate.data.model.SmpClass
import com.osis.smkn1malteng.absensilate.data.model.SmpSubClass
import com.osis.smkn1malteng.absensilate.ui.screens.components.AboutDialog
import com.osis.smkn1malteng.absensilate.ui.screens.components.StudentDetailDialog
import com.osis.smkn1malteng.absensilate.ui.theme.ThemeManager
import com.osis.smkn1malteng.absensilate.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

enum class SortOrder {
    NEWEST, OLDEST, NAME_ASC, NAME_DESC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    students: List<StudentEntity>,
    pendingViolations: List<StudentEntity>,
    onAddClick: () -> Unit,
    onResetClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onQrExportClick: () -> Unit,
    onQrImportClick: () -> Unit,
    onQrImportGalleryClick: () -> Unit,
    onStudentClick: (StudentEntity) -> Unit,
    onEditClick: (StudentEntity) -> Unit,
    onPendingClick: (StudentEntity) -> Unit,
    onAddViolationClick: (StudentEntity) -> Unit,
    onSendToServerClick: () -> Unit,
    onImportJsonGzClick: () -> Unit,
    onBackupRosterClick: () -> Unit,
    onRestoreRosterClick: () -> Unit,
    onDeleteAllClick: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    selectedStudentIds: Set<Long>,
    onOpenRecap: () -> Unit,
    maxViolation: Int,
    onMaxViolationChange: (Int) -> Unit,
    viewModel: MainViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    var rawQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selectedKelas by remember { mutableStateOf<SmpClass?>(null) }
    var selectedSubKelas by remember { mutableStateOf<SmpSubClass?>(null) }
    var sortOrder by remember { mutableStateOf(SortOrder.NEWEST) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // ============================================
    // 🔥 MODE SELECT + PENGAMAN (semua dialog lokal)
    // ============================================
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isSelectionMode by remember { mutableStateOf(false) }
    var studentToDelete by remember { mutableStateOf<StudentEntity?>(null) }
    var showBulkAddDialog by remember { mutableStateOf(false) }
    var showExitSelectionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(rawQuery) {
        delay(300)
        debouncedQuery = rawQuery
    }

    val filteredStudents by derivedStateOf {
        students
            .filter { student ->
                val query = debouncedQuery.lowercase()
                query.isEmpty() ||
                        student.name.lowercase().contains(query) ||
                        student.kelas.display.lowercase().contains(query) ||
                        student.subKelas.display.lowercase().contains(query)
            }
            .filter { student -> selectedKelas == null || student.kelas == selectedKelas }
            .filter { student -> selectedSubKelas == null || student.subKelas == selectedSubKelas }
            .sortedWith(comparator = when (sortOrder) {
                SortOrder.NEWEST -> compareByDescending { it.id }
                SortOrder.OLDEST -> compareBy { it.id }
                SortOrder.NAME_ASC -> compareBy { it.name }
                SortOrder.NAME_DESC -> compareByDescending { it.name }
            })
    }

    val allVisibleSelected = filteredStudents.isNotEmpty() &&
            filteredStudents.all { selectedStudentIds.contains(it.id) }

    fun toggleSelection(studentId: Long) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggleSelection(studentId)
    }

    fun handleLongPress(studentId: Long) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (!isSelectionMode) isSelectionMode = true
        onToggleSelection(studentId)
    }

    fun exitSelectionMode() {
        if (selectedStudentIds.isNotEmpty()) {
            showExitSelectionDialog = true
        } else {
            isSelectionMode = false
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            "${selectedStudentIds.size} dipilih",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Keluar mode pilih", tint = Color.White)
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            if (allVisibleSelected) {
                                viewModel.clearStudentSelection()
                            } else {
                                viewModel.selectAllStudents(filteredStudents.map { it.id })
                            }
                        }) {
                            Text(
                                if (allVisibleSelected) "Batal Semua" else "Pilih Semua",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "Absensi Late",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White
                    ),
                    actions = {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export QR") },
                                leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                                onClick = { expanded = false; onQrExportClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Scan QR") },
                                leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                                onClick = { expanded = false; onQrImportClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Pilih Gambar QR") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onQrImportGalleryClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export CSV") },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                onClick = { expanded = false; onExportClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Import CSV") },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                onClick = { expanded = false; onImportClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Data (JSON.GZ)") },
                                leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onImportJsonGzClick()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Kelola Data", fontWeight = FontWeight.Bold) },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = { Text("Backup Nama Siswa") },
                                leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onBackupRosterClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restore Nama Siswa") },
                                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onRestoreRosterClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus Semua Siswa", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    onDeleteAllClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Semua") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = { expanded = false; onResetClick() }
                            )
                            DropdownMenuItem(
                                text = { Text("Kirim Ke Server") },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    onSendToServerClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    showSettingsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    showAboutDialog = true
                                }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White) },
                    text = { Text("Tambah Siswa", color = Color.White) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                isSelectionMode = false
                                onOpenRecap()
                            },
                            enabled = selectedStudentIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rekap (${selectedStudentIds.size})")
                        }
                        Button(
                            onClick = { showBulkAddDialog = true },
                            enabled = selectedStudentIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+1 (${selectedStudentIds.size})")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // PENDING VIOLATIONS
            if (pendingViolations.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pending WA Calls", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        pendingViolations.forEach { student ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onPendingClick(student) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column {
                                            Text("${student.name}", style = MaterialTheme.typography.titleMedium)
                                            Text("Kelas: ${student.kelas.display}-${student.subKelas.display} | Pelanggaran: ${student.violationCount}x", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                text = "Klik untuk kirim WA atau batalkan",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SEARCH
            item {
                OutlinedTextField(
                    value = rawQuery,
                    onValueChange = { rawQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari nama, kelas, atau sub kelas...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // FILTERS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterDropdown(
                        label = "Kelas",
                        options = listOf("Semua") + SmpClass.values().map { it.display },
                        selected = selectedKelas?.display ?: "Semua",
                        onSelected = { value ->
                            selectedKelas = if (value == "Semua") null else SmpClass.values().firstOrNull { it.display == value }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "Sub Kelas",
                        options = listOf("Semua") + SmpSubClass.values().map { it.display },
                        selected = selectedSubKelas?.display ?: "Semua",
                        onSelected = { value ->
                            selectedSubKelas = if (value == "Semua") null else SmpSubClass.values().firstOrNull { it.display == value }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterDropdown(
                        label = "Urut",
                        options = listOf("Terbaru", "Terlama", "Nama A-Z", "Nama Z-A"),
                        selected = when (sortOrder) {
                            SortOrder.NEWEST -> "Terbaru"
                            SortOrder.OLDEST -> "Terlama"
                            SortOrder.NAME_ASC -> "Nama A-Z"
                            SortOrder.NAME_DESC -> "Nama Z-A"
                        },
                        onSelected = { value ->
                            sortOrder = when (value) {
                                "Terbaru" -> SortOrder.NEWEST
                                "Terlama" -> SortOrder.OLDEST
                                "Nama A-Z" -> SortOrder.NAME_ASC
                                "Nama Z-A" -> SortOrder.NAME_DESC
                                else -> SortOrder.NEWEST
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Daftar Siswa", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${filteredStudents.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // STUDENT CARDS
            items(
                items = filteredStudents,
                key = { it.id },
                contentType = { "student_card" }
            ) { student ->
                StudentCard(
                    student = student,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedStudentIds.contains(student.id),
                    maxViolation = maxViolation,
                    onToggleSelection = { toggleSelection(student.id) },
                    onLongClick = { handleLongPress(student.id) },
                    onStudentClick = onStudentClick,
                    onEditClick = onEditClick,
                    onAddViolationClick = onAddViolationClick,
                    onDeleteRequest = { studentToDelete = student }
                )
            }

            // SPACER BAWAH
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // ============================================
    // 🔥 DIALOG PENGAMAN
    // ============================================
    if (showExitSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showExitSelectionDialog = false },
            title = { Text("Keluar dari mode pilih?") },
            text = {
                Text("${selectedStudentIds.size} siswa yang dipilih akan dibatalkan.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearStudentSelection()
                    isSelectionMode = false
                    showExitSelectionDialog = false
                }) {
                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitSelectionDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    studentToDelete?.let { student ->
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            title = { Text("Hapus siswa?") },
            text = {
                Text("'${student.name}' (${student.kelas.display}-${student.subKelas.display}) akan dihapus permanen. Tindakan ini tidak bisa dibatalkan.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStudent(student.id)
                    studentToDelete = null
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showBulkAddDialog) {
        AlertDialog(
            onDismissRequest = { showBulkAddDialog = false },
            title = { Text("Tambah pelanggaran?") },
            text = {
                Text("Tambahkan 1 pelanggaran dan catat waktu saat ini untuk ${selectedStudentIds.size} siswa terpilih?")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addViolationsToStudents(selectedStudentIds.toList(), context)
                    showBulkAddDialog = false
                }) {
                    Text("Ya")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkAddDialog = false }) {
                    Text("Tidak")
                }
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            maxViolation = maxViolation,
            onMaxViolationChange = onMaxViolationChange,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    maxViolation: Int,
    onMaxViolationChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var maxInput by remember { mutableStateOf(maxViolation.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mode Gelap")
                    Switch(
                        checked = ThemeManager.isDarkTheme,
                        onCheckedChange = { ThemeManager.toggleTheme() }
                    )
                }
                Column {
                    Text(
                        "Max Pelanggaran (Panggilan Orang Tua)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = maxInput,
                        onValueChange = { maxInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Jumlah pelanggaran") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Panggilan orang tua otomatis memanggil saat pelanggaran mencapai angka ini. Bebas: 1, 2, 3, dst.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = maxInput.toIntOrNull()
                if (parsed != null && parsed >= 1) {
                    onMaxViolationChange(parsed)
                }
                onDismiss()
            }) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudentCard(
    student: StudentEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    maxViolation: Int,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit,
    onStudentClick: (StudentEntity) -> Unit,
    onEditClick: (StudentEntity) -> Unit,
    onAddViolationClick: (StudentEntity) -> Unit,
    onDeleteRequest: (StudentEntity) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val (containerColor, contentColor) = remember(student.violationCount, maxViolation, colorScheme) {
        when {
            student.violationCount >= maxViolation -> colorScheme.errorContainer to colorScheme.onErrorContainer
            student.violationCount >= 1 -> Color(0xFFFFEFA7) to Color(0xFF4D2A00)
            else -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        }
    }

    val isHighViolation = student.violationCount >= maxViolation
    val textColor = if (isHighViolation) MaterialTheme.colorScheme.onErrorContainer else contentColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelection() else onStudentClick(student)
                },
                onLongClick = onLongClick
            )
            .then(
                if (isSelectionMode && isSelected) {
                    Modifier.border(2.dp, colorScheme.primary, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = textColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSelectionMode) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(24.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Terpilih",
                                    tint = colorScheme.primary
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(2.dp, colorScheme.outlineVariant, CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = "${student.name} (${student.kelas.display}-${student.subKelas.display})",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${student.violationCount}", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.padding(start = 8.dp).size(height = 24.dp, width = 40.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isHighViolation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                        labelColor = if (isHighViolation) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = MaterialTheme.shapes.small,
                    border = null,
                    elevation = null
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Telp: ${student.phone} | Walkes: ${student.waliKelasPhone ?: "-"}",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f)
            )
            Text(
                "Pelanggaran minggu ini: ${student.violationCount}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isHighViolation) textColor.copy(alpha = 0.7f) else if (student.violationCount >= maxViolation) MaterialTheme.colorScheme.error else textColor.copy(alpha = 0.7f)
            )
            Text(
                "Total pelanggaran: ${student.timestamps.size}",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (isSelectionMode) {
                Text(
                    text = "Ketuk untuk pilih / batal",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHighViolation) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onAddViolationClick(student) },
                        modifier = Modifier.padding(end = 4.dp).height(32.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(
                            "+1",
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor
                        )
                    }
                    TextButton(
                        onClick = { onEditClick(student) },
                        modifier = Modifier.padding(end = 4.dp).height(32.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(
                            "Edit",
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor
                        )
                    }
                    TextButton(
                        onClick = { onDeleteRequest(student) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(
                            "Hapus",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isHighViolation) textColor else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Text(
                    text = "Tap untuk detail | Tahan untuk pilih",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isHighViolation) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}