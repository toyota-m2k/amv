package com.michael.video.v2.util

import com.michael.video.v2.common.AmvSettings
import java.io.Closeable
import java.io.File

interface IAmvTempFile : Closeable {
    suspend fun getTempFile(): File

    suspend fun getTempFileOrNull():File? {
        return try {
            getTempFile()
        } catch(e:Throwable) {
            AmvSettings.logger.stackTrace(e)
            null
        }
    }

    fun detach():File?

    override fun close()
}

suspend inline fun <T> IAmvTempFile.withTempFile(fn: (tempFile: File) -> T): T {
    use {
        return fn(getTempFile())
    }
}

fun File?.safeDeleteFile(silent:Boolean=true) {
    if(this==null) return
    try {
        this.delete()
    } catch(e:Throwable) {
        if(!silent) {
            AmvSettings.logger.stackTrace(e)
        }
    }
}
