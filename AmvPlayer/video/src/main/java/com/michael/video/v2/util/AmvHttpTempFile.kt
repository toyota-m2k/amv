package com.michael.video.v2.util

import android.net.Uri
import com.michael.video.v2.common.AmvSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AmvHttpTempFile(val uri:String) : IAmvTempFile {
    constructor(uri:Uri):this(uri.toString())

    enum class Status {
        NONE,
        DOWNLOADING,
        DONE,
        ERROR,
    }
    var status = MutableStateFlow(Status.NONE)
    var internalFile:File? = null
    var call:Call? = null
    var error:Throwable? = null

    private fun download() {
        if(status.value!=Status.NONE) {
            return
        }
        status.value = Status.DOWNLOADING
        internalFile = null
        error = null
        var outFile:File? = null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                outFile = File.createTempFile("a_dl", ".tmp", AmvSettings.workDirectory)
                val request = Request.Builder()
                    .url(uri)
                    .get()
                    .build()
                call = AmvSettings.httpClient.newCall(request)
                call!!.executeAsync().use { response ->
                    response.body!!.use { body ->
                        body.byteStream().use { inStream ->
                            outFile!!.outputStream().use { outStream ->
                                inStream.copyTo(outStream)
                                outStream.flush()
                                internalFile = outFile
                                status.value = Status.DONE
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                error = e
                internalFile = null
                outFile.safeDeleteFile()
                status.value = Status.ERROR
            } finally {
                call = null
                if(status.value==Status.NONE) {
                    AmvSettings.logger.error()
                    status.value = Status.ERROR
                }
            }
        }
    }

    suspend fun Call.executeAsync() : Response {
        return suspendCoroutine {cont ->
            try {
                enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        cont.resume(response)
                    }
                })
            } catch(e:Throwable) {
                cont.resumeWithException(e)
            }
        }
    }

    override suspend fun getTempFile(): File {
        download()
        if(status.filter { it!=Status.NONE }.first()==Status.DONE) {
            return internalFile!!
        } else {
            throw error ?: IllegalStateException("something is wrong.")
        }
    }

    override fun detach(): File? {
        val r = internalFile
        internalFile = null
        return r
    }

    override fun close() {
        call?.cancel()
        call = null
        internalFile?.safeDeleteFile()
        internalFile = null
    }
}