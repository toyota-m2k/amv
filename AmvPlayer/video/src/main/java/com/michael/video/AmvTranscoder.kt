/**
 * 動画のトランスコード/トリミングエンジン
 * （m4m ライブラリを利用）
 *
 * @author M.TOYOTA 2018.07.26 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
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

private const val FRAME_REFRESH_PERCENT = 5     // 5%毎にフレーム表示を更新

/**
 * トランスコード / トリミングを行うクラス
 *
 */

class AmvTranscoder(val source:File, context:Context) : SurfaceHolder.Callback{

    // region Public Properties

    /**
     * メディアファイルの情報
     */
    val mediaInfo = AmvMediaInfo(source, context)

    /**
     * エラーが発生した場合に、その情報がセットされる
     */
    val error = AmvError()

    /**
     * Transcode/Trimming 時に、外部からSurfaceViewを与えておくと、処理中に（時々。。。とりあえず５％毎に）フレームが描画される。
     * その分、処理が遅くなるけど。
     */
    var surfaceView: SurfaceView? = null
        set(v) {
            field = v
            if(null!=v) {
                mediaInfo.mediaFileInfo.setOutputSurface(AndroidMediaObjectFactory.Converter.convert(v.holder.surface))
                v.holder.addCallback(this)
            } else {
                mediaInfo.mediaFileInfo.setOutputSurface(null)
            }
        }
    // endregion -----------------------------------------------------------------------------

    // region Event Listeners (for Kotlin)

    /**
     * 処理が終了したときのイベント
     */
    val completionListener = FuncyListener2<AmvTranscoder, Boolean, Unit>()
    /**
     * プログレス通知
     */
    val progressListener = FuncyListener2<AmvTranscoder, Float, Unit>()

    // endregion -----------------------------------------------------------------------------

    // region Event Listeners for Java API

    interface ICompletionEventHandler {
        fun onCompleted(sender: AmvTranscoder, result: Boolean)
    }

    @Suppress("unused")
    fun setCompletionListener(listener:ICompletionEventHandler) {
        completionListener.set(listener::onCompleted)
    }

    interface IProgressEventHandler {
       fun onProgress(sender: AmvTranscoder, progress: Float)
    }

    @Suppress("unused")
    fun setProgressListener(listener:IProgressEventHandler) {
        progressListener.set(listener::onProgress)
    }

    // endregion -----------------------------------------------------------------------------

    // region Public Methods

    /**
     * トランスコードを開始
     * @param  distFile     出力ファイル
     */
    fun transcode(distFile:File) {
        mTrimmingRange = null
        prepare(distFile) {
            start()
        }
    }

    /**
     * トリミングを開始
     * @param  distFile     出力ファイル
     */
    fun truncate(distFile:File, start:Long, end:Long) {
        prepare(distFile) {
            val range = TrimmingRange(start*1000,end*1000)
            mTrimmingRange = range
            sourceFiles[0].addSegment(range.pair)
            start()
        }
    }

    /**
     * 処理を中止
     */
    @Suppress("unused")
    fun cancel() {
        error.setError("cancelled")
        mMediaComposer?.stop()
    }

    /**
     * 処理を一時停止
     */
    fun pause() {
        mMediaComposer?.pause()
    }

    /**
     * 一時停止した処理を再開
     */
    @Suppress("unused")
    fun resume() {
        mMediaComposer?.resume()
    }

    /**
     * 後処理
     */
    fun dispose() {
        val composer = mMediaComposer
        if(null!=composer) {
            mediaInfo.mediaFileInfo.setOutputSurface(null)
            mMediaComposer = null
            composer.stop()
        }
    }


    /**
     * surfaceView（外部から与えられたサーフェス）にフレームを表示する
     * 呼び出し側から、SurfaceView初期化前に surfaceView プロパティにセットしておくと、自動的に先頭のフレームを表示するが、
     * それより後からセットするような場合には、このメソッドを呼ぶ必要があるかもしれない。
     *
     * @param position  表示するフレーム位置（全体を 1 としたときの比率で指定）
     */
    fun displayVideoFrame(position:Float) {

        if (surfaceView != null && mediaInfo.hasVideo && position in 0f..1f) {
            try {
                //val surface = AndroidMediaObjectFactory.Converter.convert(holder.surface)
//                mediaInfo.mediaFileInfo.setOutputSurface(surface)
                val tm = if(mTrimmingRange==null) {
                    (mediaInfo.duration*position).toLong()
                } else {
                    mTrimmingRange!!.timeAtPosition(position)
                }

                val buffer = ByteBuffer.allocate(1)
                mediaInfo.mediaFileInfo.getFrameAtPosition(tm, buffer)

            } catch (e: Exception) {
                UtLogger.error(e.toString())
            }

        }
    }

