# Aturan Kerja — AbsensiLate (WAJIB diikuti agent)

> **Dokumen ini adalah kontrak kerja.** Setiap pelanggaran harus diperbaiki sebelum melanjutkan.
> Pelajaran dari: insiden overwrite Dropdowns.kt, enum SMK→SMP crash, BuildConfig package mismatch,
> Room identity hash, splash screen theme, hard-coded secrets,
> **manifest component `ClassNotFoundException` (namespace ≠ source package sejak Fase 1).**

---

## 0. PRINSIP UTAMA

1. **Jangan pernah menebak.** Selalu baca file dulu, cek path, verifikasi isi sebelum edit.
2. **Jangan pernah skip build.** Setelah perubahan apapun, jalankan `./gradlew clean assembleDebug --no-daemon --offline` DAN pastikan `BUILD SUCCESSFUL` sebelum bilang "selesai".
3. **Jangan pernah commit.** Metode backup = **folder**, bukan git commit (git boros storage). Lihat Section 1.4.
4. **Kalau ragu, tanya.** Jangan asumsi library tersedia, path benar, atau kode lama masih valid.

---

## 1. KESELAMATAN FILE (CRITICAL)

### 1.1 Verifikasi Path
- **SEBELUM write/rm** pada file existing: baca file dulu, pastikan path + isi cocok.
- **SEBELUM buat file baru**: pastikan path-nya benar, package statement sesuai, parent directory ada.
- Jangan pernah menulis ke file yang belum dibaca (kecuali file baru yang jelas-jelas baru).

### 1.2 Jangan Overwrite Tanpa Sengaja
- Kalau ragu apakah file sudah ada atau belum, pakai `glob` atau `ls` dulu.
- Kalau file sudah ada dan ingin replace isi, **baca dulu** lalu **edit bagian yang diubah**, jangan overwrite seluruh isi kecuali memang dibutuhkan.
- **TIDAK BOLEH** mengambil content dari file lain (sumber kedua/tess.txt/AA.txt) lalu menulis ke file proyek tanpa verifikasi bahwa isinya benar.

### 1.3 Backup Mental
- Sebelum perubahan besar, lakukan backup folder dulu (Section 1.4).
- Kalau perubahan gagal, rollback = salin lagi file yang diubah dari folder backup terbaru.

### 1.4 Backup Folder (METODE BAKU — ganti git commit)
- Proyek TANPA git (semua commit sudah dihapus). **JANGAN PERNAH `git init` / commit lagi.**
- Backup = copy folder penuh proyek ke `~/nihongo/WORKSPACE/ANDROID/BACKUP/`.
- Simpan dengan nama folder ber-tanggal: `AbsensiLateApp backup YYYY-MM-DD`.
- Exclude (biar kecil & cepat): `build/ .gradle/ .idea/ captures/ .kotlin/ .git/`.
- Command:
  ```bash
  rsync -a --exclude='build/' --exclude='.gradle/' --exclude='.idea/' \
    --exclude='captures/' --exclude='.kotlin/' --exclude='.git/' \
    "/home/nihongo/nihongo/WORKSPACE/ANDROID/AbsensiLateApp_copy1_SMPN38/" \
    "/home/nihongo/nihongo/WORKSPACE/ANDROID/BACKUP/AbsensiLateApp backup YYYY-MM-DD/"
  ```
- WAJIB sertakan `local.properties` (berisi secrets) — jangan di-exclude.
- Setelah rsync: verifikasi (du -sh, spot-check file penting) sebelum bilang "selesai".

---

## 2. ATURAN BUILD & KOMPILASI

### 2.1 Build Command
```bash
./gradlew clean assembleDebug --no-daemon --offline
```
- **User menjalankan build sendiri.** JANGAN PERNAH jalankan gradle (./gradlew clean assembleDebug --no-daemon --offline) kecuali user minta.
- Setelah perubahan file, INGATKAN user untuk build.
- **BUILD SUCCESSFUL = 0 errors.** Warnings boleh ada (pre-existing), tapi TIDAK BOLEH ada error (tapi jangan sampai memicu Halting Problem / Hindari Halting Problem).
- **Debug vs Release build (lag startup):** build `assembleDebug` di-install akan JIT-compile saat jalan → di HP low-end panic lag rasa lambat pada menit-menit awal (punggung input dsj) lalu lancar. Ini NORMAL, bukan bug. Untuk pemakaian/kirim ke sekolah gunakan `./gradlew clean assembleRelease --no-daemon --offline` (APK di `app/build/outputs/apk/release/app-release.apk`) → saat install langsung di-AOT → tanpa lag. Release sudah di-konfigurasi pakai `signingConfig signingConfigs.debug`. JANGAN report "lag pas awal" padahal install build debug.

