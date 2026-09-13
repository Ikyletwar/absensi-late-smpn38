package com.osis.smkn1malteng.absensilate

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import com.osis.smkn1malteng.absensilate.data.model.SmpClass
import com.osis.smkn1malteng.absensilate.data.model.SmpSubClass
import com.osis.smkn1malteng.absensilate.network.SyncClient
import com.osis.smkn1malteng.absensilate.sync.CompactSerializer
import com.osis.smkn1malteng.absensilate.sync.QrSyncEngine
import com.osis.smkn1malteng.absensilate.sync.RosterBackup
import com.osis.smkn1malteng.absensilate.ui.screens.DashboardScreen
import com.osis.smkn1malteng.absensilate.ui.screens.InputFormScreen
import com.osis.smkn1malteng.absensilate.ui.screens.RecapScreen
import com.osis.smkn1malteng.absensilate.ui.screens.SplashScreen
import com.osis.smkn1malteng.absensilate.ui.screens.components.*
import com.osis.smkn1malteng.absensilate.ui.theme.AbsensiLateTheme
import com.osis.smkn1malteng.absensilate.ui.theme.ThemeManager
import com.osis.smkn1malteng.absensilate.ui.viewmodel.MainViewModel
import com.osis.smkn1malteng.absensilate.util.RecapExporter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val nearbyGranted = permissions[Manifest.permission.NEARBY_WIFI_DEVICES] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (nearbyGranted) {
                // Permission granted, proceed
            } else {
                Toast.makeText(
                    this,
                    "Izin Perangkat di Sekitar diperlukan untuk fitur ini",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            if (locationGranted) {
                // Permission granted
            } else {
                Toast.makeText(
                    this,
                    "Izin Lokasi diperlukan untuk fitur ini",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("MainActivity", "onCreate: starting")
        installSplashScreen()
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate: splash installed, setting content")

        setContent {
            AbsensiLateTheme(darkTheme = ThemeManager.isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var showSplash by remember { mutableStateOf(true) }

                    if (showSplash) {
                        SplashScreen(
                            onTimeout = {
                                showSplash = false
                            }
                        )
                    } else {
                        App()
                    }
                }
            }
        }
    }

    // ============================================================
    // 🔥 CEK KONEKSI INTERNET (Android 5 - 16+)
    // ============================================================
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    fun checkAndRequestWifiPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
                )
                false
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                )
                false
            }
        }
    }

    fun isWifiEnabled(): Boolean {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}

