package com.justpdf

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileUtils {

    /**
     * Resolves any incoming URI (content://, file://) to a usable [File] in the app's cache dir.
     *
     * - content:// URIs (from FileProvider, Downloads, Drive, etc.) are copied to cacheDir.
     * - file:// URIs are used directly if readable, otherwise copied to cacheDir.
     *
     * Returns null if the URI cannot be resolved or the file cannot be read.
     */
    fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            when (uri.scheme?.lowercase()) {
                "file" -> {
                    val path = uri.path ?: return null
                    val file = File(path)
                    if (file.exists() && file.canRead()) file
                    else copyToCacheDir(context, uri)
                }
                "content" -> copyToCacheDir(context, uri)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copies a content:// or file:// URI stream into a temp file in cacheDir.
     * The temp file is named "justpdf_current.pdf" and is overwritten on each open.
     */
    private fun copyToCacheDir(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val outFile = File(context.cacheDir, "justpdf_current.pdf")
            FileOutputStream(outFile).use { out ->
                inputStream.use { it.copyTo(out) }
            }
            outFile
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Wraps a [File] in a FileProvider content URI suitable for sharing with other apps.
     * Requires the FileProvider to be declared in AndroidManifest.xml with authority
     * "${applicationId}.fileprovider".
     */
    fun getShareUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
