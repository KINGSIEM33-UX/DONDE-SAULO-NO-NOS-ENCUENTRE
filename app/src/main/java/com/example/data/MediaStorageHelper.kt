package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaStorageHelper {
    private const val TAG = "MediaStorageHelper"

    private fun mediaDir(context: Context): File {
        return File(context.filesDir, "media").apply { if (!exists()) mkdirs() }
    }

    private fun voiceDir(context: Context): File {
        return File(context.filesDir, "voice_notes").apply { if (!exists()) mkdirs() }
    }

    private fun cameraCacheDir(context: Context): File {
        return File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
    }

    fun isRenderableImageUri(uriOrPreset: String): Boolean {
        return uriOrPreset.startsWith("content://") ||
            uriOrPreset.startsWith("file://") ||
            uriOrPreset.startsWith("http://") ||
            uriOrPreset.startsWith("https://") ||
            uriOrPreset.startsWith("/")
    }

    fun isRemoteMediaUrl(value: String): Boolean {
        return value.startsWith("https://") || value.startsWith("http://")
    }

    fun isLocalMediaReference(value: String): Boolean {
        return value.startsWith("content://") || value.startsWith("file://") || value.startsWith("/")
    }

    fun deleteOwnedLocalMedia(context: Context, value: String): Boolean {
        if (!isLocalMediaReference(value)) return false
        val path = when {
            value.startsWith("file://") -> Uri.parse(value).path
            value.startsWith("/") -> value
            else -> null
        } ?: return false
        val file = File(path).canonicalFile
        val root = mediaDir(context).canonicalFile
        if (file.parentFile != root) return false
        return file.delete()
    }

    fun toCoilModel(uriOrPreset: String): Any {
        return if (uriOrPreset.startsWith("/")) File(uriOrPreset) else uriOrPreset
    }

    fun createCameraCaptureFile(context: Context): Pair<File, Uri> {
        val file = File(cameraCacheDir(context), "adora_cam_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return file to uri
    }

    fun createVoiceNoteFile(context: Context): File {
        return File(voiceDir(context), "voice_${System.currentTimeMillis()}.m4a")
    }

    /**
     * Persists a picked/captured image. Authenticated users must receive a Firebase URL;
     * guests explicitly receive a local file URI and must not publish it remotely.
     */
    suspend fun persistAndOptionallyUploadImage(
        context: Context,
        sourceUri: Uri,
        folder: String,
        uid: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val localFile = File(mediaDir(context), "${folder}_${UUID.randomUUID().toString().take(12)}.jpg")
        val tempFile = File(localFile.parentFile, ".${localFile.name}.part")
        try {
            val copied = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
                check(tempFile.renameTo(localFile)) { "No se pudo completar la copia de la imagen" }
                true
            } ?: false
            check(copied) { "No se pudo leer la imagen seleccionada" }
            check(localFile.exists() && localFile.length() > 0L) { "La imagen seleccionada está vacía" }

            if (uid.isNullOrBlank()) {
                return@withContext Result.success(Uri.fromFile(localFile).toString())
            }

            check(FirebaseApp.getApps(context).isNotEmpty()) {
                "Firebase no está disponible para subir la imagen"
            }
            val storageRef = FirebaseStorage.getInstance().reference
                .child("users/$uid/$folder/${localFile.name}")
            storageRef.putFile(Uri.fromFile(localFile)).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            check(isRemoteMediaUrl(downloadUrl)) { "Firebase Storage no devolvió una URL remota válida" }
            localFile.delete()
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            tempFile.delete()
            if (!uid.isNullOrBlank()) localFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            if (!uid.isNullOrBlank()) localFile.delete()
            Log.e(TAG, "Image persistence/upload failed", e)
            Result.failure(e)
        }
    }

    suspend fun optionallyUploadVoiceNote(
        context: Context,
        localFile: File,
        uid: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            check(localFile.exists() && localFile.length() > 0L) { "El audio grabado no es válido" }
            if (uid.isNullOrBlank()) {
                return@withContext Result.success(localFile.absolutePath)
            }

            check(FirebaseApp.getApps(context).isNotEmpty()) {
                "Firebase no está disponible para subir el audio"
            }
            val storageRef = FirebaseStorage.getInstance().reference
                .child("users/$uid/voice_notes/${localFile.name}")
            storageRef.putFile(Uri.fromFile(localFile)).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()
            check(isRemoteMediaUrl(downloadUrl)) { "Firebase Storage no devolvió una URL remota válida" }
            Result.success(downloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice persistence/upload failed", e)
            Result.failure(e)
        }
    }

    suspend fun saveImageToGallery(context: Context, uriOrPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes: ByteArray? = when {
                uriOrPath.startsWith("file://") -> {
                    val f = File(Uri.parse(uriOrPath).path ?: return@withContext false)
                    if (f.exists()) f.readBytes() else null
                }
                uriOrPath.startsWith("/") -> {
                    val f = File(uriOrPath)
                    if (f.exists()) f.readBytes() else null
                }
                uriOrPath.startsWith("content://") -> {
                    context.contentResolver.openInputStream(Uri.parse(uriOrPath))?.use { it.readBytes() }
                }
                uriOrPath.startsWith("http://") || uriOrPath.startsWith("https://") -> {
                    java.net.URL(uriOrPath).openStream().use { it.readBytes() }
                }
                else -> null
            }

            if (bytes == null || bytes.isEmpty()) return@withContext false

            val filename = "Adora_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Adora")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false

            resolver.openOutputStream(insertedUri)?.use { out -> out.write(bytes) }
                ?: return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(insertedUri, values, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving image to gallery", e)
            false
        }
    }

    fun shareMediaOrText(context: Context, uriOrPath: String, caption: String) {
        try {
            val fileToShare: File? = when {
                uriOrPath.startsWith("file://") -> Uri.parse(uriOrPath).path?.let { File(it) }?.takeIf { it.exists() }
                uriOrPath.startsWith("/") -> File(uriOrPath).takeIf { it.exists() }
                uriOrPath.startsWith("content://") -> {
                    val tmp = File(context.cacheDir, "share_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(Uri.parse(uriOrPath))?.use { input ->
                        FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    tmp.takeIf { it.exists() && it.length() > 0 }
                }
                else -> null
            }

            if (fileToShare != null) {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    fileToShare
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir recuerdo"))
            } else {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Compartir recuerdo"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share media", e)
        }
    }
}
