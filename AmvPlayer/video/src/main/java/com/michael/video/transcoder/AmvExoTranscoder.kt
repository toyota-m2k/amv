package com.mihcael.video.transcoder

import android.content.Context
import android.os.Build
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.transformer.ProgressHolder
import com.google.android.exoplayer2.transformer.Transformer
import com.google.android.exoplayer2.util.MimeTypes
import com.michael.video.v2.common.AmvSettings
import com.michael.video.v2.util.AmvClipping
import com.michael.video.v2.util.AmvFile
import com.michael.video.transcoder.AmvResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AmvExoTranscoder(val srcFile: AmvFile, context: Context, override var progress: MutableStateFlow<Int>?) : IAmvTranscoder, Transformer.Listener {
    val logger = AmvSettings.logger
    override val remainingTime:Long = -1L
    private var cancelled: Boolean = false
    private var succeeded: Boolean = false

    private var mDstFile:AmvFile? = null

    val transformer:Transformer = Transformer.Builder()
        .setContext(context)
        .setOutputMimeType(MimeTypes.VIDEO_MP4)
        .setListener(this)
        .build()


    private suspend fun process(startTransform:()->Unit) : AmvResult {
        return suspendCoroutine<AmvResult> {
            continuation = it
            CoroutineScope(Dispatchers.IO).launch {
                startTransform()
                val progressHolder = ProgressHolder()
                do {
                    progress?.value = progressHolder.progress
                    delay(500)
                } while (transformer.getProgress(progressHolder) != Transformer.PROGRESS_STATE_NO_TRANSFORMATION)
            }
        }
    }

    var continuation:Continuation<AmvResult>? = null

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
        continuation?.resume(AmvResult.succeeded)
    }

    override fun onTransformationError(inputMediaItem: MediaItem, exception: Exception) {
        logger.stackTrace(exception)
        continuation?.resume(if(cancelled) AmvResult.cancelled else  AmvResult.failed(exception))
    }

}