### 2.2 Sebelum Bilang "Selesai"
- Pastikan semua import benar dan lengkap.
- Pastikan tidak ada import yang unused (warning acceptable, error tidak).
- Pastikan semua referensi BuildConfig menggunakan package namespace yang benar (`com.smpn38malteng.absensilate.BuildConfig`, BUKAN `com.osis.smkn1malteng.absensilate.BuildConfig`).
- Pastikan tidak ada `Name shadowed` yang menyebabkan bug (warning acceptable kalau harmless).
- Pastikan tidak ada `Unresolved reference` di output build.

### 2.3 Build Error Checklist
Kalau build gagal, periksa urut:
1. **Import salah/hilang** → tambah/fix import
2. **Unresolved reference** → cek package, cek apakah class ada, cek apakah library tersedia
3. **Type mismatch** → cek tipe return, cek nullable vs non-nullable
4. **BuildConfig not found** → cek namespace di `build.gradle` vs import di Kotlin
5. **Room schema error** → cek versi DB, cek migration, cek annotation

---

## 3. ATURAN ROOM DATABASE (CRITICAL)

> **Pelajaran: enum SMK→SMP crash, identity hash mismatch, migration lupa.**

### 3.1 Setiap Perubahan Schema = Migrasi Baru
- Kalau menambah/menghapus field di Entity → **WAJIB** tambah migration + naikkan version.
- Kalau mengubah tipe field → **WAJIB** tambah migration + naikkan version.
- Kalau menambah Entity baru → **WAJIB** tambah migration + naikkan version (CREATE TABLE).
- Kalau menambah Index → **WAJIB** tambah migration + naikkan version.

### 3.2 Enum di Room
- Room menyimpan enum sebagai `name()` (string). JANGAN asumsi enum name sama antar versi.
- **SELALU** buat TypeConverter untuk enum yang mungkin berubah antar versi.
- TypeConverter harus **toleran**: handle old values + new values + unknown values dengan fallback, JANGAN throw exception.
- Contoh: `SmpClass` TypeConverter harus handle "TIKR" (SMK lama) → mapping ke "VII" (SMP baru).

### 3.3 Migration Rules
- Migration ID = source version → target version. Jangan lupa registrasi migration di `addMigrations()`.
- Migration SQL harus DIUJI mental: apakah `UPDATE ... CASE WHEN` benar untuk semua kemungkinan value?
- **SELALU** tambah `Log.d(TAG, ...)` di setiap migration untuk debugging.
- **SELALU** tambah Room `Callback` (onCreate/onOpen) untuk logging.

### 3.4 Identity Hash
- Room menghitung identity hash berdasarkan **schema structure**, bukan data.
- Menambah TypeConverter **TIDAK** mengubah identity hash (karena column type tetap TEXT).
- Mengubah column name/type/menambah column → identity hash BERUBEA → **WAJIB** migration.
- Kalau identity hash tidak cocok → Room crash. Pastikan migration benar.

### 3.5 Entity Field Rules
- **JANGAN** ubah field type dari non-nullable ke nullable atau sebaliknya tanpa migration.
- **JANGAN** rename field tanpa migration `RENAME COLUMN`.
- **JANGAN** hapus field tanpa migration (atau `fallbackToDestructiveMigration`, tapi itu HAPUS SEMUA DATA).
- `var` hanya untuk field yang berubah setelah insert (mis. `violationCount`). Sisanya `val`.

### 3.6 Database Access Rules
- **JANGAN** akses database di main thread. Selalu pakai coroutine + `Dispatchers.IO`.
- **JANGAN** akses database di `init {}` block secara synchronous. Pakai `viewModelScope.launch`.
- **SELALU** wrap database access dengan try-catch.
- **SELALU** log jumlah data yang diterima dari Flow/Query.

---

## 4. ATURAN KODE & ARCHITECTURE

### 4.1 Naming Conventions
- Entity: `XxxEntity` (mis. `StudentEntity`, `AppConfigEntity`)
- DAO: `XxxDao` (mis. `StudentDao`, `AppConfigDao`)
- Screen: `XxxScreen` (mis. `DashboardScreen`, `RecapScreen`)
- ViewModel: `MainViewModel` (single ViewModel untuk semua screen)
- Engine/Utility: `XxxEngine` / `XxxUtil` / `XxxExporter` (mis. `CsvEngine`, `CompactSerializer`)

