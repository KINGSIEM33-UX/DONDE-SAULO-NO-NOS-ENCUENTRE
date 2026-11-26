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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
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
     * Copies a transient content:// URI or temporary camera file into persistent internal storage,
     * and optionally uploads it to Firebase Storage when authenticated and Firebase is available.
     */
    suspend fun persistAndOptionallyUploadImage(
        context: Context,
        sourceUri: Uri,
        folder: String,
        uid: String?
    ): String = withContext(Dispatchers.IO) {
        val localFile = File(mediaDir(context), "${folder}_${UUID.randomUUID().toString().take(12)}.jpg")
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to internal storage", e)
            return@withContext sourceUri.toString()
        }

        if (!localFile.exists() || localFile.length() == 0L) {
            return@withContext sourceUri.toString()
        }

        if (!uid.isNullOrBlank() && FirebaseApp.getApps(context).isNotEmpty()) {
            try {
                val storageRef = FirebaseStorage.getInstance().reference
                    .child("users/$uid/$folder/${localFile.name}")
                storageRef.putFile(Uri.fromFile(localFile)).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()
                return@withContext downloadUrl
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Storage upload skipped/failed, using local file: ${e.message}")
            }
        }

        Uri.fromFile(localFile).toString()
    }

    suspend fun optionallyUploadVoiceNote(
        context: Context,
        localFile: File,
        uid: String?
    ): String = withContext(Dispatchers.IO) {
        if (!localFile.exists() || localFile.length() == 0L) return@withContext ""
        if (!uid.isNullOrBlank() && FirebaseApp.getApps(context).isNotEmpty()) {
            try {
                val storageRef = FirebaseStorage.getInstance().reference
                    .child("users/$uid/voice_notes/${localFile.name}")
                storageRef.putFile(Uri.fromFile(localFile)).await()
                return@withContext storageRef.downloadUrl.await().toString()
            } catch (e: Exception) {
                Log.w(TAG, "Voice upload skipped/failed, keeping local path: ${e.message}")
            }
        }
        localFile.absolutePath
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

            resolver.openOutputStream(insertedUri)?.use { out ->
                out.write(bytes)
            } ?: return@withContext false

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
