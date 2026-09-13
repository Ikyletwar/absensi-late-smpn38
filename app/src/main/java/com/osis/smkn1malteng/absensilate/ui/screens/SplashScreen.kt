package com.osis.smkn1malteng.absensilate.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smpn38malteng.absensilate.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    // Tutup splash cepat (data sudah dimuat di background; dashboard punya loading state sendiri)
    LaunchedEffect(Unit) {
        delay(1200)
        onTimeout()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo sekolah
        Image(
            painter = painterResource(id = R.drawable.logo_sekolah),
            contentDescription = "Logo Sekolah",
            modifier = Modifier.size(180.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Teks "Absensi Late" warna hitam
        Text(
            text = "Absensi Late\nSMP Negeri 38 Maluku Tengah",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = Color.Black,
                fontSize = 24.sp
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Three dot animation - menggunakan pendekatan resmi Android
        ThreeDotsLoading()
    }
}

@Composable
fun ThreeDotsLoading() {
    // Ini adalah infinite progress indicator dengan 3 titik berdenyut (pulsing)[reference:4]
    @Composable
    fun Dot(scale: State<Float>) {
        Box(
            modifier = Modifier
                .padding(5.dp)
                .size(20.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(Color.Black, shape = CircleShape)
        )
    }

    val infiniteTransition = rememberInfiniteTransition()

    // Titik 1 - tidak ada offset[reference:5]
    val scale1 = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Titik 2 - offset 150ms[reference:6]
    val scale2 = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                offsetMillis = 150,
                StartOffsetType.FastForward
            )
        )
    )

    // Titik 3 - offset 300ms[reference:7]
    val scale3 = infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                offsetMillis = 300,
                StartOffsetType.FastForward
            )
        )
    )

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(scale1)
        Dot(scale2)
        Dot(scale3)
    }
}