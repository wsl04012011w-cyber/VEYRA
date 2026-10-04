package com.veyra.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF0B0B0D)
private val SurfaceDark = Color(0xFF19191C)
private val Muted = Color(0xFF9A9A9F)
private val Border = Color(0xFF303034)

@Composable
fun VeyraApp() {
    val context = LocalContext.current
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(context, uri)
            val isGguf = name.endsWith(".gguf", ignoreCase = true)
            if (isGguf) {
                runCatching {
                    // Keep access to the selected document after process restarts.
                    // Native model loading will consume this URI in the engine stage.
                    // The URI itself is intentionally not copied into app memory.
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                selectedModel = name
                importMessage = "Arquivo selecionado. A integração de inferência será ativada na próxima etapa."
            } else {
                importMessage = "Selecione um arquivo com extensão .gguf."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FourPointStar(Modifier.size(31.dp))
                Text(
                    text = "V E Y R A",
                    modifier = Modifier.padding(start = 14.dp),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Configurações",
                        tint = Color.White,
                        modifier = Modifier.size(27.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF29292C))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FourPointStar(Modifier.size(76.dp))
                Spacer(Modifier.height(36.dp))
                Text(
                    text = "Sua inteligência.\nNo seu controle.",
                    color = Color.White,
                    fontSize = 27.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Converse com seus modelos de IA localmente.",
                    color = Muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(62.dp))
                OutlinedButton(
                    onClick = { modelPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(23.dp)
                    )
                    Text(
                        text = "  Carregar modelo",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (selectedModel != null) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Selecionado: $selectedModel",
                        color = Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp)
                    .border(1.dp, Border, RoundedCornerShape(20.dp)),
                color = SurfaceDark,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+", color = Color.White, fontSize = 30.sp)
                    Text(
                        text = "  Digite sua mensagem...",
                        color = Muted,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text("↑", color = Muted, fontSize = 27.sp)
                }
            }

            Text(
                text = "Seus modelos e conversas permanecem no seu dispositivo.",
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp)
            )
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = SurfaceDark,
            title = { Text("VEYRA", color = Color.White) },
            text = {
                Text(
                    "Versão 0.1.0\nInferência local: em desenvolvimento.",
                    color = Muted
                )
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) {
                    Text("Fechar", color = Color.White)
                }
            }
        )
    }

    importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { importMessage = null },
            containerColor = SurfaceDark,
            title = { Text("Modelos GGUF", color = Color.White) },
            text = { Text(message, color = Muted) },
            confirmButton = {
                TextButton(onClick = { importMessage = null }) {
                    Text("Entendi", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun FourPointStar(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.56f, h * 0.36f, w * 0.64f, h * 0.44f, w, h * 0.5f)
            cubicTo(w * 0.64f, h * 0.56f, w * 0.56f, h * 0.64f, w * 0.5f, h)
            cubicTo(w * 0.44f, h * 0.64f, w * 0.36f, h * 0.56f, 0f, h * 0.5f)
            cubicTo(w * 0.36f, h * 0.44f, w * 0.44f, h * 0.36f, w * 0.5f, 0f)
            close()
        }
        drawPath(path, Color.White)
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) ?: "modelo.gguf" else "modelo.gguf"
        } ?: "modelo.gguf"
