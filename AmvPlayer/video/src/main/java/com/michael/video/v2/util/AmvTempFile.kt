package com.michael.video.v2.util

import android.content.Context
import android.net.Uri
import com.michael.video.v2.common.AmvSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AmvTempFile(val sourceFile:AmvFile?=null) : IAmvTempFile {
    constructor(uri: Uri, context: Context) : this(AmvFile(uri, context))
    constructor(path: File) : this(AmvFile(path))
    constructor(path: String) : this(AmvFile(File(path)))

    var internalFile:File? = null
    var error:Throwable? = null

    private fun createTempFile():File {
        return File.createTempFile("a_", ".tmp", AmvSettings.workDirectory).also { outFile ->
            try {
                val src = sourceFile
                if(src!=null) {
                    src.openInputStream().use { inputStream ->
                        FileOutputStream(outFile, false).use { outStream ->
                            inputStream.copyTo(outStream)
                            outStream.flush()
                        }
                    }
                }
            } catch (e: Throwable) {
                outFile.safeDeleteFile()
                throw e
            }
        }
    }

    /**
     * temp file を作成する。
     */
    override suspend fun getTempFile():File {
        return internalFile ?: withContext(Dispatchers.IO) {
                createTempFile().apply {
                    internalFile = this
                }
            }
    }

    override fun detach():File? {
        val r = internalFile
        internalFile = null
        return r
    }

    override fun close() {
        internalFile.safeDeleteFile()
        internalFile = null
    }
}