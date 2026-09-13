package com.osis.smkn1malteng.absensilate.util

/**
 * Utility untuk normalisasi dan validasi nomor telepon Indonesia.
 * Mendukung format:
 * - 08xxxxxxxxxx
 * - 628xxxxxxxxxx
 * - 8xxxxxxxxxx (tanpa 0)
 * - 6208xxxxxxxxxx (diperbaiki ke 628)
 * - 082xxxxxxxxxxx
 * - 6281xxxxxxxxx
 */
object PhoneNumberUtil {

    /**
     * Normalisasi nomor telepon ke format internasional (628xxxxxxxxxx)
     * @return String format internasional tanpa tanda +, atau null jika tidak valid
     */
    fun normalizeToInternational(phone: String): String? {
        // Hapus semua karakter non-digit
        var cleaned = phone.replace(Regex("[^\\d]"), "")

        if (cleaned.isEmpty()) return null

        // Jika diawali '0', ganti dengan '62'
        if (cleaned.startsWith("0")) {
            cleaned = "62" + cleaned.drop(1)
        }
        // Jika diawali '62', biarkan
        else if (cleaned.startsWith("62")) {
            // sudah benar
        }
        // Jika diawali '8' (tanpa 0), tambahkan '62' di depan
        else if (cleaned.startsWith("8")) {
            cleaned = "62" + cleaned
        }
        // Jika diawali '6208' (kasus 62081234567890), perbaiki ke 628
        else if (cleaned.startsWith("6208")) {
            cleaned = "628" + cleaned.drop(4)
        }
        // Jika diawali '620' dan panjang > 10, kemungkinan 620812345 -> 62812345
        // Tapi kita tidak otomatis ubah karena bisa membingungkan, biarkan user memasukkan dengan benar.

        // Validasi akhir: harus diawali 628 dan diikuti 8-11 digit
        val regex = Regex("^628[0-9]{8,11}$")
        return if (regex.matches(cleaned)) cleaned else null
    }

    /**
     * Validasi nomor telepon.
     * @return pesan error jika tidak valid, null jika valid
     */
    fun validatePhone(phone: String): String? {
        if (phone.isBlank()) return null
        val normalized = normalizeToInternational(phone)
        if (normalized == null) {
            return "Format nomor tidak valid. Gunakan 08xxx, 628xxx, atau 8xxx"
        }
        // Cek panjang setelah normalisasi (biasanya 11-13 digit)
        if (normalized.length < 10 || normalized.length > 14) {
            return "Nomor terlalu pendek atau panjang (${normalized.length} digit)"
        }
        return null // valid
    }

    /**
     * Mendapatkan nomor yang siap digunakan untuk WhatsApp (format internasional tanpa +)
     */
    fun getWhatsAppNumber(phone: String): String? {
        return normalizeToInternational(phone)
    }
}
