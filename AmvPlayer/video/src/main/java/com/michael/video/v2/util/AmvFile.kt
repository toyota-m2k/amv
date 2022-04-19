package com.michael.video.v2.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.michael.video.v2.common.AmvSettings
import io.github.toyota32k.media.lib.converter.AndroidFile
import java.io.*
import java.lang.IllegalStateException

class AmvFile {
    val uri: Uri?
    val context: Context?
    val path: File?

    constructor(uri: Uri, context: Context) {
        this.uri = uri
        this.context = context
        this.path = null
    }
    constructor(file: File) {
        uri = null
        context = null
        path = file
    }

    companion object {
        fun AndroidFile.asAmvFile() :AmvFile {
            return if(hasPath) {
                AmvFile(path!!)
            } else if(hasUri) {
                AmvFile(uri!!, context!!)
            } else {
                throw IllegalStateException("no source")
            }
        }
    }

    fun asAndroidFile():AndroidFile {
        return if(hasPath) {
            AndroidFile(path!!)
        } else if(hasUri) {
            AndroidFile(uri!!, context!!)
        } else {
            throw IllegalStateException("no source")
        }
    }

    val hasPath:Boolean get() = path!=null
    val hasUri:Boolean get() = uri!=null

    val toUri:Uri get() = if(hasUri) uri!! else Uri.fromFile(path)

    fun <T> withFileDescriptor(mode:String, fn:(FileDescriptor)->T):T {
        return if(hasUri) {
            context!!.contentResolver.openAssetFileDescriptor(uri!!, mode)!!.use {
                fn(it.fileDescriptor)
            }
        } else {
            ParcelFileDescriptor.open(path!!, ParcelFileDescriptor.parseMode(mode)).use {
                fn(it.fileDescriptor)
            }
        }
    }

    fun <T> fileDescriptorToRead(fn:(FileDescriptor)->T):T = withFileDescriptor("r", fn)

    fun <T> fileDescriptorToWrite(fn:(FileDescriptor)->T):T = withFileDescriptor("rw", fn)

    fun openParcelFileDescriptor(mode:String): ParcelFileDescriptor {
        return if(hasUri) {
            context!!.contentResolver.openFileDescriptor(uri!!, mode )!!
        } else { // Use RandomAccessFile so we can open the file with RW access;
            ParcelFileDescriptor.open(path!!, ParcelFileDescriptor.parseMode(mode))
        }
    }

    fun openParcelFileDescriptorToRead() = openParcelFileDescriptor("r")
    fun openParcelFileDescriptorToWrite() = openParcelFileDescriptor("rw")

    fun openInputStream() : InputStream {
        return try {
            if (hasUri) {
                context!!.contentResolver.openInputStream(uri!!)!!
            } else {
                path!!.inputStream()
            }
        } catch(e:Throwable) {
            AmvSettings.logger.stackTrace(e)
            throw e
        }
    }

    fun openOutputStream(append:Boolean) : OutputStream {
        return try {
            if(hasUri) {
                context!!.contentResolver.openOutputStream(uri!!, if(append) "wa" else "w")!!
            } else {
                FileOutputStream(path!!, append)
            }
        } catch(e:Throwable) {
            AmvSettings.logger.stackTrace(e)
            throw e
        }
    }

    inline fun <T> withInputStream(fn:(stream:InputStream)->T):T {
        return openInputStream().use {
            fn(it)
        }
    }

    inline fun <T> withOutputStream(append:Boolean=false, fn:(stream:OutputStream)->T):T {
        return openOutputStream(append).use {
            fn(it)
        }
    }

    override fun toString(): String {
        return path?.toString() ?: uri?.toString() ?: "*invalid-path*"
    }

    private fun delete() {
        if (hasPath) {
            path!!.delete()
        } else if (hasUri) {
            DocumentFile.fromSingleUri(context!!, uri!!)?.delete()
        }
    }
    fun safeDelete(silent:Boolean=true) {
        try {
            delete()
        } catch (e:Throwable) {
            if(!silent) {
                AmvSettings.logger.stackTrace(e)
            }
        }
    }
}