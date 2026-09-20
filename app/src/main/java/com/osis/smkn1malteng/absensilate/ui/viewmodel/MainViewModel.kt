package com.osis.smkn1malteng.absensilate.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.osis.smkn1malteng.absensilate.data.local.AppConfigEntity
import com.osis.smkn1malteng.absensilate.data.local.AppDatabase
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import com.osis.smkn1malteng.absensilate.data.model.SmpClass
import com.osis.smkn1malteng.absensilate.data.model.SmpSubClass
import com.osis.smkn1malteng.absensilate.network.SyncClient
import com.osis.smkn1malteng.absensilate.network.SyncServerManager
import com.osis.smkn1malteng.absensilate.network.TelegramSender
import com.osis.smkn1malteng.absensilate.network.SendResult
import com.osis.smkn1malteng.absensilate.sync.CompactSerializer
import com.osis.smkn1malteng.absensilate.sync.CsvEngine
import com.osis.smkn1malteng.absensilate.sync.QrSyncEngine
import com.osis.smkn1malteng.absensilate.sync.RosterBackup
import com.osis.smkn1malteng.absensilate.util.PhoneNumberUtil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream

data class UiState(
    val students: List<StudentEntity> = emptyList(),
    val pendingViolations: List<StudentEntity> = emptyList(),
    val showViolationDialog: Boolean = false,
    val showWaliKelasPhoneBottomSheet: Boolean = false,
    val showQrExportDialog: Boolean = false,
    val selectedStudent: StudentEntity? = null,
    val currentWeek: Int = 0,
    val currentYear: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val qrEncodedData: String = "",
    val qrStudentCount: Int = 0,
    val syncServerState: SyncServerManager.ServerState = SyncServerManager.ServerState.Idle,
    val syncStatus: String? = null,
    val syncProgress: Float = 0f,
    val selectedStudentIds: Set<Long> = emptySet(),
    val showRecapScreen: Boolean = false,
    // 🔥 Max pelanggaran sebelum panggilan orang tua (single source of truth, default 3).
    val maxViolation: Int = 3
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val db = AppDatabase.getInstance(application)
    private val studentDao = db.studentDao()
    private val configDao = db.configDao()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())
    val students: StateFlow<List<StudentEntity>> = _students

    private var syncServerManager: SyncServerManager? = null
    private var syncJob: Job? = null

    private val gson = Gson()

    init {
        Log.d(TAG, "ViewModel init started")
        try {
            initConfig()
            seedStudentsIfNeeded()
            loadStudents()
            loadPendingViolations()
            Log.d(TAG, "ViewModel init completed — DB access delegated to coroutines")
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: ViewModel init failed", e)
            _uiState.value = _uiState.value.copy(
                errorMessage = "Gagal inisialisasi: ${e.message}"
            )
        }
    }

    // ============================================
    // 🔥 SEED DATA SISWA (pertama kali instal)
    // ============================================

    // Saat pertama kali aplikasi dibuka, DB masih kosong → isi otomatis dari asset
    // seed_students.csv (roster 713 siswa SMPN 38). Hanya berjalan SEKALI;
    // kalau DB sudah berisi data (mis. update app), dilewati tanpa mengubah apapun.
    private fun seedStudentsIfNeeded() {
        val prefs = getApplication<Application>().getSharedPreferences(
            "absensi_prefs", Context.MODE_PRIVATE
        )
        if (prefs.getBoolean("seed_students_done", false)) {
            Log.d(TAG, "Seed: sudah pernah dijalankan — lewati")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = studentDao.getCount()
                if (count > 0) {
                    Log.d(TAG, "Seed: DB sudah berisi $count siswa — lewati")
                } else {
                    val seed = loadSeedFromAssets()
                    if (seed.isNotEmpty()) {
                        db.withTransaction { studentDao.insertAll(seed) }
                        Log.d(TAG, "Seed: ${seed.size} siswa dimasukkan ke DB")
                    } else {
                        Log.w(TAG, "Seed: file seed kosong / tidak terbaca")
                    }
                }
                withContext(Dispatchers.Main) {
                    prefs.edit().putBoolean("seed_students_done", true).apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Seed FAILED", e)
            }
        }
    }

    // Baca & parse file roster seed dari assets → daftar StudentEntity (tanpa pelanggaran).
    private fun loadSeedFromAssets(): List<StudentEntity> {
        return try {
            val lines = getApplication<Application>().assets.open("seed_students.csv")
                .bufferedReader().use { it.readLines() }
            Log.d(TAG, "Seed: asset seed_students.csv dibaca (${lines.size} baris)")
            RosterBackup.parseRoster(lines)
        } catch (e: Exception) {
            Log.e(TAG, "loadSeedFromAssets FAILED", e)
            emptyList()
        }
    }

    // ============================================
    // 🔥 SYNC SERVER MANAGEMENT
    // ============================================

    fun startSyncServer() {
        if (syncServerManager?.isActive() == true) {
            updateSyncStatus("Server already running", 1.0f)
            return
        }

        syncServerManager = SyncServerManager(
            context = getApplication(),
            scope = viewModelScope
        )

        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            syncServerManager?.state?.collect { state ->
                _uiState.value = _uiState.value.copy(
                    syncServerState = state,
                    syncStatus = when (state) {
                        is SyncServerManager.ServerState.Idle -> "Server idle"
                        is SyncServerManager.ServerState.Starting -> "Starting server..."
                        is SyncServerManager.ServerState.Active -> {
                            "Server active: ${state.ipAddress}:${state.port}"
                        }
                        is SyncServerManager.ServerState.Stopping -> "Stopping server..."
                        is SyncServerManager.ServerState.Error -> "Error: ${state.message}"
                    }
                )
                when (state) {
                    is SyncServerManager.ServerState.Starting -> updateSyncProgress(0.3f)
                    is SyncServerManager.ServerState.Active -> updateSyncProgress(1.0f)
                    is SyncServerManager.ServerState.Error -> updateSyncProgress(0f)
                    else -> {}
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            updateSyncStatus("Starting hotspot...", 0.1f)
            val result = syncServerManager?.start {
                _students.value
            }

            result?.fold(
                onSuccess = { activeState ->
                    Log.d("MainViewModel", "Server started: ${activeState.ipAddress}")
                    updateSyncStatus("Server ready — scan QR to sync", 1.0f)
                    generateHandshakeQr()
                },
                onFailure = { error ->
                    Log.e("MainViewModel", "Server start failed: ${error.message}", error)
                    updateSyncStatus("Failed to start: ${error.message}", 0f)
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Sync server error: ${error.message}"
                    )
                }
            )
        }
    }

    fun stopSyncServer() {
        syncServerManager?.stop()
        syncServerManager = null
        syncJob?.cancel()
        syncJob = null
        updateSyncStatus("Server stopped", 0f)
        _uiState.value = _uiState.value.copy(
            qrEncodedData = "",
            qrStudentCount = 0,
            showQrExportDialog = false
        )
    }

    private fun generateHandshakeQr() {
        val qrContent = syncServerManager?.getQrContent()
        val credentials = syncServerManager?.getCredentials()

        if (qrContent != null && credentials != null) {
            val (ssid, password) = credentials
            _uiState.value = _uiState.value.copy(
                qrEncodedData = qrContent,
                qrStudentCount = _students.value.size,
                showQrExportDialog = true
            )
            updateSyncStatus("QR generated — scan with another device", 1.0f)
        } else {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Failed to generate QR — server not ready"
            )
        }
    }

    fun getHandshakeCredentials(): Pair<String, String>? {
        return syncServerManager?.getCredentials()
    }

    fun isSyncServerActive(): Boolean {
        return syncServerManager?.isActive() == true
    }

    // ============================================
    // 🔥 IMPORT VIA HANDSHAKE OTOMATIS
    // ============================================

    fun importViaHandshakeAuto(qrContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateSyncStatus("Connecting to hotspot...", 0.2f)
                _uiState.value = _uiState.value.copy(isImporting = true)

                val client = SyncClient(getApplication())
                val result = client.sync(qrContent)

                withContext(Dispatchers.Main) {
                    when (result) {
                        is SyncClient.SyncResult.Success -> {
                            val localStudents = _students.value
                            val merged = QrSyncEngine.mergeStudents(result.students, localStudents)

                            viewModelScope.launch(Dispatchers.IO) {
                                merged.forEach { student ->
                                    val existing = studentDao.getStudentByName(student.name)
                                    if (existing != null) {
                                        studentDao.update(student)
                                    } else {
                                        studentDao.insert(student)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    _uiState.value = _uiState.value.copy(
                                        isImporting = false,
                                        isLoading = false
                                    )
                                    loadStudents()
                                    loadPendingViolations()
                                    updateSyncStatus(
                                        "✅ Synced ${result.students.size} students (${result.source})",
                                        1.0f
                                    )
                                    Toast.makeText(
                                        getApplication(),
                                        "✅ Berhasil sinkron ${result.students.size} siswa",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                        is SyncClient.SyncResult.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isImporting = false,
                                isLoading = false,
                                errorMessage = "Sync failed: ${result.message}"
                            )
                            updateSyncStatus("❌ ${result.message}", 0f)
                            Toast.makeText(
                                getApplication(),
                                "❌ Gagal sinkron: ${result.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is SyncClient.SyncResult.Cancelled -> {
                            _uiState.value = _uiState.value.copy(
                                isImporting = false,
                                isLoading = false
                            )
                            updateSyncStatus("Sync cancelled", 0f)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        isLoading = false,
                        errorMessage = "Sync error: ${e.message}"
                    )
                    updateSyncStatus("❌ ${e.message}", 0f)
                }
            }
        }
    }

    fun importViaHandshake(qrContent: String, ssid: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateSyncStatus("Connecting to hotspot...", 0.2f)
                _uiState.value = _uiState.value.copy(isImporting = true)

                val client = SyncClient(getApplication())
                val result = client.sync(qrContent, ssid, password)

                withContext(Dispatchers.Main) {
                    when (result) {
                        is SyncClient.SyncResult.Success -> {
                            val localStudents = _students.value
                            val merged = QrSyncEngine.mergeStudents(result.students, localStudents)

                            viewModelScope.launch(Dispatchers.IO) {
                                merged.forEach { student ->
                                    val existing = studentDao.getStudentByName(student.name)
                                    if (existing != null) {
                                        studentDao.update(student)
                                    } else {
                                        studentDao.insert(student)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    _uiState.value = _uiState.value.copy(
                                        isImporting = false,
                                        isLoading = false
                                    )
                                    loadStudents()
                                    loadPendingViolations()
                                    updateSyncStatus(
                                        "✅ Synced ${result.students.size} students (${result.source})",
                                        1.0f
                                    )
                                    Toast.makeText(
                                        getApplication(),
                                        "✅ Berhasil sinkron ${result.students.size} siswa",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                        is SyncClient.SyncResult.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isImporting = false,
                                isLoading = false,
                                errorMessage = "Sync failed: ${result.message}"
                            )
                            updateSyncStatus("❌ ${result.message}", 0f)
                            Toast.makeText(
                                getApplication(),
                                "❌ Gagal sinkron: ${result.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is SyncClient.SyncResult.Cancelled -> {
                            _uiState.value = _uiState.value.copy(
                                isImporting = false,
                                isLoading = false
                            )
                            updateSyncStatus("Sync cancelled", 0f)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        isLoading = false,
                        errorMessage = "Sync error: ${e.message}"
                    )
                    updateSyncStatus("❌ ${e.message}", 0f)
                }
            }
        }
    }

    private fun updateSyncStatus(message: String, progress: Float) {
        _uiState.value = _uiState.value.copy(
            syncStatus = message,
            syncProgress = progress
        )
    }

    private fun updateSyncProgress(progress: Float) {
        _uiState.value = _uiState.value.copy(syncProgress = progress)
    }

    // ============================================
    // LOAD DATA
    // ============================================

    private fun loadStudents() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "loadStudents: starting Flow collect")
                studentDao.getAll().collect { students ->
                    Log.d(TAG, "loadStudents: received ${students.size} students")
                    withContext(Dispatchers.Main) {
                        _students.value = students
                        _uiState.value = _uiState.value.copy(students = students)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadStudents FAILED", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Gagal load siswa: ${e.message}"
                    )
                }
            }
        }
    }

    private fun loadPendingViolations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "loadPendingViolations: starting Flow collect")
                val threshold = _uiState.value.maxViolation
                studentDao.getStudentsWithViolations(threshold).collect { students ->
                    Log.d(TAG, "loadPendingViolations: received ${students.size} pending")
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            pendingViolations = students
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadPendingViolations FAILED", e)
            }
        }
    }

    // 🔥 Set max pelanggaran (panggilan orang tua). Single source of truth: simpan di DB + update state,
    //    semua bagian lain (dialog, warna kartu, daftar pending, teks WA) otomatis ikut.
    fun updateMaxViolation(newValue: Int) {
        if (newValue < 1) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configDao.getConfig()
                val base = current ?: AppConfigEntity(
                    lastRecordedWeek = _uiState.value.currentWeek,
                    lastRecordedYear = _uiState.value.currentYear
                )
                val updated = base.copy(maxViolation = newValue)
                configDao.upsertConfig(updated)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(maxViolation = newValue)
                    loadPendingViolations()
                }
            } catch (e: Exception) {
                Log.e(TAG, "updateMaxViolation FAILED: ${e.message}", e)
            }
        }
    }

    // ============================================
    // INISIALISASI CONFIG
    // ============================================

    // Membaca config saat app start: set week/year & maxViolation.
    // Jika config belum ada (first run), dibuat dulu dengan default maxViolation = 3.
    // CATATAN: fitur "reset pelanggaran otomatis tiap minggu" tidak dipakai di app SMP;
    //          minggu baru TIDAK memunculkan modal reset otomatis.
    fun initConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val calendar = Calendar.getInstance()
                val currentWeek = calendar.get(Calendar.WEEK_OF_YEAR)
                val currentYear = calendar.get(Calendar.YEAR)

                val config = configDao.getConfig()

                withContext(Dispatchers.Main) {
                    if (config == null) {
                        viewModelScope.launch(Dispatchers.IO) {
                            val newConfig = AppConfigEntity(
                                lastRecordedWeek = currentWeek,
                                lastRecordedYear = currentYear,
                                maxViolation = 3
                            )
                            configDao.upsertConfig(newConfig)
                        }
                        _uiState.value = _uiState.value.copy(
                            currentWeek = currentWeek,
                            currentYear = currentYear,
                            maxViolation = 3
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            currentWeek = currentWeek,
                            currentYear = currentYear,
                            maxViolation = config.maxViolation
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "initConfig FAILED", e)
            }
        }
    }

    // ============================================
    // QR EXPORT / IMPORT
    // ============================================

    fun generateQrExport() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }
                val students = _students.value
                if (syncServerManager?.isActive() == true) {
                    generateHandshakeQr()
                } else {
                    val encoded = QrSyncEngine.encodeStudents(students)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            qrEncodedData = encoded,
                            qrStudentCount = students.size,
                            showQrExportDialog = true
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Gagal generate QR: ${e.message}"
                    )
                }
            }
        }
    }

    fun importQrData(encodedData: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isImporting = true)
                }

                if (CompactSerializer.isLegacyFormat(encodedData)) {
                    val incomingStudents = QrSyncEngine.decodeStudentsLegacy(encodedData)
                    if (incomingStudents.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                isImporting = false,
                                errorMessage = "Data QR tidak valid atau kosong"
                            )
                        }
                        return@launch
                    }

                    val localStudents = _students.value
                    val mergedStudents = QrSyncEngine.mergeStudents(incomingStudents, localStudents)

                    mergedStudents.forEach { student ->
                        val existing = studentDao.getStudentByName(student.name)
                        if (existing != null) {
                            studentDao.update(student)
                        } else {
                            studentDao.insert(student)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isImporting = false,
                            errorMessage = null
                        )
                        loadStudents()
                        loadPendingViolations()
                        Toast.makeText(
                            getApplication(),
                            "✅ Berhasil import ${mergedStudents.size} siswa",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val students = CompactSerializer.deserialize(encodedData)
                if (students.isNotEmpty()) {
                    val localStudents = _students.value
                    val mergedStudents = QrSyncEngine.mergeStudents(students, localStudents)

                    mergedStudents.forEach { student ->
                        val existing = studentDao.getStudentByName(student.name)
                        if (existing != null) {
                            studentDao.update(student)
                        } else {
                            studentDao.insert(student)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isImporting = false,
                            errorMessage = null
                        )
                        loadStudents()
                        loadPendingViolations()
                        Toast.makeText(
                            getApplication(),
                            "✅ Berhasil import ${mergedStudents.size} siswa (compact)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        errorMessage = "Format QR tidak dikenali"
                    )
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        errorMessage = "Gagal import QR: ${e.message}"
                    )
                }
            }
        }
    }

    fun closeQrExportDialog() {
        _uiState.value = _uiState.value.copy(
            showQrExportDialog = false,
            qrEncodedData = "",
            qrStudentCount = 0
        )
        if (syncServerManager?.isActive() == true) {
            stopSyncServer()
        }
    }

    // ============================================
    // CSV EXPORT / IMPORT
    // ============================================

    fun exportCsv(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isExporting = true)
                }
                val students = _students.value
                val weekNumber = _uiState.value.currentWeek

                CsvEngine.writeCsvToUri(
                    context.contentResolver,
                    uri,
                    students,
                    weekNumber
                )

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = "Gagal export CSV: ${e.message}"
                    )
                }
            }
        }
    }

    fun importCsv(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isImporting = true)
                }

                val importedStudents = CsvEngine.parseCsvFromUri(
                    context.contentResolver,
                    uri
                )

                if (importedStudents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "⚠️ File CSV kosong atau format tidak sesuai", Toast.LENGTH_LONG).show()
                        _uiState.value = _uiState.value.copy(isImporting = false)
                    }
                    return@launch
                }

                var insertedCount = 0

                db.withTransaction {
                    importedStudents.forEach { student ->
                        // 🔥 MODE TAMBAH (bukan merge): setiap baris CSV menjadi siswa BARU.
                        //    id dari CSV diabaikan (Room auto-generate) dan uuid dibuat baru
                        //    supaya tidak bentrok dengan data lokal.
                        val newStudent = student.copy(
                            id = 0,
                            uuid = if (student.uuid.isEmpty()) {
                                UUID.randomUUID().toString()
                            } else {
                                student.uuid
                            }
                        )
                        studentDao.insert(newStudent)
                        insertedCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isImporting = false)
                    loadStudents()
                    loadPendingViolations()

                    Toast.makeText(
                        context,
                        "✅ Berhasil import: $insertedCount siswa ditambahkan (data lama tidak diubah)",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isImporting = false)
                    Toast.makeText(context, "❌ Gagal import CSV: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ============================================
    // 🔥 BACKUP / RESTORE / HAPUS SEMUA SISWA
    // ============================================

    // Backup roster (nama + kelas + HP, TANPA pelanggaran) → file CSV.
    fun exportRoster(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isExporting = true)
                }
                val students = _students.value

                RosterBackup.writeRosterToUri(
                    context.contentResolver,
                    uri,
                    students
                )

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = null
                    )
                    Toast.makeText(
                        context,
                        "✅ Backup ${students.size} siswa disimpan (tanpa pelanggaran)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = "Gagal backup roster: ${e.message}"
                    )
                }
            }
        }
    }

    // Baca file backup roster → daftar siswa (dipakai untuk dialog pra-konfirmasi restore).
    // Jalan di IO; jika format tidak dikenal, return list kosong.
    suspend fun readRosterForRestore(uri: Uri, context: Context): List<StudentEntity> {
        return withContext(Dispatchers.IO) {
            try {
                RosterBackup.parseRosterFromUri(context.contentResolver, uri)
            } catch (e: Exception) {
                Log.e(TAG, "readRosterForRestore FAILED", e)
                emptyList()
            }
        }
    }

    // Restore nama siswa = GANTI TOTAL: hapus semua siswa lama dulu, lalu isi dari roster file.
    fun replaceAllStudents(roster: List<StudentEntity>, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.withTransaction {
                    studentDao.deleteAll()
                    if (roster.isNotEmpty()) {
                        studentDao.insertAll(roster)
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        selectedStudentIds = emptySet(),
                        showRecapScreen = false
                    )
                    loadStudents()
                    loadPendingViolations()
                    Toast.makeText(
                        context,
                        "✅ Restore selesai: ${roster.size} siswa dimasukkan (semua siswa lama diganti)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Gagal restore: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Hapus SEMUA siswa + reset config (ke default: maxViolation = 3, week/year = sekarang).
    // Dipicu hanya setelah pengguna mengetik "HAPUS" (diverifikasi di MainActivity).
    fun deleteAllStudentsAndResetConfig(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val calendar = Calendar.getInstance()
                val currentWeek = calendar.get(Calendar.WEEK_OF_YEAR)
                val currentYear = calendar.get(Calendar.YEAR)

                db.withTransaction {
                    studentDao.deleteAll()
                    configDao.upsertConfig(
                        AppConfigEntity(
                            lastRecordedWeek = currentWeek,
                            lastRecordedYear = currentYear,
                            maxViolation = 3
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        currentWeek = currentWeek,
                        currentYear = currentYear,
                        maxViolation = 3,
                        selectedStudentIds = emptySet(),
                        showRecapScreen = false,
                        pendingViolations = emptyList()
                    )
                    loadStudents()
                    loadPendingViolations()
                    Toast.makeText(
                        context,
                        "✅ Semua siswa dihapus dan pengaturan dikembalikan ke default",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Gagal menghapus siswa: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ============================================
    // CRUD SISWA
    // ============================================

    fun addStudent(
        name: String,
        phone: String,
        waliKelasPhone: String,
        kelas: SmpClass,
        subKelas: SmpSubClass,
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = true)
                }

                val existing = studentDao.getStudentByName(name)
                val newViolationCount = if (existing != null) {
                    existing.violationCount + 1
                } else {
                    1
                }

                val student = if (existing != null) {
                    val updated = existing.copy(
                        phone = phone,
                        waliKelasPhone = waliKelasPhone.takeIf { it.isNotBlank() } ?: existing.waliKelasPhone,
                        kelas = kelas,
                        subKelas = subKelas,
                        violationCount = newViolationCount,
                        timestamps = existing.timestamps + timestamp
                    )
                    studentDao.update(updated)
                    updated
                } else {
                    val newStudent = StudentEntity(
                        uuid = UUID.randomUUID().toString(),
                        name = name,
                        phone = phone,
                        waliKelasPhone = waliKelasPhone.takeIf { it.isNotBlank() },
                        kelas = kelas,
                        subKelas = subKelas,
                        violationCount = 1,
                        timestamps = listOf(timestamp)
                    )
                    val id = studentDao.insert(newStudent)
                    newStudent.copy(id = id)
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        selectedStudent = student
                    )

                    if (newViolationCount >= _uiState.value.maxViolation) {
                        _uiState.value = _uiState.value.copy(
                            showViolationDialog = true
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            selectedStudent = null
                        )
                    }
                    loadPendingViolations()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Gagal menyimpan: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun editStudent(
        id: Long,
        name: String,
        phone: String,
        waliKelasPhone: String,
        kelas: SmpClass,
        subKelas: SmpSubClass
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = studentDao.getStudentById(id)
            existing?.let {
                val updatedStudent = it.copy(
                    name = name,
                    phone = phone,
                    waliKelasPhone = waliKelasPhone.takeIf { it.isNotBlank() },
                    kelas = kelas,
                    subKelas = subKelas
                )
                studentDao.update(updatedStudent)
            }
        }
    }

    fun deleteStudent(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            studentDao.deleteById(id)
            withContext(Dispatchers.Main) {
                loadPendingViolations()
            }
        }
    }

    fun resetAllCounters() {
        viewModelScope.launch(Dispatchers.IO) {
            studentDao.resetAllCounters()
            withContext(Dispatchers.Main) {
                loadPendingViolations()
            }
        }
    }

    // ============================================
    // CRUD PELANGGARAN PER SISWA
    // ============================================

    fun addViolationDirectly(studentId: Long, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val student = studentDao.getStudentById(studentId)
                student?.let {
                    val updated = it.copy(
                        violationCount = it.violationCount + 1,
                        timestamps = it.timestamps + System.currentTimeMillis()
                    )
                    studentDao.update(updated)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "✅ Pelanggaran ditambahkan untuk ${student.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadPendingViolations()
                        loadStudents()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "❌ Gagal menambah pelanggaran: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun decrementViolation(studentId: Long, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val student = studentDao.getStudentById(studentId)
                if (student == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ Siswa tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (student.violationCount <= 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "⚠️ Tidak ada pelanggaran untuk dikurangi", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val updatedTimestamps = student.timestamps.dropLast(1)
                val updatedStudent = student.copy(
                    violationCount = student.violationCount - 1,
                    timestamps = updatedTimestamps
                )
                studentDao.update(updatedStudent)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "✅ Pelanggaran dikurangi untuk ${student.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadStudents()
                    loadPendingViolations()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "❌ Gagal mengurangi pelanggaran: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun deleteViolationTimestamp(studentId: Long, timestampToRemove: Long, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val student = studentDao.getStudentById(studentId)
                if (student == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ Siswa tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val updatedTimestamps = student.timestamps.filter { it != timestampToRemove }
                val updatedStudent = student.copy(
                    violationCount = updatedTimestamps.size,
                    timestamps = updatedTimestamps
                )
                studentDao.update(updatedStudent)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "✅ Satu catatan pelanggaran dihapus untuk ${student.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadStudents()
                    loadPendingViolations()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "❌ Gagal menghapus catatan: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun resetSingleStudent(studentId: Long, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val student = studentDao.getStudentById(studentId)
                if (student == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "❌ Siswa tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                studentDao.resetStudentViolations(studentId)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "✅ Pelanggaran ${student.name} direset",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadStudents()
                    loadPendingViolations()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "❌ Gagal reset pelanggaran: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ============================================
    // VIOLATION DIALOG
    // ============================================

    fun handleViolationAction(action: String, context: Context) {
        val student = _uiState.value.selectedStudent ?: return

        when (action) {
            "Tolak" -> {
                _uiState.value = _uiState.value.copy(
                    showViolationDialog = false,
                    selectedStudent = null
                )
                loadPendingViolations()
            }
            "Terima" -> {
                if (student.waliKelasPhone.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        showWaliKelasPhoneBottomSheet = true,
                        showViolationDialog = false
                    )
                } else {
                    sendWhatsAppMessage(student, context)
                    _uiState.value = _uiState.value.copy(
                        showViolationDialog = false,
                        selectedStudent = null
                    )
                }
            }
        }
    }

    fun saveWaliKelasPhoneAndSendWa(waliKelasPhone: String, context: Context) {
        val student = _uiState.value.selectedStudent ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val updatedStudent = student.copy(waliKelasPhone = waliKelasPhone)
                studentDao.update(updatedStudent)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        showWaliKelasPhoneBottomSheet = false,
                        selectedStudent = updatedStudent
                    )
                    sendWhatsAppMessage(updatedStudent, context)
                    _uiState.value = _uiState.value.copy(
                        selectedStudent = null
                    )
                    loadPendingViolations()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Gagal menyimpan nomor: ${e.message}"
                    )
                }
            }
        }
    }

    private fun sendWhatsAppMessage(student: StudentEntity, context: Context) {
        try {
            val rawNumber = student.waliKelasPhone ?: ""
            val waNumber = PhoneNumberUtil.getWhatsAppNumber(rawNumber)

            if (waNumber == null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Nomor wali kelas tidak valid: $rawNumber"
                )
                return
            }

            val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            val lastTimestamp = student.timestamps.lastOrNull() ?: System.currentTimeMillis()
            val date = dateFormat.format(Date(lastTimestamp))
            val time = timeFormat.format(Date(lastTimestamp))

            val maxCount = _uiState.value.maxViolation
            val maxWords = numberToWords(maxCount)

            val message = """
                [PANGGILAN ORANG TUA - SMP NEGERI 38 MALUKU TENGAH]

                Yth. Bapak/Ibu Orang Tua/Wali dari:
                Nama Siswa: ${student.name}
                Kelas: ${student.kelas.display} ${student.subKelas.display}

                Dengan hormat, dengan ini kami sampaikan bahwa putra/putri Bapak/Ibu tercatat terlambat hadir ke sekolah sebanyak $maxCount ($maxWords) kali pada minggu ini (terakhir: $date, pukul $time).

                Sehubungan dengan hal tersebut, kami mengharapkan kesediaan Bapak/Ibu untuk berkenan hadir ke sekolah guna membahas lebih lanjut terkait kedisiplinan putra/putri Bapak/Ibu.

                Kami mohon maaf atas ketidaknyamanannya dan mengucapkan terima kasih atas perhatian serta kerja sama Bapak/Ibu.
            """.trimIndent()

            val encodedMessage = Uri.encode(message)
            val waUrl = "https://wa.me/$waNumber?text=$encodedMessage"

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Gagal membuka WhatsApp: ${e.message}"
            )
        }
    }

    fun retryPendingViolation(student: StudentEntity) {
        _uiState.value = _uiState.value.copy(
            selectedStudent = student,
            showViolationDialog = true
        )
    }

    // ============================================
    // IMPORT JSON.GZ
    // ============================================

    suspend fun importFromJsonGz(uri: Uri, context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val compressed = context.contentResolver.openInputStream(uri)?.readBytes()
                    ?: return@withContext false

                val json = decompressGzip(compressed)

                // 🔥 Safe-parse manual — TIDAK langsung fromJson ke StudentEntity.
                //    Backup lama (schema SMK: jurusan/parentPhone) dan backup baru
                //    (schema SMP: subKelas/waliKelasPhone) dipetakan toleran;
                //    entry yang tidak valid di-skip, bukan bikin crash.
                val jsonArray = gson.fromJson(json, JsonArray::class.java)
                    ?: return@withContext false

                val importedStudents = jsonArray.mapNotNull { element ->
                    try {
                        parseStudentJson(element.asJsonObject)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (importedStudents.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "⚠️ Data kosong atau format tidak sesuai (kemungkinan backup versi lama)", Toast.LENGTH_LONG).show()
                    }
                    return@withContext false
                }

                val localStudents = _students.value
                val mergedStudents = QrSyncEngine.mergeStudents(importedStudents, localStudents)

                mergedStudents.forEach { student ->
                    val existing = studentDao.getStudentByName(student.name)
                    if (existing != null) {
                        studentDao.update(student)
                    } else {
                        studentDao.insert(student)
                    }
                }

                withContext(Dispatchers.Main) {
                    loadStudents()
                    loadPendingViolations()
                    Toast.makeText(
                        context,
                        "✅ Berhasil import ${mergedStudents.size} siswa dari file .json.gz",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@withContext true

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Gagal import: ${e.message}", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }
        }
    }

    private fun decompressGzip(compressed: ByteArray): String {
        return ByteArrayInputStream(compressed).use { bais ->
            GZIPInputStream(bais).use { gzip ->
                gzip.readBytes().toString(Charsets.UTF_8)
            }
        }
    }

    /**
     * 🔥 Parse satu JsonObject menjadi StudentEntity secara toleran.
     * - Menerima enum berdasarkan NAME ("VII", "SUB_1") maupun DISPLAY ("7", "Sains")
     * - Fallback key lama: jurusan→subKelas, parentPhone→waliKelasPhone
     * - id diabaikan (auto-generate) agar tidak bentrok dengan data lokal
     * - Return null jika entry tidak valid / kelas tidak dikenali
     */
    private fun parseStudentJson(o: JsonObject): StudentEntity? {
        val name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        if (name.isEmpty()) return null

        fun stringField(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key ->
                o.get(key)?.takeIf { it.isJsonPrimitive && it.asString.isNotBlank() }?.asString
            }

        val kelasRaw = stringField("kelas")
        val subKelasRaw = stringField("subKelas", "jurusan")
        val waliPhone = stringField("waliKelasPhone", "parentPhone")

        val kelas = SmpClass.values().firstOrNull { it.name == kelasRaw || it.display == kelasRaw }
            ?: return null
        val subKelas = SmpSubClass.values().firstOrNull { it.name == subKelasRaw || it.display == subKelasRaw }
            ?: return null

        val violationCount = o.get("violationCount")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        val timestamps = o.getAsJsonArray("timestamps")
            ?.mapNotNull { element -> element.takeIf { it.isJsonPrimitive }?.asLong }
            ?: emptyList()

        return StudentEntity(
            uuid = stringField("uuid") ?: UUID.randomUUID().toString(),
            name = name,
            phone = stringField("phone") ?: "",
            waliKelasPhone = waliPhone,
            kelas = kelas,
            subKelas = subKelas,
            violationCount = violationCount,
            timestamps = timestamps
        )
    }

    // ============================================
    // KIRIM KE SERVER (TELEGRAM)
    // ============================================

    suspend fun sendAllStudentsToServer(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val students = _students.value
                if (students.isEmpty()) {
                    return@withContext false
                }
                val result = TelegramSender.sendAllStudents(context, students)
                return@withContext (result is SendResult.Success)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Gagal kirim data: ${e.message}", e)
                return@withContext false
            }
        }
    }

    // ============================================
    // LAINNYA
    // ============================================

    suspend fun getStudent(id: Long): StudentEntity? {
        return studentDao.getStudentById(id)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ============================================
    // 🔥 REKAP SELEKTIF MULTI-SISWA
    // ============================================

    // Toggle seleksi siswa untuk rekap
    fun toggleStudentSelection(studentId: Long) {
        val current = _uiState.value.selectedStudentIds
        val updated = if (current.contains(studentId)) {
            current - studentId
        } else {
            current + studentId
        }
        _uiState.value = _uiState.value.copy(selectedStudentIds = updated)
    }

    // Ganti seluruh pilihan (digunakan "Pilih Semua" pada list ter-filter)
    fun selectAllStudents(ids: List<Long>) {
        _uiState.value = _uiState.value.copy(selectedStudentIds = ids.toSet())
    }

    // Kosongkan pilihan (digunakan tombol X keluar mode select / "Batal Semua")
    fun clearStudentSelection() {
        _uiState.value = _uiState.value.copy(selectedStudentIds = emptySet())
    }

    // Tambah +1 pelanggaran sekaligus ke banyak siswa (batch select mode)
    fun addViolationsToStudents(ids: List<Long>, context: Context) {
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                studentDao.addViolationsBatch(ids, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "✅ +1 pelanggaran untuk ${ids.size} siswa",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadPendingViolations()
                    loadStudents()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal tambah pelanggaran massal: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "❌ Gagal menambah pelanggaran: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Masuk ke layar rekap
    fun openRecapScreen() {
        if (_uiState.value.selectedStudentIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(showRecapScreen = true)
    }

    // Keluar dari layar rekap
    fun closeRecapScreen() {
        _uiState.value = _uiState.value.copy(
            showRecapScreen = false,
            selectedStudentIds = emptySet()
        )
    }

    // Dapatkan siswa terpilih untuk rekap
    fun getSelectedStudentsForRecap(): List<StudentEntity> {
        val ids = _uiState.value.selectedStudentIds
        return _students.value.filter { it.id in ids }
    }

    fun clearViolationState() {
        _uiState.value = _uiState.value.copy(
            showViolationDialog = false,
            showWaliKelasPhoneBottomSheet = false,
            selectedStudent = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        stopSyncServer()
    }

    // 🔥 Ubah angka ke kata (terbilang) untuk teks WA. Cover 1-20; di atas itu pakai angka biasa.
    private fun numberToWords(value: Int): String {
        val basic = arrayOf(
            "satu", "dua", "tiga", "empat", "lima",
            "enam", "tujuh", "delapan", "sembilan", "sepuluh",
            "sebelas", "dua belas", "tiga belas", "empat belas", "lima belas",
            "enam belas", "tujuh belas", "delapan belas", "sembilan belas", "dua puluh"
        )
        return if (value in 1..20) basic[value - 1] else value.toString()
    }
}