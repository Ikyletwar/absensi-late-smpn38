package com.osis.smkn1malteng.absensilate.ui.screens.components

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smpn38malteng.absensilate.R

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // Fungsi untuk membuka aplikasi (single package)
    fun openAppOrWeb(packageName: String, webUrl: String) {
        try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                context.startActivity(webIntent)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
            context.startActivity(webIntent)
        }
    }

    // Fungsi untuk membuka aplikasi dengan multiple package name (untuk TikTok)
    fun openAppOrWeb(packageNames: List<String>, webUrl: String) {
        for (packageName in packageNames) {
            try {
                packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    context.startActivity(intent)
                    return
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // Package tidak ditemukan, lanjut ke package berikutnya
            }
        }
        // Jika tidak ada package yang ditemukan, buka web
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Foto Profile (lingkaran)
                Image(
                    painter = painterResource(id = R.drawable.profil),
                    contentDescription = "Profile Photo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Nama
                Text(
                    text = "Hizkia Letwar",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "aka Nihongo",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Kelas & Jurusan
                Text(
                    text = "Pencipta & Pengembang Aplikasi Absensi Late",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Version
                Text(
                    text = "Version 1.0",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Divider()

                Spacer(modifier = Modifier.height(12.dp))

                // Social Media Pribadi
                Text(
                    text = "Follow Me",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Instagram Pribadi
                    IconButton(onClick = {
                        openAppOrWeb(
                            packageName = "com.instagram.android",
                            webUrl = "https://www.instagram.com/official.smpn38malteng/"
                        )
                    }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_instagram),
                            contentDescription = "Instagram",
                            tint = Color(0xFFE4405F)
                        )
                    }
                    // WhatsApp Pribadi
                    IconButton(onClick = {
                        openAppOrWeb(
                            packageName = "com.whatsapp",
                            webUrl = "https://wa.me/6281227237338"
                        )
                    }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_whatsapp),
                            contentDescription = "WhatsApp",
                            tint = Color.Unspecified
                        )
                    }
                    // TikTok Pribadi - coba dua package name
                    IconButton(onClick = {
                        openAppOrWeb(
                            packageNames = listOf(
                                "com.ss.android.ugc.trill",  // Asia (termasuk Indonesia)
                                "com.zhiliaoapp.musically"   // Global
                            ),
                            webUrl = "https://www.tiktok.com/@ikyletwar"
                        )
                    }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_tiktok),
                            contentDescription = "TikTok",
                            tint = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Official School",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Instagram Sekolah
                    IconButton(onClick = {
                        openAppOrWeb(
                            packageName = "com.instagram.android",
                            webUrl = "https://www.instagram.com/official.smpn38malteng/"
                        )
                    }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_instagram),
                            contentDescription = "School Instagram",
                            tint = Color(0xFFE4405F)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}