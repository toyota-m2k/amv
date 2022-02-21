package com.michael.video

import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * URLを作業フォルダにDLし、不要になったら削除するための作業ファイルクラス
 */
class AmvDLTempFile(val uri:String, val onPrepared:(AmvDLTempFile, File?)->Unit) {
    /**
     * DLに失敗した場合に、エラー情報を取得
     */
    val error = AmvError()

    fun dispose() {
        synchronized(this) {
            mDisposed = true
            mFile?.delete()
            mFile = null
        }
    }

    private var mDownloading:Boolean = false
    private var mFile: File? = null
    private var mDisposed = false
//    private var mHandler = Handler()

    init {
        download()
    }

    data class Status(val busy:Boolean, val file:File?)

    val status:Status
        get() = Status(mDownloading, mFile)

    private companion object {
        val logger = AmvSettings.logger
        private const val READ_TIMEOUT = 60L
        private const val WRITE_TIMEOUT = 60L
        private const val CONNECTION_TIMEOUT = 30L
        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                    .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
                    .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .build()
        }
    }

    private fun download() {
        if(mDownloading) {
            throw AmvException("internal error: download twice")
        }
        mDownloading = true
        mFile = null

        try {
            val request = Request.Builder()
                    .url(uri)
                    .get()
                    .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    var file: File? = null
                    try {
                        error.reset()

                        file = response.use {
                            it.body?.use { body->
                                body.byteStream().use { inStream->
                                    if (mDisposed) {
                                        null
                                    } else {
                                        File.createTempFile("a_dl_", ".tmp", AmvSettings.workDirectory).apply {
                                            FileOutputStream(this, false).use { outStream ->
                                                inStream.copyTo(outStream, 128 * 1024) // buffer size 128KB
                                            }
                                            logger.debug("${this.name}: file created")
                                        }
                                    }
                                }
                            }
                        }
                        if(null==file) {
                            onFailure(null)
                            return
                        }
                    } catch (e: Throwable) {
                        onFailure(e)
                        if (null != file && file.exists()) {
                            file.delete()
                        }
                        return
                    }

                    mDownloading = false
                    synchronized(this@AmvDLTempFile) {
                        mFile = file
                        if (mDisposed) {
                            dispose()
                            return
                        }
                    }
                    onPrepared(this@AmvDLTempFile, file)
                }

                override fun onFailure(call: Call, e: IOException) {
                    this@AmvDLTempFile.onFailure(e)
                }
            })
        } catch(e:Throwable) {
            onFailure(e)
        }

    }

    private fun onFailure(e:Throwable?) {
        if(e!=null) {
            logger.stackTrace(e)
            error.setError(e)
        } else {
            logger.error("something wrong.")
            error.setError("generic error")
        }

        mDownloading = false
        if( synchronized(this) {
             mFile = null
             !mDisposed
             }) {
            onPrepared(this@AmvDLTempFile, null)
        }
    }

}