package com.osis.smkn1malteng.absensilate.ui.screens.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QrExportDialog(
    encodedData: String,
    studentCount: Int,
    onDismiss: () -> Unit,
    isLoading: Boolean = false,
    isHandshake: Boolean = false,  // 🔥 NEW: true = handshake mode (URL only)
    ssid: String? = null,          // 🔥 NEW: SSID for handshake
    password: String? = null,      // 🔥 NEW: Password for handshake
    onStartServer: (() -> Unit)? = null,  // 🔥 NEW: Start server callback
    onStopServer: (() -> Unit)? = null    // 🔥 NEW: Stop server callback
) {
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Generate QR Code when data is available
    if (!isLoading && qrBitmap == null && encodedData.isNotEmpty()) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(encodedData, BarcodeFormat.QR_CODE, 400, 400)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            qrBitmap = bitmap.asImageBitmap()
        } catch (e: Exception) {
            // QR generation failed
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isHandshake) Icons.Default.Wifi else Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHandshake) "QR Handshake" else "QR Export",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isHandshake) "Starting server..." else "Mengencode data...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (qrBitmap != null) {
                    // QR Code
                    androidx.compose.foundation.Image(
                        bitmap = qrBitmap!!,
                        contentDescription = "QR Code",
                        modifier = Modifier.size(300.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Info
                    if (isHandshake) {
                        // Handshake mode — show SSID and password
                        Text(
                            text = "🔗 Scan QR untuk sinkronisasi data",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Total data: $studentCount siswa",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (ssid != null && password != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📶 SSID: $ssid",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "🔑 Password: $password",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Copy credentials button
                            Row {
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(
                                            AnnotatedString("SSID: $ssid\nPassword: $password\nURL: $encodedData")
                                        )
                                        android.widget.Toast.makeText(
                                            context,
                                            "Credentials copied to clipboard",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy Credentials")
                                }
                            }
                        }
                        Text(
                            text = "⚠️ Pastikan perangkat lain terhubung ke hotspot ini",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        // Legacy mode
                        Text(
                            text = "Total data: $studentCount siswa",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan QR ini dengan HP lain untuk sinkronisasi data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Gagal generate QR Code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isHandshake && onStopServer != null) {
                    Button(
                        onClick = onStopServer,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Stop Server")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isHandshake) "Tutup" else "Tutup")
                }
            }
        }
    )
}