### 4.2 Compose Rules
- **JANGAN** panggil composable function di luar `@Composable` scope.
- **JANGAN** akses ViewModel database function langsung di composable. Gunakan state dari `collectAsState()`.
- **SELALU** gunakan `remember` untuk value yang tidak perlu recompose.
- **SELALU** gunakan `contentType` di LazyColumn items untuk performa.
- **JANGAN** gunakan `material3.ExperimentalMaterial3Api` tanpa global opt-in di `build.gradle`.

### 4.3 Kotlin Best Practices
- **JANGAN** gunakan `!!` (non-null assertion) kecuali benar-benar yakin value tidak null.
- **JANGAN** gunakan `TypeToken` dari Gson kalau sudah pakai `JsonArray`/`JsonObject` manual.
- **SELALU** handle `CancellationException` di coroutine (re-throw atau handle khusus).
- **JANGAN** shadow variable names dalam scope yang sama.

### 4.4 Import Rules
- **JANGAN** import yang tidak terpakai (clean code).
- **JANGAN** import dari package yang salah (mis. `com.osis...BuildConfig` padahal namespace `com.smpn38malteng...`).
- **SELALU** cek `build.gradle` namespace untuk BuildConfig/R imports.

---

## 5. PACKAGE / NAMESPACE CONSISTENCY (PALING KRITIS)

> **√ Pelajaran utama:** App crash sejak Fase 1 karena rebrand mengubah `namespace`/`applicationId`
> di `build.gradle` menjadi `com.smpn38malteng.absensilate`, TAPI semua source file tetap
> `package com.osis.smkn1malteng.absensilate`. Akibatnya manifest di-prefix namespace
> (`com.smpn38malteng.absensilate.MainActivity`) → cari class yang TIDAK ADA →
> `ClassNotFoundException` saat launch → **crash sepersekian detik setelah splash + log kosong**.

### 5.1 Konfigurasi Paket Proyek INI (WAJIB hafal)
| Aspek | Nilai | File |
|-------|-------|------|
| `namespace` (build.gradle) | `com.smpn38malteng.absensilate` | `app/build.gradle` line 8 |
| `applicationId` | `com.smpn38malteng.absensilate` | `app/build.gradle` line 12 |
| Source package SEMUA file Kotlin | `com.osis.smkn1malteng.absensilate` | semua `.kt` |
| Generated `R` class | `com.smpn38malteng.absensilate.R` | (ikut namespace) |
| Generated `BuildConfig` | `com.smpn38malteng.absensilate.BuildConfig` | (ikut namespace) |

**KONSEKUENSI:** `namespace ≠ source package` → komponen di manifest TIDAK boleh pakai
shortcut `.` (dot prefix). WAJIB fully-qualified ke source package:
`com.osis.smkn1malteng.absensilate.MainActivity`.
- `R` dan `BuildConfig` → pakai package **namespace** (`com.smpn38malteng.absensilate.X`).
- Activity, Application, Service, Receiver (component) → pakai package **source**
  (`com.osis.smkn1malteng.absensilate.X`) via nama fully-qualified DI MANIFEST.

### 5.2 Aturan Manifest Component
- **JANGAN PERNAH** menulis `android:name=".MainActivity"` atau `.App` di manifest.
  Di proyek dengan namespace ≠ source package, itu resolve ke namespace → crash.
- **SELALU** tulis nama lengkap:
  ```xml
  <application android:name="com.osis.smkn1malteng.absensilate.AbsensiLateApp" ...>
      <activity android:name="com.osis.smkn1malteng.absensilate.MainActivity" ...>
  ```
- Kalau ditambahkan Activity/Service/Receiver BARU → nama fully-qualified source package.

### 5.3 Verifikasi WAJIB Setelah Build (sebelum bilang "selesai")
Periksa **merged manifest** untuk memastikan nama component resolve ke class yang benar:
```bash
# 1. Lihat nama application/activity SEBENARNYA di APK
grep -E "android:name=\"com\.smpn38malteng" app/build/intermediates/merged_manifest/debug/AndroidManifest.xml

# 2. Pastikan SEMUA component (bukan library) pakai com.osis.smkn1malteng
grep -E "android:name=\"\.|android:name=\"com\.smpn38malteng\.(MainActivity|AbsensiLateApp)" \
  app/build/intermediates/merged_manifest/debug/AndroidManifest.xml

# 3. Pastikan class asli ADA di hasil kompilasi
find app/build/tmp/kotlin-classes/debug \
  -path "*com/osis/smkn1malteng/absensilate/MainActivity.class" -o \
  -path "*com/osis/smkn1malteng/absensilate/AbsensiLateApp.class"
```