// ============================================================
// 🔥 COMPOSABLE App()
// ============================================================
@Composable
fun App() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val viewModel: MainViewModel = viewModel()
    val students by viewModel.students.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // 🔥 Error overlay: show crash info if errorMessage is set
    if (uiState.errorMessage != null) {
        Log.e("App", "Error state: ${uiState.errorMessage}")
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(uiState.errorMessage!!) },
            confirmButton = {
                Button(onClick = { viewModel.dismissError() }) {
                    Text("OK")
                }
            }
        )
    }

    var showForm by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<StudentEntity?>(null) }

    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedStudent by remember { mutableStateOf<StudentEntity?>(null) }

    // ============================================
    // 🔥 DIALOG KIRIM KE SERVER
    // ============================================
    var showSendServerDialog by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var showNoInternetDialog by remember { mutableStateOf(false) }

    // ============================================
    // 🔥 STATE HAPUS SEMUA / RESTORE NAMA SISWA
    // ============================================
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var rosterToRestore by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }

    fun onSendToServerClick() {
        showSendServerDialog = true
    }

    fun performSendToServer() {
        showSendServerDialog = false

        // Cek internet
        if (activity == null || !activity.isNetworkAvailable(context)) {
            showNoInternetDialog = true
            return
        }

        if (students.isEmpty()) {
            Toast.makeText(context, "Tidak ada data siswa untuk dikirim", Toast.LENGTH_SHORT).show()
            return
        }

        isSending = true
        scope.launch {
            val success = viewModel.sendAllStudentsToServer(context)
            isSending = false
            if (success) {
                Toast.makeText(context, "✅ Data berhasil dikirim ke server", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "❌ Gagal mengirim data. Periksa token bot atau koneksi.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ============================================
    // 🔥 QR HANDLER
    // ============================================
    fun handleQrResult(qrData: String) {
        scope.launch {
            try {
                val client = SyncClient(context)
                val handshake = client.parseCombinedQr(qrData)

                if (handshake != null) {
                    Toast.makeText(context, "🔗 Handshake QR detected, connecting...", Toast.LENGTH_SHORT).show()
                    viewModel.importViaHandshakeAuto(qrData)
                    return@launch
                }

                if (CompactSerializer.isLegacyFormat(qrData)) {
                    viewModel.importQrData(qrData)
                    return@launch
                }

                val students = CompactSerializer.deserialize(qrData)
                if (students.isNotEmpty()) {
                    viewModel.importQrData(qrData)
                    return@launch
                }

                Toast.makeText(context, "⚠️ Format QR tidak dikenali", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
                viewModel.dismissError()
            }
        }
    }

    fun processImageQr(bitmap: android.graphics.Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val result = barcodes[0].rawValue ?: barcodes[0].displayValue
                    if (!result.isNullOrBlank()) {
                        handleQrResult(result)
                    } else {
                        Toast.makeText(context, "QR kosong", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Tidak ada QR terdeteksi", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Gagal scan: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ============================================
    // LAUNCHER UNTUK QR SCAN (GALERI)
    // ============================================
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        processImageQr(bitmap)
                    } else {
                        Toast.makeText(context, "Gagal membaca gambar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============================================
    // LAUNCHER UNTUK EXPORT/IMPORT CSV
    // ============================================
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportCsv(it, context)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importCsv(it, context)
        }
    }

    // ============================================
    // 🔥 LAUNCHER UNTUK IMPORT JSON.GZ
    // ============================================
    val importJsonGzLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // 🔥 PERBAIKAN: Panggil suspend function di dalam coroutine
            scope.launch {
                viewModel.importFromJsonGz(it, context)
            }
        }
    }

    fun onImportJsonGzClick() {
        importJsonGzLauncher.launch("application/gzip")
    }

    // ============================================
    // 🔥 LAUNCHER BACKUP / RESTORE NAMA SISWA
    // ============================================
    val rosterExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportRoster(it, context)
        }
    }

    val rosterImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val roster = viewModel.readRosterForRestore(it, context)
                if (roster.isNotEmpty()) {
                    rosterToRestore = roster
                } else {
                    Toast.makeText(
                        context,
                        "⚠️ File tidak berisi data roster yang valid",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun onBackupRosterClick() {
        rosterExportLauncher.launch(RosterBackup.suggestedBackupFileName())
    }

    fun onRestoreRosterClick() {
        rosterImportLauncher.launch("*/*")
    }

    fun onDeleteAllClick() {
        showDeleteAllDialog = true
    }

    // ============================================
    // FUNGSI BANTU
    // ============================================
    fun openEditForm(student: StudentEntity) {
        editingStudent = student
        isEditMode = true
        showForm = true
    }

    fun openAddForm() {
        editingStudent = null
        isEditMode = false
        showForm = true
    }

    fun openDetailDialog(student: StudentEntity) {
        selectedStudent = student
        showDetailDialog = true
    }

    fun onQrExportClick() {
        if (activity == null) {
            Toast.makeText(context, "Activity tidak tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        if (!activity.checkAndRequestWifiPermissions()) {
            return
        }

        if (!activity.isWifiEnabled()) {
            Toast.makeText(context, "Mohon nyalakan Wi-Fi terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        if (!viewModel.isSyncServerActive()) {
            viewModel.startSyncServer()
        } else {
            viewModel.generateQrExport()
        }
    }

    fun onQrImportClick() {
        if (activity != null) {
            try {
                val scanner = GmsBarcodeScanning.getClient(activity)
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        val result = barcode.rawValue ?: barcode.displayValue
                        if (!result.isNullOrBlank()) {
                            handleQrResult(result)
                        } else {
                            Toast.makeText(context, "QR kosong", Toast.LENGTH_SHORT).show()
                            viewModel.dismissError()
                        }
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(context, "Gagal scan: ${exception.message}", Toast.LENGTH_SHORT).show()
                        viewModel.dismissError()
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                viewModel.dismissError()
            }
        } else {
            Toast.makeText(context, "Activity tidak tersedia", Toast.LENGTH_SHORT).show()
            viewModel.dismissError()
        }
    }

    // ============================================
    // MODAL-MODAL & DIALOG
    // ============================================

    if (uiState.showQrExportDialog) {
        val isHandshake = uiState.qrEncodedData.startsWith("WIFI:")
        val credentials = if (isHandshake) {
            viewModel.getHandshakeCredentials()
        } else null

        QrExportDialog(
            encodedData = uiState.qrEncodedData,
            studentCount = uiState.qrStudentCount,
            isLoading = uiState.isLoading,
            isHandshake = isHandshake,
            ssid = credentials?.first,
            password = credentials?.second,
            onStartServer = { viewModel.startSyncServer() },
            onStopServer = { viewModel.stopSyncServer() },
            onDismiss = { viewModel.closeQrExportDialog() }
        )
    }

    if (uiState.showViolationDialog && uiState.selectedStudent != null) {
        ViolationConfirmationDialog(
            student = uiState.selectedStudent!!,
            onConfirm = { action -> viewModel.handleViolationAction(action, context) },
            onDismiss = { viewModel.clearViolationState() }
        )
    }

    if (uiState.showWaliKelasPhoneBottomSheet && uiState.selectedStudent != null) {
        ParentPhoneBottomSheet(
            studentName = uiState.selectedStudent!!.name,
            onSave = { phone, ctx -> viewModel.saveWaliKelasPhoneAndSendWa(phone, ctx) },
            onDismiss = { viewModel.clearViolationState() },
            context = context
        )
    }

    if (showDetailDialog && selectedStudent != null) {
        StudentDetailDialog(
            student = selectedStudent!!,
            maxViolation = uiState.maxViolation,
            onDismiss = {
                showDetailDialog = false
                selectedStudent = null
            },
            viewModel = viewModel
        )
    }

    // ============================================
    // 🔥 DIALOG KIRIM KE SERVER
    // ============================================
    if (showSendServerDialog) {
        AlertDialog(
            onDismissRequest = { showSendServerDialog = false },
            title = { Text("Kirim Ke Server") },
            text = {
                Text("Kirim Semua data Siswa Ke Server Nihongo?")
            },
            confirmButton = {
                Button(
                    onClick = { performSendToServer() },
                    enabled = !isSending
                ) {
                    Text(if (isSending) "Mengirim..." else "Ya")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showSendServerDialog = false },
                    enabled = !isSending
                ) {
                    Text("Tidak")
                }
            }
        )
    }

    // ============================================
    // 🔥 DIALOG TIDAK ADA INTERNET
    // ============================================
    if (showNoInternetDialog) {
        AlertDialog(
            onDismissRequest = { showNoInternetDialog = false },
            title = { Text("Tidak Ada Koneksi Internet") },
            text = {
                Text("Harap nyalakan Wi-Fi atau data seluler untuk mengirim data ke server.")
            },
            confirmButton = {
                Button(
                    onClick = { showNoInternetDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }

    // ============================================
    // 🔥 DIALOG HAPUS SEMUA SISWA (wajib ketik "HAPUS")
    // ============================================
    if (showDeleteAllDialog) {
        var confirmText by remember { mutableStateOf("") }
        val isConfirmed = confirmText.trim() == "HAPUS"

        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Hapus Semua Siswa", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text(
                        "Tindakan ini MENGHAPUS SEMUA data siswa dan mengembalikan " +
                        "pengaturan ke default (Max Pelanggaran = 2). Tindakan ini " +
                        "TIDAK BISA dibatalkan."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Ketik HAPUS untuk melanjutkan:")
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.deleteAllStudentsAndResetConfig(context)
                    },
                    enabled = isConfirmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // ============================================
    // 🔥 DIALOG PRA-KONFIRMASI RESTORE (GANTI TOTAL)
    // ============================================
    if (rosterToRestore.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { rosterToRestore = emptyList() },
            title = { Text("Restore Nama Siswa") },
            text = {
                Text(
                    "File backup berisi ${rosterToRestore.size} siswa. " +
                    "Semua siswa saat ini (${students.size} siswa) akan DIGANTI TOTAL. " +
                    "Lanjutkan restore?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val roster = rosterToRestore
                        rosterToRestore = emptyList()
                        viewModel.replaceAllStudents(roster, context)
                    }
                ) {
                    Text("Ya, Ganti Total")
                }
            },
            dismissButton = {
                TextButton(onClick = { rosterToRestore = emptyList() }) {
                    Text("Batal")
                }
            }
        )
    }

    // ============================================
    // UI UTAMA
    // ============================================
    if (uiState.showRecapScreen) {
        RecapScreen(
            students = viewModel.getSelectedStudentsForRecap(),
            maxViolation = uiState.maxViolation,
            onBack = { viewModel.closeRecapScreen() },
            onExportCsv = {
                val students = viewModel.getSelectedStudentsForRecap()
                val uri = RecapExporter.exportRecapCsv(context, students)
                if (uri != null) {
                    Toast.makeText(context, "✅ CSV berhasil disimpan ke Downloads", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "❌ Gagal export CSV", Toast.LENGTH_SHORT).show()
                }
            },
            onExportPdf = {
                val students = viewModel.getSelectedStudentsForRecap()
                val uri = RecapExporter.exportRecapPdf(context, students)
                if (uri != null) {
                    Toast.makeText(context, "✅ PDF berhasil disimpan ke Downloads", Toast.LENGTH_LONG).show()
                    // Optional: auto-open PDF
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Silent fail — file sudah tersimpan
                    }
                } else {
                    Toast.makeText(context, "❌ Gagal export PDF", Toast.LENGTH_SHORT).show()
                }
            }
        )
    } else {
        if (showForm) {
            InputFormScreen(
                initialName = editingStudent?.name ?: "",
                initialPhone = editingStudent?.phone ?: "",
                initialWaliKelasPhone = editingStudent?.waliKelasPhone ?: "",
                initialKelas = editingStudent?.kelas ?: SmpClass.VII,
                initialSubKelas = editingStudent?.subKelas ?: SmpSubClass.SUB_1,
                initialTimestamp = System.currentTimeMillis(),
                isEditMode = isEditMode,
                onSave = { name, phone, waliKelasPhone, kelas, subKelas, timestamp ->
                    if (isEditMode && editingStudent != null) {
                        viewModel.editStudent(
                            id = editingStudent!!.id,
                            name = name,
                            phone = phone,
                            waliKelasPhone = waliKelasPhone,
                            kelas = kelas,
                            subKelas = subKelas
                        )
                    } else {
                        viewModel.addStudent(name, phone, waliKelasPhone, kelas, subKelas, timestamp)
                    }
                    showForm = false
                },
                onCancel = { showForm = false }
            )
        } else {
            DashboardScreen(
                students = students,
                pendingViolations = uiState.pendingViolations,
                onAddClick = { openAddForm() },
                onResetClick = { viewModel.resetAllCounters() },
                onExportClick = {
                    val fileName = "Data_Siswa_${System.currentTimeMillis()}.csv"
                    exportLauncher.launch(fileName)
                },
                onImportClick = {
                    importLauncher.launch("*/*")
                },
                onQrExportClick = { onQrExportClick() },
                onQrImportClick = { onQrImportClick() },
                onQrImportGalleryClick = {
                    galleryLauncher.launch("image/*")
                },
                onStudentClick = { student -> openDetailDialog(student) },
                onEditClick = { student -> openEditForm(student) },
                onPendingClick = { student -> viewModel.retryPendingViolation(student) },
                onAddViolationClick = { student ->
                    viewModel.addViolationDirectly(student.id, context)
                },
                onSendToServerClick = { onSendToServerClick() },
                onImportJsonGzClick = { onImportJsonGzClick() },
                onBackupRosterClick = { onBackupRosterClick() },
                onRestoreRosterClick = { onRestoreRosterClick() },
                onDeleteAllClick = { onDeleteAllClick() },
                selectedStudentIds = uiState.selectedStudentIds,
                onToggleSelection = { id -> viewModel.toggleStudentSelection(id) },
                onOpenRecap = { viewModel.openRecapScreen() },
                maxViolation = uiState.maxViolation,
                onMaxViolationChange = { value -> viewModel.updateMaxViolation(value) },
                viewModel = viewModel
            )
        }
    }
}