    // endregion -----------------------------------------------------------------------------

    // region Privates

    init {
        if (!mediaInfo.hasVideo) {
            throw AmvException("source format error.")
        }
        UtLogger.debug(mediaInfo.summary)
    }

    private val mContext = WeakReference<Context>(context)              // MediaComposerやInternalSurfaceViewを生成するために必要（Weakで持っておこう）
    private var mMediaComposer: MediaComposer? = null                   // コンポーザー
//    private var mInternalSurfaceView: AmvWorkingSurfaceView? = null     // surfaceViewが与えられなかったときに、自力で代用品を用意できるようにしておく。
    private var mTrimmingRange :TrimmingRange? = null                   // トリミング用の範囲を保持する（フレーム描画のため）
    private var mDstFile: File? = null

    /**
     * トリミング範囲を保持するデータクラス
     */
    private data class TrimmingRange(val start:Long, val end:Long) {
        val range : Long
            get() = end-start
        val pair : Pair<Long,Long>
            get() = Pair(start,end)
        fun timeAtPosition(p:Float) : Long {
            return start + (range * p).toLong()
        }
    }

    /**
     * Transcode/Trimmingの進捗を受け取るコールバックi/fインスタンス
     */
    private val mListener = object : IProgressListener {
        var lenderFrame = FRAME_REFRESH_PERCENT

        override fun onMediaStart() {
            UtLogger.debug("AmvTranscoder: started")
            lenderFrame = FRAME_REFRESH_PERCENT
        }

        override fun onMediaProgress(progress: Float) {
            UtLogger.debug("AmvTranscoder: progressing ${(progress*100).roundToInt()}")
            progressListener.invoke(this@AmvTranscoder, progress)

            if(lenderFrame<(progress*100).toInt()) {
                lenderFrame += FRAME_REFRESH_PERCENT        // 5%ずつ表示
                displayVideoFrame(progress)
            }
        }

        override fun onMediaDone() {
            UtLogger.debug("AmvTranscoder: done")
            completionListener.invoke(this@AmvTranscoder, true)
            mDstFile = null
            dispose()
        }

        override fun onMediaPause() {
            UtLogger.debug("AmvTranscoder: paused")
        }

        override fun onMediaStop() {
            UtLogger.debug("AmvTranscoder: stopped")
            if(error.message=="cancelled") {
                completionListener.invoke(this@AmvTranscoder, false)
            }
            dispose()
        }

        override fun onError(exception: Exception?) {
            if(exception!=null) {
                error.setError(exception)
            } else {
                error.setError("transcode/trimming error.")
            }
            UtLogger.debug("AmvTranscoder: error\n${error.message}")
            completionListener.invoke(this@AmvTranscoder, false)
            disposeHalfBakedFile()
            dispose()
        }
    }

    private fun disposeHalfBakedFile() {
        mDstFile?.apply {
            if(exists() && isFile) {
                delete()
            }
        }
        mDstFile = null
    }

    /**
     * Transcode/Truncate共通のコンポーザー初期化処理
     */
    private fun prepare(distFile:File, fn:MediaComposer.()->Unit) {
        if(!mediaInfo.hasVideo) {
            throw AmvException("No Video")
        }
        val context = mContext.get() ?: throw AmvException("Context has gone")
        val composer = MediaComposer(AndroidMediaObjectFactory(context), mListener)
        mMediaComposer = composer
        mediaInfo.applyHD720TranscodeParameters(composer, distFile)

//        if(null==surfaceView) {
//            mInternalSurfaceView = AmvWorkingSurfaceView(context)
//            mInternalSurfaceView!!.setVideoSize(mediaInfo.hd720Size.width, mediaInfo.hd720Size.height)
//            mediaInfo.mediaFileInfo.setOutputSurface(AndroidMediaObjectFactory.Converter.convert(mInternalSurfaceView!!.holder.surface))
//        }
        mDstFile = distFile
        composer.fn()
    }

    // endregion -----------------------------------------------------------------------------

    // region SurfaceHolder.Callback i/f

    override fun surfaceCreated(holder: SurfaceHolder) {
        displayVideoFrame(0.01f)
        UtLogger.debug("AmvTranscoder: surfaceCreated")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        UtLogger.debug("AmvTranscoder: surfaceDestroyed")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        UtLogger.debug("AmvTranscoder: surfaceChanged")
    }

    // endregion -----------------------------------------------------------------------------
}