package com.introcarbon.beatrice.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class BeatriceModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val defaultPitch: Float,
    val downloadUrl: String
)

object ModelManager {

    val AVAILABLE_MODELS = listOf(
        BeatriceModelInfo(
            id = "model_a1_sherlock_takt",
            name = "Model A1 (Sherlock + Takt)",
            description = "50% Cumberbatch + 50% Uchiyama (Calm Studio Baritone)",
            defaultPitch = 0.0f,
            downloadUrl = "https://github.com/Intro0siddiqui/beatrice-android/releases/download/v1.0.0/beatrice_model_a1_sherlock_takt.zip"
        ),
        BeatriceModelInfo(
            id = "model_a2_sherlock_nine",
            name = "Model A2 (Sherlock + Nine)",
            description = "50% Cumberbatch + 50% Ishikawa (Stoic Detached Baritone)",
            defaultPitch = 1.0f,
            downloadUrl = "https://github.com/Intro0siddiqui/beatrice-android/releases/download/v1.0.0/beatrice_model_a2_sherlock_nine.zip"
        ),
        BeatriceModelInfo(
            id = "model_b_twelve_l",
            name = "Model B (Twelve + L)",
            description = "50% Saitō + 50% Yamaguchi (Expressive Tenor / Countertenor)",
            defaultPitch = 0.5f,
            downloadUrl = "https://github.com/Intro0siddiqui/beatrice-android/releases/download/v1.0.0/beatrice_model_b_twelve_l.zip"
        )
    )

    fun getModelDir(context: Context, modelId: String): File {
        return File(context.filesDir, "models/$modelId")
    }

    fun isModelInstalled(context: Context, modelId: String): Boolean {
        val dir = getModelDir(context, modelId)
        val genFile = File(dir, "waveform_generator.bin")
        val phoneFile = File(dir, "phone_extractor.bin")
        val pitchFile = File(dir, "pitch_estimator.bin")
        return genFile.exists() && phoneFile.exists() && pitchFile.exists()
    }

    fun getInstalledModelIds(context: Context): Set<String> {
        return AVAILABLE_MODELS.filter { isModelInstalled(context, it.id) }.map { it.id }.toSet()
    }

    suspend fun downloadAndInstallModel(
        context: Context,
        modelInfo: BeatriceModelInfo,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val targetDir = getModelDir(context, modelInfo.id)
        targetDir.mkdirs()

        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val reqBuilder = Request.Builder().url(modelInfo.downloadUrl)
        
        // Load custom user token from preferences if downloading from private Hugging Face repo
        val prefs = context.getSharedPreferences("beatrice_prefs", Context.MODE_PRIVATE)
        val userToken = prefs.getString("hf_token", "") ?: ""
        if (userToken.isNotBlank() && modelInfo.downloadUrl.contains("huggingface.co")) {
            reqBuilder.addHeader("Authorization", "Bearer $userToken")
        }
        val request = reqBuilder.build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("ModelManager", "Failed to download model ${modelInfo.id}: HTTP ${response.code} ${response.message}")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()

            val tempZip = File(context.cacheDir, "${modelInfo.id}.zip")
            val outputStream = FileOutputStream(tempZip)
            val inputStream = body.byteStream()

            val buffer = ByteArray(8192)
            var totalRead = 0L
            var read: Int

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                totalRead += read
                if (contentLength > 0) {
                    onProgress(totalRead.toFloat() / contentLength.toFloat())
                }
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Unzip into targetDir
            ZipInputStream(tempZip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(targetDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            tempZip.delete()
            return@withContext isModelInstalled(context, modelInfo.id)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}
