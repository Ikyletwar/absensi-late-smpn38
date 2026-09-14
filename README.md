# 📱 Absensi Late SMPN 38 — SMP Negeri 38 Maluku Tengah

Aplikasi **Android offline-first** untuk pencatatan absensi keterlambatan siswa dan manajemen pelanggaran di lingkungan **SMP Negeri 38 Maluku Tengah**. Dibangun dengan **Kotlin + Jetpack Compose + Room**, dirancang khusus untuk dipakai **seluruh guru, terutama guru piket**, di lapangan sehari-hari — tanpa membutuhkan koneksi internet yang stabil. Semua data tersimpan **lokal di perangkat** dan dapat dibagikan antar perangkat via **QR handshake**, atau dikirim ke **Telegram** sebagai laporan cadangan.

---

## 📸 Tampilan Aplikasi

> Gambar di bawah ini masih **placeholder** — silakan ganti dengan screenshot asli aplikasi.

| Tampilan Utama | Tentang Aplikasi | Form Input Siswa |
|---|---|---|
| ![Tampilan Utama](screenshots/tampilan.png) | ![Tentang Aplikasi](screenshots/about.png) | ![Input Siswa](screenshots/input.png) |

---

## ✨ Fitur Utama

- **Pencatatan keterlambatan & pelanggaran** satu ketukan — tekan `+1` pada kartu siswa, waktu terekam otomatis.
- **Tidak butuh internet**: seluruh data tinggal di perangkat (SQLite via Room), offline-first.
- **QR Handshake Sync**: bagikan data antar perangkat lewat hotspot lokal (`LocalOnlyHotspot` + server `NanoHTTPD`) — QR berisi gabungan **WiFi + URL**.
- **Kirim ke Server (Telegram)**: kirim **2 file sekaligus** — data terkompresi `.json.gz` + cadangan `.csv` (siap dibuka di Excel). Pesan info otomatis menampilkan nama & ukuran kedua file.
- **Kelola Data**: *Backup Nama Siswa* (roster), *Restore* (ganti total), *Hapus Semua Siswa* (konfirmasi ketik `HAPUS`), atur *Max Pelanggaran*.
- **Rekap pelanggaran**: layar rekap dengan multi-select, ekspor **CSV / PDF** via MediaStore.
- **Dark mode** + tema *Navy (aksen emas)*, ikon WhatsApp **transparan** tanpa kotak putih.

---

## 📑 Daftar Isi

