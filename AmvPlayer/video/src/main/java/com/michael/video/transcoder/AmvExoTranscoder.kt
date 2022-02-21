package com.mihcael.video.transcoder

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.transformer.ProgressHolder
import com.google.android.exoplayer2.transformer.Transformer
import com.google.android.exoplayer2.util.MimeTypes
import com.michael.utils.FuncyListener2
import com.michael.video.AmvError
import com.michael.video.AmvSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class AmvExoTranscoder(val sourceFile:File, context: Context) : IAmvTranscoder, Transformer.Listener {
    val logger = AmvSettings.logger

    override val completionListener =  FuncyListener2<IAmvTranscoder, Boolean, Unit>()
    override val progressListener = FuncyListener2<IAmvTranscoder, Float, Unit>()
    override val error = AmvError()

    private var mDstFile:File? = null

    val transformer:Transformer = Transformer.Builder()
        .setContext(context)
        .setOutputMimeType(MimeTypes.VIDEO_MP4)
        .setListener(this)
        .build()


    private fun progressWatch() {
        CoroutineScope(Dispatchers.Main).launch {
            val progressHolder = ProgressHolder()
            do {
                progressListener.invoke(this@AmvExoTranscoder, progressHolder.progress.toFloat()/100)
                delay(500)
            } while(transformer.getProgress(progressHolder)!= Transformer.PROGRESS_STATE_NO_TRANSFORMATION)
        }
    }

    override fun transcode(distFile: File) {
        logger.debug("from: ${sourceFile.path}")
        logger.debug("to  : ${distFile.path}")
        mDstFile = distFile
        transformer.startTransformation(MediaItem.Builder().setUri(Uri.fromFile(sourceFile)).build(), distFile.path)
        progressWatch()
    }

    override fun truncate(distFile: File, start: Long, end: Long) {
        logger.debug("${start}-${end}")
        logger.debug("from: ${sourceFile.path}")
        logger.debug("to  : ${distFile.path}")
        mDstFile = distFile
        val clip = if(start>0L||end>0L) {
            val builder = MediaItem.ClippingConfiguration.Builder()
            if (start >= 0) {
                builder.setStartPositionMs(start)
            }
            if (end > 0) {
                builder.setEndPositionMs(end)
            }
            builder.build()
        } else MediaItem.ClippingConfiguration.UNSET
        transformer.startTransformation(MediaItem.Builder().setUri(Uri.fromFile(sourceFile)).setClippingConfiguration(clip).build(), distFile.path)
        progressWatch()
    }

    override fun cancel() {
        transformer.cancel()
    }

    override fun dispose() {
        if (error.hasError) {
            mDstFile?.apply {
                if (exists() && isFile) {
                    delete()
                }
                mDstFile = null
            }
        }
        completionListener.reset()
        progressListener.reset()
    }

    override fun onTransformationCompleted(inputMediaItem: MediaItem) {
        completionListener.invoke(this, true)
    }

    override fun onTransformationError(inputMediaItem: MediaItem, exception: Exception) {
        logger.stackTrace(exception)
        error.setError(exception)
        completionListener.invoke(this, false)
    }

}