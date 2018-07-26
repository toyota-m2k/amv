package com.michael.video

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.michael.utils.FuncyListener2
import com.michael.utils.UtLogger
import org.m4m.IProgressListener
import org.m4m.MediaComposer
import org.m4m.android.AndroidMediaObjectFactory
import org.m4m.domain.Pair
import java.io.File
import java.lang.Exception
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import kotlin.math.roundToInt


class AmvTranscoder(val source:File, val surfaceView:AmvWorkingSurfaceView, context:Context) : SurfaceHolder.Callback{

    val mediaInfo = AmvMediaInfo(source, context)
    val error = AmvError()

    init {
        surfaceView.holder.addCallback(this)
        val size = mediaInfo.hd720Size
        surfaceView.setVideoSize(size.width, size.height)
    }

    private fun terminate() {
        val composer = mMediaComposer
        if(null!=composer) {
            mMediaComposer = null
            composer.stop()
        }
    }

    private val mListener = object : IProgressListener {
        override fun onMediaStart() {
            UtLogger.debug("AmvTranscoder: started")
        }

        override fun onMediaProgress(progress: Float) {
            UtLogger.debug("AmvTranscoder: progressing ${(progress*100).roundToInt()}")
            progressListener.invoke(this@AmvTranscoder, progress)
        }

        override fun onMediaDone() {
            UtLogger.debug("AmvTranscoder: done")
            completionListener.invoke(this@AmvTranscoder, true)
            terminate()
        }

        override fun onMediaPause() {
            UtLogger.debug("AmvTranscoder: paused")
        }

        override fun onMediaStop() {
            UtLogger.debug("AmvTranscoder: stopped")
//            completionListener.invoke(this@AmvTranscoder, false)
            terminate()
        }

        override fun onError(exception: Exception?) {
            if(exception!=null) {
                error.setError(exception)
            } else {
                error.setError("transcode/trimming error.")
            }
            UtLogger.debug("AmvTranscoder: error\n${error.toString()}")
            completionListener.invoke(this@AmvTranscoder, false)
            terminate()
        }

    }
    private val mContext = WeakReference<Context>(context)
//    private var mSurfaceView: AmvWorkingSurfaceView? = null
    private var mMediaComposer: MediaComposer? = null

    init {
        if (!mediaInfo.hasVideo) {
            throw AmvException("source format error.")
        }
        UtLogger.debug(mediaInfo.summary)
    }

    class CompletionListener : FuncyListener2<AmvTranscoder, Boolean, Unit>() {
        interface IHandler {
            fun onCompleted(sender: AmvTranscoder, result: Boolean)
        }
        fun set(listener: IHandler) = this.set(listener::onCompleted)
    }

    class ProgressListener : FuncyListener2<AmvTranscoder, Float, Unit>(){
        interface IHandler {
            fun onProgress(sender: AmvTranscoder, progress: Float)
        }
        fun set(listener: IHandler) = this.set(listener::onProgress)
    }

    val completionListener = CompletionListener()
    val progressListener = ProgressListener()

    private fun prepare(distFile:File, fn:MediaComposer.()->Unit) {
        if(!mediaInfo.hasVideo) {
            throw AmvException("No Video")
        }
        val context = mContext.get()
        if(context==null) {
            throw AmvException("Context has gone")
        }
        val composer = MediaComposer(AndroidMediaObjectFactory(context), mListener)
        mMediaComposer = composer
        mediaInfo.applyHD720TranscodeParameters(mMediaComposer!!, distFile)

//        mSurfaceView = AmvWorkingSurfaceView(context)
//        mediaInfo.mediaFileInfo.setOutputSurface(AndroidMediaObjectFactory.Converter.convert(mSurfaceView!!.holder.surface))
        composer.fn()
    }

    /**
     * トランスコード
     */
    fun transcode(distFile:File) {
        prepare(distFile) {
            start()
        }
    }

    /**
     * トリミング
     */
    fun truncate(distFile:File, start:Long, end:Long) {
        prepare(distFile) {
            sourceFiles[0].addSegment(Pair(start,end))
            start()
        }
    }

    fun cancel() {
        mMediaComposer?.stop()
    }

    fun pause() {
        mMediaComposer?.pause()
    }
    fun resume() {
        mMediaComposer?.resume()
    }

    private fun displayVideoFrame(holder: SurfaceHolder) {

        if (mediaInfo.hasVideo) {
            try {
                val surface = AndroidMediaObjectFactory.Converter.convert(holder.surface)
                mediaInfo.mediaFileInfo.setOutputSurface(surface)

                val buffer = ByteBuffer.allocate(1)
                mediaInfo.mediaFileInfo.getFrameAtPosition(100, buffer)

            } catch (e: Exception) {
                UtLogger.error(e.toString())
            }

        }
    }

    // region SurfaceHolder.Callback i/f
    override fun surfaceCreated(holder: SurfaceHolder) {
        displayVideoFrame(holder)
        UtLogger.debug("AmvTranscoder: surfaceCreated")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        UtLogger.debug("AmvTranscoder: surfaceDestroyed")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        UtLogger.debug("AmvTranscoder: surfaceChanged")
    }

}