1. [Deskripsi Aplikasi](#1-deskripsi-aplikasi)
2. [Fitur Lengkap](#2-fitur-lengkap)
3. [Teknologi](#3-teknologi)
4. [Struktur Proyek](#4-struktur-proyek)
5. [Persyaratan Build](#5-persyaratan-build)
6. [Cara Build APK](#6-cara-build-apk)
7. [Cara Install](#7-cara-install)
8. [Konfigurasi Secret (local.properties)](#8-konfigurasi-secret-localfolder-properties)
9. [Cara Pakai](#9-cara-pakai)
10. [FAQ](#10-faq)
11. [Kontribusi & Lisensi](#11-kontribusi--lisensi)
12. [Disclaimer Data](#12-disclaimer-data)

---

## 1. Deskripsi Aplikasi

**Absensi Late SMPN 38** adalah aplikasi Android buatan internal sekolah yang dipakai oleh:

1. **Guru piket** untuk mencatat siswa yang datang **terlambat** sepanjang hari.
2. **Guru mata pelajaran & wali kelas** untuk melihat rekapan pelanggaran dan menindaklanjuti.
3. **Guru BK / administrasi** untuk mengambil **rekap lengkap** dan menghubungi wali siswa.

Kegunaan utama aplikasi:

1. Mencatat keterlambatan & pelanggaran siswa setiap hari dengan cepat (satu ketukan).
2. Menghitung akumulasi pelanggaran per siswa dalam satu pekan.
3. Memberi **peringatan visual** ketika jumlah pelanggaran melewati ambang `maxViolation` (bawaan **3**).
4. Menghubungkan guru ke **WhatsApp wali kelas** untuk konfirmasi orang tua.
5. Membuat **rekapan** yang diekspor ke **CSV** (Excel) dan **PDF**.
6. Mengirim laporan cadangan ke **grup Telegram sekolah**.
7. **Sinkronisasi antar perangkat** piket lewat teknologi QR handshake (tanpa internet/kabel).

Aplikasi memakai arsitektur **single-Activity + Jetpack Compose**, satu `MainViewModel` sebagai pusat logika bisnis, **Room Database** dengan migrasi versi berjenjang, serta **TypeConverter** yang toleran terhadap perubahan skema antar versi. Tidak ada data yang dikirim ke luar kecuali saat fitur *Kirim Ke Server* dijalankan secara manual.

**Versi saat ini:** `1.0` (versionCode `1`).

---

## 2. Fitur Lengkap

### 2.1 Manajemen Siswa
- Tambah siswa individu (nama, kelas, **sub kelas**, No. HP siswa, No. HP **wali kelas**).
- Edit & **hapus siswa** (dengan konfirmasi agar tidak terhapus tanpa sengaja).
- Pencarian instan (nama, kelas, sub kelas), **filter** dan *sorting*.
- Kartu siswa berwarna **dinamis** sesuai `violationCount` vs `maxViolation` (hijau → kuning → merah).

### 2.2 Pencatatan Pelanggaran
- **`+1`** menambah satu pelanggaran serta mencatat timestamp otomatis.
- **Decrement**, **reset**, dan **hapus timestamp tertentu** pada detail siswa.
- **Multi-select batch**: rekap atau menambah pelanggaran untuk banyak siswa sekaligus via satu query batch.

### 2.3 QR Synchronization (Multi Perangkat Ruang Piket)
- *Export QR*: perangkat menjadi hotspot lokal (`LocalOnlyHotspot`) + menyajikan data via `NanoHTTPD`.
- *Scan QR*: perangkat lain memindai QR berisi **SSID + password + URL**, otomatis terhubung lalu mengunduh data.
- *Scan dari galeri*: didukung ML Kit Barcode Scanning.
- Data digabung dengan logika **merge berbasis UUID unik** — tanpa duplikat, saling melengkapi.

### 2.4 Kelola Data (Roster)
- **Backup Nama Siswa**: simpan roster (nama + kelas + sub kelas + HP) ke file CSV terpisah.
- **Restore Nama Siswa**: ganti total seluruh daftar siswa dari file backup (header divalidasi).
- **Hapus Semua Siswa**: wipe + reset konfigurasi, wajib mengetik **`HAPUS`**.
- **Import CSV (tambah/append)**: siswa lama **tidak diubah**, siswa baru ditambahkan.
- **Import `.json.gz`**: memakai parser aman `JsonArray` — kebal terhadap error *TypeToken* di build release (R8).

### 2.5 Rekap & Laporan
- Layar **Rekap Pelanggaran** dengan multi-select.
- Ekspor **CSV** (RFC 4180) & **PDF** melalui API **MediaStore**.
- Normalisasi nomor HP Indonesia ke format **62xxx** otomatis (`PhoneNumberUtil`).

### 2.6 Kirim Ke Server (Telegram)
- Sekali tekan: pesan info + **`students_<timestamp>.json.gz`** + **`students_<timestamp>.csv`** dikirim bersama.
- Menampilkan nama & ukuran kedua file sebagai bukti pengiriman.
- Token & chat ID dari `BuildConfig` (tidak di-hardcode).

### 2.7 Tampilan & Pengalaman
- Tema **biru navy (aksen emas)**; **dark mode** lewat `ThemeManager` + Material3.
- Splash screen dengan logo sekolah + animasi tiga titik.
- Ikon kontak (WhatsApp / Instagram / TikTok) **transparan**.
- *Haptic feedback*, *debounce* pencarian, `contentType` untuk performa daftar ratusan siswa.

### 2.8 Keamanan & Pemeliharaan
- Akses database dibungkus `try-catch` + log (`MainViewModel`, `AppDatabase`, `CRASH:`).
- **UUID unik per siswa**; migrasi Room berjenjang (v1→v8) termasuk enum kelas dan `maxViolation`.
- Network binding dilepas di `finally` setelah sinkronisasi.

---

## 3. Teknologi

| Lapisan | Teknologi |
|---|---|
| Bahasa | Kotlin 1.9.20 |
| UI | Jetpack Compose (BOM 2024.02.00), Material 3 |
| Persistensi | Room 2.6.1 + KSP, migrations, TypeConverter |
| Serialisasi | Gson (import aman via `JsonArray`), CompactSerializer (QR v2) |
| Jaringan | NanoHTTPD, OkHttp, `LocalOnlyHotspot`, `bindProcessToNetwork` |
| Scanner | ML Kit Barcode Scanning |
| Build | Gradle 8.2, AGP 8.2.0, R8 (minify di release) |
| Target | minSdk 29 (Android 10), targetSdk 34 |

---

## 4. Struktur Proyek

```
app/src/main/java/com/osis/smkn1malteng/absensilate/
├── data/
│   ├── local/          # Room: Entity, DAO, database & migrasi, TypeConverter
│   └── model/          # Enum kelas (VII/VIII/IX) & sub kelas (SmpClass, SmpSubClass)
├── network/            # TelegramSender, SyncClient, SyncServerManager
├── sync/               # QR sync, CompactSerializer, CsvEngine, RosterBackup
├── ui/
│   ├── screens/        # Dashboard, InputForm, Recap, Splash
│   ├── theme/          # Warna, tema, ThemeManager
│   └── viewmodel/      # MainViewModel (pusat logika)
└── util/               # PhoneNumberUtil, RecapExporter
```

---

## 5. Persyaratan Build

- **JDK 17** (disarankan; AGP 8.2 kompatibel dengan JDK 17).
- **Android SDK** (platform 34, build-tools).
- **Android Studio** (opsional — build bisa via CLI `./gradlew`).
- Koneksi internet saat *pertama* kali untuk mengunduh dependency (build berikutnya bisa `--offline`).

---

## 6. Cara Build APK

**Debug:**
```bash
./gradlew clean assembleDebug --no-daemon
# Output: app/build/outputs/apk/debug/app-debug.apk
```

**Release (R8 minify):**
```bash
./gradlew clean assembleRelease --no-daemon
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

### ⚠️ Catatan Penting sebelum Build
1. `local.properties` **diperlukan** untuk kredensial (Section 8).
2. `local.properties`, `build/`, `.gradle/`, `.kotlin/` **tidak ikut di-commit** (dijaga `.gitignore`).

---

## 7. Cara Install

1. Salin APK release ke HP Android (minSdk 29+).
2. Buka dari file manager → izinkan *install dari sumber tidak dikenal* bila diminta.
3. Jalankan aplikasi — database dibuat otomatis.
4. **Backup dulu** sebelum meng-upgrade versi (struktur database bisa berubah antar versi).

---

## 8. Konfigurasi Secret (local.folder Properties)

> 🙅 **JANGAN commit `local.properties`** — diabaikan `.gitignore`.

Buat file `local.properties` di **root proyek**:

```properties
sdk.dir=/home/username/Android/Sdk

# Seket Telegram (lihat @BotFather dan @userinfobot)
TELEGRAM_BOT_TOKEN=<TOKEN_BOT_DARI_BOTFATHER>
TELEGRAM_CHAT_ID=<CHAT_ID_GRUP_ATAU_USER>
```

Nilai tersebut otomatis masuk ke `BuildConfig` oleh `app/build.gradle`:

```groovy
buildConfigField "String", "TELEGRAM_BOT_TOKEN", "\"${localProperties.getProperty('TELEGRAM_BOT_TOKEN') ?: ''}\""
buildConfigField "String", "TELEGRAM_CHAT_ID", "\"${localProperties.getProperty('TELEGRAM_CHAT_ID') ?: ''}\""
```

Jika token kosong, fitur *Kirim Ke Server* dinonaktifkan secara aman.

---

## 9. Cara Pakai

1. **Tambah siswa** → isi nama, kelas (VII/VIII/IX), sub kelas (Sains / Bil / Seni / 1–5), nomor HP.
2. **Mencatat keterlambatan** → ketuk `+1` pada kartu siswa.
3. **Menghubungi wali kelas** → buka detail siswa → WhatsApp → ModalBottomSheet nomor.
4. **Pekan baru** → simpan laporan dulu (Kirim Ke Server / Export CSV), lalu tekan **Reset Semua** agar angka pelanggaran kembali nol. Aplikasi **tidak me-reset otomatis** — petugas yang menentukan waktunya.
5. **Rekap** → buka menu Rekap → pilih siswa → ekspor CSV/PDF.
6. **Kirim laporan** → *Kirim Ke Server* → kedua file dikirim ke Telegram.
7. **Sinkron antar perangkat piket** → perangkat A *Export QR*, perangkat B *Scan QR*.

---

## 10. FAQ

**Q: Data hilang kalau uninstall?**
Ya — data di perangkat. Selalu **Export CSV** / **Kirim Ke Server** sebelum uninstall.

**Q: Apakah butuh internet?**
Tidak untuk pencatatan harian. Internet hanya untuk *Kirim Ke Server* (Telegram).

**Q: Max pelanggaran bisa diubah?**
Bisa. Kelola Data → *Max Pelanggaran* (bawaan 3).

**Q: Bagaimana jika dua perangkat mencatat siswa yang sama?**
Gunakan **QR handshake** — UUID unik memastikan data saling melengkapi tanpa duplikat.

**Q: Kenapa import `.json.gz` sempat gagal "TypeToken"?**
Bug lama di build release (R8 membuang metadata generik). Sudah diperbaiki dengan parser `JsonArray` yang aman.

**Q: Apakah Reset Semua menghapus siswa?**
Tidak — hanya mengosongkan angka pelanggaran; nama, kelas, sub kelas, dan HP tetap tersimpan.

---

## 11. Kontribusi & Lisensi

Proyek ini dikembangkan untuk keperluan internal sekolah. Untuk kontribusi, silakan buat *Issue* atau *Pull Request*. **Lisensi:** lihat berkas `LICENSE` (jika ada); jika tidak ada, hubungi pengelola sekolah.

---

## 12. Disclaimer Data

Repositori ini **hanya berisi kode sumber**. **Tidak ada data siswa asli**, file CSV, backup database, atau token bot yang diunggah ke sini. Seluruh data sensitif sekolah tetap berada di perangkat yang dikelola pihak sekolah.