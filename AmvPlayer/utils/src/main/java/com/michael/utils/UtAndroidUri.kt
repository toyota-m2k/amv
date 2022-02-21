package com.michael.utils

import android.R.attr.scheme
import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore.MediaColumns
import io.github.toyota32k.utils.UtLogger
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor


class UtAndroidUri(val uri: Uri) : Closeable {
    private var afd: AssetFileDescriptor? = null

    /**
     * @param mode mode – The string representation of the file mode. Can be "r", "w", "wt", "wa", "rw" or "rwt".
     */
    fun open(context: Context, mode:String="r"):FileDescriptor? {
        if(afd==null) {
            try {
                afd = context.contentResolver.openAssetFileDescriptor(uri, mode)
            } catch (e:Throwable) {
                UtLogger.stackTrace(e)
            }
        }
        return afd?.fileDescriptor
    }

    /**
     * ファイルの長さ
     */
    val length:Long
        get() {
            val afd = this.afd ?: throw IllegalStateException("file not opened.")
            return afd.length
        }

    fun detach():FileDescriptor? {
       val r = afd?.fileDescriptor
       afd = null
       return r
    }

    override fun close() {
        afd?.close()
        afd = null
    }

    fun getAsTempFile(context:Context):File? {
        val dir = context.cacheDir
        return context.contentResolver.openInputStream(uri)?.use { input->
            val file = File.createTempFile("tmp", ".mp4", dir)
            file.outputStream().use { output->
                input.copyTo(output)
                output.flush()
            }
            file
        }
    }
}