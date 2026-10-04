package com.veyra.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {
    const val MODEL_FILE = "gemma-3-1b-it-Q4_K_S.gguf"
    const val MODEL_URL =
        "https://huggingface.co/unsloth/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_S.gguf?download=true"

    suspend fun download(
        context: Context,
        onProgress: (Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "models").apply { mkdirs() }
        val destination = File(directory, MODEL_FILE)
        if (destination.exists() && destination.length() > 0L) return@withContext destination

        val temporary = File(directory, "$MODEL_FILE.part")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "VEYRA-Android/0.1")
            }
            connection.connect()
            val status = connection.responseCode
            check(status in 200..299) { "Download HTTP $status" }
            val total = connection.contentLengthLong

            BufferedInputStream(connection.inputStream, 1024 * 1024).use { input ->
                BufferedOutputStream(temporary.outputStream(), 1024 * 1024).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var downloaded = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                    output.flush()
                    check(downloaded > 0L) { "O servidor retornou um arquivo vazio." }
                    if (total > 0L) check(downloaded == total) { "Download incompleto ($downloaded/$total bytes)." }
                }
            }
            if (destination.exists()) destination.delete()
            check(temporary.renameTo(destination)) { "Não foi possível finalizar o arquivo do modelo." }
            destination
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }
}
