package com.michael.video.v2.elements

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import com.michael.utils.UtAndroidUri
import com.michael.video.AmvFitter
import com.michael.video.AmvSettings
import com.michael.video.MuSize
import com.michael.video.v2.getBitmapAt
import com.michael.video.v2.getDate
import com.michael.video.v2.getLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AmvFrameExtractor(val fitter:AmvFitter, val scope: CoroutineScope) : Closeable {
    var analyzer: MediaMetadataRetriever? = null

//    fun open(file:File) {
//        try {
//            analyzer = MediaMetadataRetriever(). apply { setDataSource(file.path) }
//        } catch(e:Throwable) {
//            logger.stackTrace(e)
//        }
//    }

    suspend fun open(uri: Uri, context: Context):Boolean {
        if(analyzer!=null) return false
        return try {
            suspendCoroutine<Boolean> { cont->
                CoroutineScope(scope.coroutineContext+Dispatchers.IO).launch {
                    UtAndroidUri(uri).use {
                        analyzer = MediaMetadataRetriever().apply { setDataSource(it.open(context)) }
                    }
                    cont.resume(true)
                }
            }
        } catch(e:Throwable) {
            logger.stackTrace(e)
            false
        }
    }
//    constructor(uri: Uri, fitter:AmvFitter) : this(MediaMetadataRetriever().apply { setDataSource(uri.toString())}, fitter)


    companion object {
        val logger get() = AmvSettings.logger
    }

    private var cancelled = false
    fun cancel() {
        cancelled = true
    }

    data class BasicProperties(
        var creationDate: Date?,
        var duration:Long,
        val width:Int,
        val height:Int
    ) {
        val size:MuSize
            get() = MuSize(width.toFloat(), height.toFloat())
    }

    val properties: BasicProperties by lazy {
        val analyzer = this.analyzer ?: throw IllegalStateException("not opened")
        var height = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
        var width = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
        val rotate = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        if(rotate==90L || rotate==270L) {
            val v = height
            height = width
            width = v
        }
        BasicProperties(
            analyzer.getDate(MediaMetadataRetriever.METADATA_KEY_DATE),
            analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_DURATION),
            width,
            height
        )
    }

    fun thumbnailSize(): Size {
        val thumbnailSize = MuSize()
        fitter.fit(properties.size, thumbnailSize)
        return thumbnailSize.asSize
    }

    fun extractFrames(count:Int, autoClose:Boolean) : Flow<Bitmap> {
        if(count<=0) throw IllegalArgumentException("count must be grater than 1")
        val analyzer = this.analyzer ?: throw IllegalStateException("not opened")
        return flow<Bitmap> {
            val size = thumbnailSize()
            val step = properties.duration / count
            val pos = step / 2
            for(index in 0 until count) {
                if(cancelled) break
                emit(analyzer.getBitmapAt(pos+step*index, exactFrame = false, size.width, size.height))
            }
            if(autoClose) {
                close()
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun extractFrame(pos:Long) : Bitmap {
        val analyzer = this.analyzer ?: throw IllegalStateException("not opened")
        return withContext(Dispatchers.IO) {
            val size = thumbnailSize()
            analyzer.getBitmapAt(pos, exactFrame = false, size.width, size.height)
        }
    }

    override fun close() {
        try {
            analyzer?.close()
        } catch(e:Throwable) {
            logger.stackTrace(e)
        } finally {
            analyzer = null
        }
    }
}