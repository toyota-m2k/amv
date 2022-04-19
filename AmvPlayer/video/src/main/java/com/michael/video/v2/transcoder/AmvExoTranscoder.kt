package com.michael.video.v2.transcoder

import android.content.Context
import android.os.Build
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.transformer.ProgressHolder
import com.google.android.exoplayer2.transformer.TransformationException
import com.google.android.exoplayer2.transformer.Transformer
import com.google.android.exoplayer2.util.MimeTypes
import com.michael.video.v2.common.AmvSettings
import com.michael.video.v2.util.AmvClipping
import com.michael.video.v2.util.AmvFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AmvExoTranscoder(val srcFile: AmvFile, context: Context, override var progress: MutableStateFlow<Int>?) : IAmvTranscoder, Transformer.Listener {
    val logger = AmvCascadeTranscoder.logger
    override val remainingTime:Long = -1L
    private var cancelled: Boolean = false
    private var succeeded: Boolean = false

    private var mDstFile:AmvFile? = null
    private val result : MutableStateFlow<AmvResult?> = MutableStateFlow(null)

    private val transformer:Transformer = Transformer.Builder(context)
        .addListener(this)
        .build()


    private suspend fun process(startTransform:()->Unit) : AmvResult {
        logger.debug()
        // ExoPlayer は UIスレッドで操作しないといけない。
        return withContext(Dispatchers.Main) {
            startTransform()
            launch {
                val progressHolder = ProgressHolder()
                do {
                    progress?.value = progressHolder.progress
                    delay(500)
                } while (transformer.getProgress(progressHolder) != Transformer.PROGRESS_STATE_NO_TRANSFORMATION)
                logger.debug("watching progress finished.")
            }
            result.filterNotNull().first()
        }
    }

//    var continuation:Continuation<AmvResult>? = null

    override suspend fun transcode(distFile: AmvFile, clipping: AmvClipping) : AmvResult {
        logger.debug("$clipping")
        logger.debug("from: $srcFile")
        logger.debug("to  : $distFile")
        mDstFile = distFile
        val clip = if(clipping.isValid) {
            val builder = MediaItem.ClippingConfiguration.Builder()
            if (clipping.isValidStart) {
                builder.setStartPositionMs(clipping.start)
            }
            if (clipping.isValidEnd) {
                builder.setEndPositionMs(clipping.end)
            }
            builder.build()
        } else MediaItem.ClippingConfiguration.UNSET
        return try {
            process {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    transformer.startTransformation(MediaItem.Builder().setUri(srcFile.toUri).setClippingConfiguration(clip).build(), distFile.openParcelFileDescriptorToWrite())
                } else {
                    if(!distFile.hasPath) throw IllegalArgumentException("file path is required on Android 6/7")
                    transformer.startTransformation(MediaItem.Builder().setUri(srcFile.toUri).setClippingConfiguration(clip).build(), distFile.path!!.path)
                }
            }
        } finally {
            logger.debug("process completed.")
            close()
        }
    }

    override fun cancel() {
        cancelled = true
        transformer.cancel()
    }

    override fun close() {
        if (!succeeded) {
            mDstFile?.safeDelete()
            mDstFile = null
        }
    }

    override fun onTransformationCompleted(inputMediaItem: MediaItem) {
        succeeded = true
        result.value = AmvResult.succeeded
    }

    override fun onTransformationError(inputMediaItem: MediaItem, exception: TransformationException) {
        logger.stackTrace(exception)
        result.value = if(cancelled) AmvResult.cancelled else  AmvResult.failed(exception)
    }

}