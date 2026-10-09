package com.fleetflow.mobile.ui.screens

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.fleetflow.mobile.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ComprovanteAnexo(
    comprovanteUri: Uri?,
    onComprovanteChange: (Uri?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var uriTemporariaCamera by remember { mutableStateOf<Uri?>(null) }

    val launcherCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { sucesso ->
        if (sucesso) onComprovanteChange(uriTemporariaCamera)
    }

    val launcherPermissaoCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            val uri = criarUriTemporaria(context)
            uriTemporariaCamera = uri
            launcherCamera.launch(uri)
        }
    }

    val launcherGaleria = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onComprovanteChange(uri)
    }

    Column(modifier = modifier) {
        Text("Comprovante", color = TextoSecundario, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        if (comprovanteUri != null) {
            var bitmap by remember(comprovanteUri) { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(comprovanteUri) {
                bitmap = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(comprovanteUri)?.use {
                            BitmapFactory.decodeStream(it)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CinzaSuave)
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Comprovante anexado",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Vermelho)
                        .clickable { onComprovanteChange(null) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Remover", color = Branco, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val jaTemPermissao = ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (jaTemPermissao) {
                        val uri = criarUriTemporaria(context)
                        uriTemporariaCamera = uri
                        launcherCamera.launch(uri)
                    } else {
                        launcherPermissaoCamera.launch(android.Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Petroleo)
            ) {
                Text("📷 Câmera", color = Branco, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    launcherGaleria.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AmbarDourado)
            ) {
                Text("🖼 Galeria", color = Petroleo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun criarUriTemporaria(context: android.content.Context): Uri {
    val pastaImagens = context.getExternalFilesDir("images") ?: context.filesDir
    pastaImagens.mkdirs()
    val arquivo = File(pastaImagens, "comprovante_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        arquivo
    )
}