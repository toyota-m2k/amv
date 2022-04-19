package com.michael.video.v2.transcoder

import com.michael.video.v2.common.AmvSettings
import com.michael.video.v2.util.AmvClipping
import com.michael.video.v2.util.AmvFile
import io.github.toyota32k.media.lib.converter.ConvertResult
import io.github.toyota32k.media.lib.converter.Converter
import io.github.toyota32k.media.lib.converter.IAwaiter
import io.github.toyota32k.media.lib.converter.IProgress
import kotlinx.coroutines.flow.MutableStateFlow

class AmvAmpTranscoder(val srcFile: AmvFile, override var progress: MutableStateFlow<Int>?) : IAmvTranscoder {
    val logger = AmvCascadeTranscoder.logger
    override var remainingTime:Long = -1L
        private set
    private var awaiter: IAwaiter<ConvertResult>? = null

    private fun onProgress(progress: IProgress) {
        this.remainingTime = progress.remainingTime
        this.progress?.value = progress.percentage
    }

    private suspend fun await(): AmvResult {
        val result = awaiter?.await()
        return when {
            result == null -> AmvResult.failed("fatal error")
            result.succeeded -> AmvResult.succeeded
            result.cancelled -> AmvResult.cancelled
            result.exception != null -> AmvResult.failed(result.exception!!)
            result.errorMessage != null -> AmvResult.failed(result.errorMessage!!)
            else -> AmvResult.failed("unknown status")
        }
    }

    override suspend fun transcode(distFile: AmvFile, clipping: AmvClipping): AmvResult {
        logger.debug("$clipping")
        logger.debug("from: $srcFile")
        logger.debug("to  : $distFile")
        val converter = Converter.Factory()
            .input(srcFile.asAndroidFile())
            .output(distFile.asAndroidFile())
            .deleteOutputOnError(true)
            .setProgressHandler(this::onProgress)
            .trimmingStartFrom(clipping.start)
            .trimmingEndTo(clipping.end)
            .build()
        awaiter = converter.executeAsync()
        return await()
    }

    override fun cancel() {
        logger.debug()
        awaiter?.cancel()
    }

    override fun close() {
        logger.debug()
    }
}