package com.veyra.app.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veyra.app.data.ModelDownloader
import com.veyra.app.nativeengine.NativeGenerationCallback
import com.veyra.app.nativeengine.NativeLlama
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Background = Color(0xFF0B0B0D)
private val SurfaceDark = Color(0xFF19191C)
private val Muted = Color(0xFF9A9A9F)
private val Border = Color(0xFF303034)

private data class ChatMessage(val role: String, val content: String)

@Composable
fun VeyraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var messages by remember { mutableStateOf(emptyList<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var modelReady by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    fun downloadModel() {
        if (downloading || modelReady) return
        scope.launch {
            downloading = true
            downloadProgress = 0
            status = "Preparando download do Gemma 3 1B..."
            try {
                val modelFile: File = ModelDownloader.download(context) { received, total ->
                    if (total > 0L) {
                        val percent = ((received * 100L) / total).toInt().coerceIn(0, 100)
                        mainHandler.post { downloadProgress = percent }
                    }
                }
                status = "Carregando modelo na engine local..."
                val error = withContext(Dispatchers.IO) {
                    NativeLlama.nativeLoadModel(modelFile.absolutePath)
                }
                if (error.isBlank()) {
                    modelReady = true
                    status = "Gemma 3 1B pronto · execução local"
                } else {
                    // A failed native load must not leave a broken cached model blocking retries.
                    modelFile.delete()
                    status = error
                }
            } catch (error: Exception) {
                status = "Não foi possível baixar/carregar o modelo: ${error.message ?: "erro desconhecido"}"
            } finally {
                downloading = false
            }
        }
    }

    fun sendMessage() {
        val userText = input.trim()
        if (!modelReady || generating || userText.isEmpty()) return
        val history = messages
        val prompt = (history + ChatMessage("user", userText))
            .joinToString("\n") { message ->
                "${message.role}: ${message.content}"
            } + "\nassistant:"
        messages = history + ChatMessage("user", userText) + ChatMessage("assistant", "")
        input = ""
        generating = true
        status = null

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    NativeLlama.nativeGenerate(prompt, object : NativeGenerationCallback {
                        override fun onToken(token: String) {
                            mainHandler.post {
                                val last = messages.lastOrNull()
                                if (last?.role == "assistant") {
                                    messages = messages.dropLast(1) + last.copy(content = last.content + token)
                                }
                            }
                        }

                        override fun onError(message: String) {
                            mainHandler.post { status = message }
                        }

                        override fun onComplete() = Unit
                    })
                }
            } catch (error: Exception) {
                status = "Erro na geração: ${error.message ?: "falha nativa"}"
            } finally {
                generating = false
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
                    GearIcon(Modifier.size(27.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF29292C))
            )

            if (!modelReady) {
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
                        text = "Converse com seus modelos de IA\nlocalmente.",
                        color = Muted,
                        fontSize = 15.sp,
                        lineHeight = 25.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(62.dp))
                    OutlinedButton(
                        onClick = { downloadModel() },
                        enabled = !downloading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(
                            text = if (downloading) "Baixando modelo  $downloadProgress%" else "↓   Baixar modelo",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (downloading) {
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    status?.let {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = it,
                            color = Muted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "GEMMA 3 1B  ·  LOCAL",
                        color = Muted,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(messages) { _, message ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = if (message.role == "user") SurfaceDark else Color.Transparent,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .widthIn(max = 320.dp)
                                        .then(
                                            if (message.role == "user") Modifier.border(1.dp, Border, RoundedCornerShape(18.dp))
                                            else Modifier
                                        )
                                ) {
                                    Text(
                                        text = message.content.ifEmpty { if (generating) "…" else "" },
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        lineHeight = 23.sp,
                                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }
                    status?.let {
                        Text(
                            text = it,
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 7.dp)
                    .border(1.dp, Border, RoundedCornerShape(22.dp)),
                color = SurfaceDark,
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("+", color = Color.White, fontSize = 29.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 13.dp),
                        enabled = modelReady && !generating,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            Box {
                                if (input.isEmpty()) {
                                    Text("Digite sua mensagem...", color = Muted, fontSize = 16.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                    IconButton(
                        onClick = {
                            if (generating) {
                                NativeLlama.nativeStop()
                            } else {
                                sendMessage()
                            }
                        },
                        enabled = if (generating) true else modelReady && input.isNotBlank()
                    ) {
                        Text(
                            text = if (generating) "■" else "↑",
                            color = if (modelReady || generating) Color.White else Muted,
                            fontSize = 27.sp
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = SurfaceDark,
            title = { Text("VEYRA", color = Color.White) },
            text = {
                Text(
                    "Versão 0.2.0\nEngine: llama.cpp\nModelo: Gemma 3 1B Q4_K_S\nInferência executada localmente.",
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
}

@Composable
private fun FourPointStar(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
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


@Composable
private fun GearIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.31f
        val toothOuter = size.minDimension * 0.46f
        val stroke = size.minDimension * 0.105f

        for (i in 0 until 8) {
            val angle = (i * PI / 4.0).toFloat()
            val inner = Offset(
                center.x + cos(angle) * radius,
                center.y + sin(angle) * radius
            )
            val outer = Offset(
                center.x + cos(angle) * toothOuter,
                center.y + sin(angle) * toothOuter
            )
            drawLine(
                color = Color.White,
                start = inner,
                end = outer,
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = Background,
            radius = size.minDimension * 0.105f,
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = size.minDimension * 0.105f,
            center = center,
            style = Stroke(width = stroke * 0.72f)
        )
    }
}
