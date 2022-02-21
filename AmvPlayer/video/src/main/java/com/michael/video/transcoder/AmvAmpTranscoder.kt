package com.mihcael.video.transcoder

import com.michael.utils.FuncyListener2
import com.michael.video.AmvError
import com.michael.video.AmvSettings
import io.github.toyota32k.media.lib.converter.ConvertResult
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IAwaiter
import io.github.toyota32k.media.lib.converter.IProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AmvAmpTranscoder(val srcFile:File) : IAmvTranscoder {
    val logger = AmvSettings.logger
    override val completionListener = FuncyListener2<IAmvTranscoder, Boolean, Unit>()
    override val progressListener = FuncyListener2<IAmvTranscoder, Float, Unit>()
    override val error = AmvError()

    private var awaiter: IAwaiter<ConvertResult>? = null

    private fun onProgress(progress: IProgress) {
        progressListener.invoke(this, progress.percentage.toFloat()/100)
    }

    private fun await() {
        CoroutineScope(Dispatchers.Main).launch {
            val result = awaiter?.await()
            if(result?.succeeded==true) {
                completionListener.invoke(this@AmvAmpTranscoder, true)
            } else {
                when {
                    result == null -> error.setError("fatal error")
                    result.cancelled -> error.reset()
                    result.exception != null -> error.setError(result.exception!!)
                    result.errorMessage != null -> error.setError(result.errorMessage!!)
                }
                completionListener.invoke(this@AmvAmpTranscoder, false)
            }
        }
    }

    override fun transcode(distFile: File) {
        logger.debug("from: ${srcFile.path}")
        logger.debug("to  : ${distFile.path}")
        val converter = Converter.Factory()
            .input(srcFile)
            .output(distFile)
            .deleteOutputOnError(true)
            .setProgressHandler(this::onProgress)
            .build()
        awaiter = converter.executeAsync()
        await()
    }

    override fun truncate(distFile: File, start: Long, end: Long) {
        logger.debug("${start}-${end}")
        logger.debug("from: ${srcFile.path}")
        logger.debug("to  : ${distFile.path}")
        val converter = Converter.Factory()
            .input(srcFile)
            .output(distFile)
            .deleteOutputOnError(true)
            .setProgressHandler(this::onProgress)
            .trimmingStartFrom(start)
            .trimmingEndTo(end)
            .build()
        awaiter = converter.executeAsync()
        await()
    }

    override fun cancel() {
        logger.debug()
        awaiter?.cancel()
    }

    override fun dispose() {
        logger.debug()
        completionListener.reset()
        progressListener.reset()
    }
}