**PASS criteria:**
- ✅ Semua component milik app di merged manifest = `com.osis.smkn1malteng.absensilate.*`
- ✅ `.` shortcut component TIDAK ADA di merged manifest (selain library pihak ketiga)
- ✅ Class file cocok dengan nama component di merged manifest
- ❌ Kalau ada `com.smpn38malteng.absensilate.MainActivity` di merged manifest → CRASH → FIX manifest

### 5.4 Gejala Buruk Yang Harus Langsung Dikenali
- App crash **sepersekian detik** setelah splash/logo muncul
- Log aplikasi **tidak muncul sama sekali** di logcat (Application gagal dibuat)
- Install sukses tapi ikon langsung crash
- Gejala ini = hampir selalu manifest component mismatch (bukan bug di Kotlin)

### 5.5 Setiap Rebrand / Rename Package
1. Ganti `namespace` DAN `applicationId` → **THEN** update SEMUA `package` di `.kt` source
   ATAU fully-qualified di manifest (lihat 5.2).
2. Verifikasi sesuai 5.3.
3. Kalau hanya salah satu diubah → jelaskan ke user risiko + tawarkan opsi konsisten.

---

## 6. ATURAN KEAMANAN

### 6.1 Secrets
- **TIDAK BOLEH** hardcode bot token, API key, atau secrets di source code Kotlin.
- **SELALU** simpan di `local.properties` (jangan pernah docopy ke publik; ikut tersimpan di backup folder).
- **SELALU** akses via `BuildConfig` yang di-generate dari `build.gradle`.
- Format di `build.gradle`:
  ```groovy
  buildConfigField "String", "SECRET_NAME", "\"${localProperties.getProperty('SECRET_NAME') ?: ''}\""
  ```

### 6.2 Network
- **SELALU** unbind network setelah `bindProcessToNetwork()`.
- **SELALU** tambah timeout pada network request (jangan biarkan hang selamanya).
- **SELALU** handle `CancellationException` di coroutine network.

---

## 7. ATURAN DEBUGGING

### 7.1 Log Tags yang Wajib Ada
- `AppDatabase` → untuk database lifecycle (open, create, migration)
- `MainViewModel` → untuk ViewModel init, load data, error
- `MainActivity` → untuk activity lifecycle
- `AbsensiLateApp` → untuk application-level crash handler
- `Converters` → untuk TypeConverter fallback (old SMK values)
- `CRASH:` → untuk uncaught exception (global crash handler)

### 7.2 Cara Debug Crash
```bash
# Lihat crash terakhir:
adb logcat -s "CRASH:" -d | tail -30

# Lihat semua log startup:
adb logcat -s "AppDatabase:" "MainViewModel:" "MainActivity:" "AbsensiLateApp:"

# Lihat Room-specific logs:
adb logcat -s "Room:" "RoomWarnings:"
```

### 7.3 Debug Checklist Kalau App Crash
1. Cek `adb logcat -s CRASH:` → cari exception message
2. Cek `adb logcat -s AppDatabase:` → apakah DB opened/created?
3. Cek `adb logcat -s MainViewModel:` → apakah init berhasil?
4. Kalau Room crash → cek migration SQL, cek enum TypeConverter
5. Kalau Compose crash → cek theme XML, cek resource reference

---

## 8. KONTEKS PROYEK

### 8.1 Identitas
- **Nama:** AbsensiLate — SMP Negeri 38 Maluku Tengah
- **Package (namespace & applicationId di build.gradle):** `com.smpn38malteng.absensilate`
- **Source package (SEMUA source file `.kt`):** `com.osis.smkn1malteng.absensilate`
- **⚠️ namespace ≠ source package → komponen manifest WAJIB fully-qualified (lihat Section 5)**
- **Stack:** Kotlin + Jetpack Compose + Room + KSP, minSdk 29, targetSdk 34
- **AGP:** 8.2.0, Kotlin: 1.9.20, Compose BOM: 2024.02.00, Room: 2.6.1

### 8.2 Fase Selesai
| Fase | Deskripsi | DB Version |
|------|-----------|------------|
| 1 | Rebranding SMKN 1 → SMPN 38 Maluku Tengah | - |
| 2 | `parentPhone` → `waliKelasPhone` (notifikasi ke wali kelas) | v3 |
| 3 | `StudentClass`/`StudentMajor` → `SmpClass`/`SmpSubClass`, `jurusan` → `subKelas` | v4 |
| 4 | Optimasi performa 720+ siswa (indices, transactions, remember, contentType) | v5 |
| 5 | Multi-select rekap siswa (checkbox, RecapScreen) | - |
| 6 | Export rekap ke CSV/PDF (RecapExporter, MediaStore) | - |
| Security | QR versioning, safe-parse import, secrets via BuildConfig, network hygiene | v6 |
| Crash | Fix manifest component ClassNotFoundException (FQN `com.osis.smkn1malteng...`) + crash handler + logging | v6 |
| 7 | Max pelanggaran panggilan ortu (`app_config.maxViolation`, single source of truth, default 2) | v7 |
| 8 | **Kelola Data**: Backup Nama Siswa (roster tanpa pelanggaran), Restore (GANTI TOTAL), Hapus Semua (ketik "HAPUS"). **Import CSV diubah: merge → TAMBAH (append), data lama tidak diubah** | v7 |

> 🔥 **Kelola Data vs Export/Import:** Export/Import yang lama = share/merge data antar HP
> (Import CSV sekarang = tambah/append, tidak menyentuh siswa lama). Kelola Data = manipulasi
> seluruh data: Backup roster (hanya nama+kelas+HP), Restore = GANTI TOTAL (hapus semua lalu isi),
> Hapus Semua = wipe + reset config (maxViolation→2, week/year→sekarang). Restore/backup DIJAMIN
> berbeda format header (`Nama,Kelas,SubKelas,NoHP,NoHP_WaliKelas`) dengan CSV export lama (9 kolom)
> → header divalidasi dulu di `RosterBackup.parseRosterFromUri`.

### 8.3 Database Schema (v7)
- **entities:** `StudentEntity`, `AppConfigEntity`
- **version:** 7
- **TypeConverters:** `Converters` (timestamps, SmpClass, SmpSubClass)
- **Migrations:** v1→v2 (uuid), v2→v3 (rename parentPhone), v3→v4 (rename jurusan), v4→v5 (indices), v5→v6 (enum conversion), v6→v7 (ADD COLUMN maxViolation pada app_config)
- **Format tampilan kelas:** `"${kelas.display}-${subKelas.display}"` → contoh "7-Sains"

### 8.4 File Kritis
| File | Fungsi |
|------|--------|
| `AppDatabase.kt` | DB singleton, migrations, version |
| `StudentEntity.kt` | Room entity dengan indices |
| `StudentDao.kt` | Query CRUD + Flow |
| `Converters.kt` | TypeConverter untuk enum (backward compat) |
| `MainViewModel.kt` | Semua business logic, UiState |
| `MainActivity.kt` | Composable App(), dialog handlers |
| `TelegramSender.kt` | Kirim ke Telegram via BuildConfig secrets |
| `SyncClient.kt` | Network sync, bind/unbind |
| `CompactSerializer.kt` | QR versioning v2 |
| `CsvEngine.kt` | CSV export/import (import = tambah/append) |
| `RosterBackup.kt` | Backup/restore Nama Siswa (roster CSV terpisah, header divalidasi) |
| `RecapExporter.kt` | Rekap CSV/PDF export |
| `build.gradle` | Dependencies, buildConfigField |
| `AndroidManifest.xml` | Component names WAJIB fully-qualified (Section 5) |
| `local.properties` | Secrets (disimpan di backup folder, jangan disebar) |

---

## 9. CHECKLIST SEBELUM KATA "SELESAI"

- [ ] Semua file yang diedit sudah dibaca dulu
- [ ] Semua import benar dan lengkap
- [ ] BuildConfig references pakai namespace yang benar
- [ ] **Manifest component pakai nama fully-qualified `com.osis.smkn1malteng...` (bukan `.MainActivity`)**
- [ ] **Verifikasi 5.3 dijalankan** (merged manifest tidak ada `com.smpn38malteng.MainActivity`/`.Application`, class file cocok)
- [ ] Room migration ada (kalau schema berubah)
- [ ] TypeConverter tolerant untuk old values
- [ ] Try-catch di semua database access
- [ ] Log.d/Log.e di critical paths
- [ ] Tidak ada hard-coded secrets
- [ ] Network bind selalu dilepas di finally
- [ ] User sudah diberitahu untuk build + test
