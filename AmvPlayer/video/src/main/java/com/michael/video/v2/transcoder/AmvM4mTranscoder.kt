/**
 * 動画のトランスコード/トリミングエンジン
 * （m4m ライブラリを利用）
 *
 * @author M.TOYOTA 2018.07.26 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video.v2.transcoder
import android.content.Context
import com.michael.video.v2.util.AmvClipping
import com.michael.video.v2.util.AmvFile
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * トランスコード / トリミングを行うクラス
 *
 */

class AmvM4mTranscoder(val source: AmvFile, context:Context, override var progress: MutableStateFlow<Int>?)
    : IAmvTranscoder
//    , SurfaceHolder.Callback
{
    override val remainingTime: Long
        get() = TODO("Not yet implemented")

    override suspend fun transcode(distFile: AmvFile, clipping: AmvClipping): AmvResult {
        TODO("Not yet implemented")
    }
//    companion object {
//        val logger = AmvSettings.logger
//        private const val FRAME_REFRESH_PERCENT = 5     // 5%毎にフレーム表示を更新
//    }
//    // region Public Properties
//
//    /**
//     * メディアファイルの情報
//     */
//    private val mediaInfo = AmvMediaInfo(source, context)
//
//    /**
//     * エラーが発生した場合に、その情報がセットされる
//     */
//    override val error = AmvError()
//
//    @Suppress("unused")
//    val resultFile
//        get() = mDstFile
//
//    /**
//     * Transcode/Trimming 時に、外部からSurfaceViewを与えておくと、処理中に（時々。。。とりあえず５％毎に）フレームが描画される。
//     * その分、処理が遅くなるけど。
//     */
//    private var surfaceView: SurfaceView? = null
//        set(v) {
//            field = v
//            if(null!=v) {
//                mediaInfo.mediaFileInfo.setOutputSurface(AndroidMediaObjectFactory.Converter.convert(v.holder.surface))
//                v.holder.addCallback(this)
//            } else {
//                mediaInfo.mediaFileInfo.setOutputSurface(null)
//            }
//        }
//    // endregion -----------------------------------------------------------------------------
//
//    // region Event Listeners (for Kotlin)
//
//    /**
//     * 処理が終了したときのイベント
//     */
//    override val completionListener = FuncyListener2<IAmvTranscoder, Boolean, Unit>()
//    /**
//     * プログレス通知
//     */
//    override val progressListener = FuncyListener2<IAmvTranscoder, Float, Unit>()
//
//    // endregion -----------------------------------------------------------------------------
//
//    // region Event Listeners for Java API
//
////    interface ICompletionEventHandler {
////        fun onCompleted(sender: AmvTranscoder, result: Boolean)
////    }
////
////    @Suppress("unused")
////    fun setCompletionListener(listener:ICompletionEventHandler) {
////        completionListener.set(listener::onCompleted)
////    }
////
////    interface IProgressEventHandler {
////       fun onProgress(sender: AmvTranscoder, progress: Float)
////    }
////
////    @Suppress("unused")
////    fun setProgressListener(listener:IProgressEventHandler) {
////        progressListener.set(listener::onProgress)
////    }
//
//    // endregion -----------------------------------------------------------------------------
//
//    // region Public Methods
//
//    /**
//     * トランスコードを開始
//     * @param  distFile     出力ファイル
//     */
//    override fun transcode(distFile:File) {
//        error.reset()
//        mTrimmingRange = null
//        prepare(distFile) {
//            start()
//        }
//    }
//
//    /**
//     * トリミングを開始
//     * @param  distFile     出力ファイル
//     */
//    override fun truncate(distFile:File, start:Long, end:Long) {
//        error.reset()
//        prepare(distFile) {
//            val range = TrimmingRange(start*1000,end*1000)
//            mTrimmingRange = range
//            sourceFiles[0].addSegment(range.pair)
//            start()
//        }
//    }
//
//    /**
//     * 処理を中止
//     */
//    @Suppress("unused")
//    override fun cancel() {
//        error.setError("cancelled")
//        mMediaComposer?.stop()
//    }
//
//    /**
//     * 処理を一時停止
//     */
//    fun pause() {
//        mMediaComposer?.pause()
//    }
//
//    /**
//     * 一時停止した処理を再開
//     */
//    @Suppress("unused")
//    fun resume() {
//        mMediaComposer?.resume()
//    }
//
//    /**
//     * 後処理
//     */
//    override fun dispose() {
//        val composer = mMediaComposer
//        if(null!=composer) {
//            mediaInfo.mediaFileInfo.setOutputSurface(null)
//            mMediaComposer = null
//            composer.stop()
//        }
//        disposeHalfBakedFile()
//        completionListener.reset()
//        progressListener.reset()
//    }
//
//    /**
//     * surfaceView（外部から与えられたサーフェス）にフレームを表示する
//     * 呼び出し側から、SurfaceView初期化前に surfaceView プロパティにセットしておくと、自動的に先頭のフレームを表示するが、
//     * それより後からセットするような場合には、このメソッドを呼ぶ必要があるかもしれない。
//     *
//     * @param position  表示するフレーム位置（全体を 1 としたときの比率で指定）
//     */
//    fun displayVideoFrame(position:Float) {
//
//        if (surfaceView != null && mediaInfo.hasVideo && position in 0f..1f) {
//            try {
//                //val surface = AndroidMediaObjectFactory.Converter.convert(holder.surface)
////                mediaInfo.mediaFileInfo.setOutputSurface(surface)
//                val tm = if(mTrimmingRange==null) {
//                    (mediaInfo.duration*position).toLong()
//                } else {
//                    mTrimmingRange!!.timeAtPosition(position)
//                }
//
//                val buffer = ByteBuffer.allocate(1)
//                mediaInfo.mediaFileInfo.getFrameAtPosition(tm, buffer)
//
//            } catch (e: Exception) {
//                logger.stackTrace(e)
//            }
//
//        }
//    }
//
//    // endregion -----------------------------------------------------------------------------
//
//    // region Privates
//
//    init {
////        if (!mediaInfo.hasVideo) {
////            throw AmvException("source format error (no video data).")
////        }
//        logger.debug(mediaInfo.summary)
//    }
//
//    private val mContext = WeakReference(context)              // MediaComposerやInternalSurfaceViewを生成するために必要（Weakで持っておこう）
//    private var mMediaComposer: MediaComposer? = null                   // コンポーザー
////    private var mInternalSurfaceView: AmvWorkingSurfaceView? = null     // surfaceViewが与えられなかったときに、自力で代用品を用意できるようにしておく。
//    private var mTrimmingRange : TrimmingRange? = null                   // トリミング用の範囲を保持する（フレーム描画のため）
//    private var mDstFile: File? = null
//
//    /**
//     * トリミング範囲を保持するデータクラス
//     */
//    private data class TrimmingRange(val start:Long, val end:Long) {
//        val range : Long
//            get() = end-start
//        val pair : Pair<Long,Long>
//            get() = Pair(start,end)
//        fun timeAtPosition(p:Float) : Long {
//            return start + (range * p).toLong()
//        }
//    }
//
//    /**
//     * Transcode/Trimmingの進捗を受け取るコールバックi/fインスタンス
//     */
//    private val mListener = object : IProgressListener {
//        var lenderFrame = FRAME_REFRESH_PERCENT
//
//        override fun onMediaStart() {
//            logger.debug("m4m-transcoder: started.")
//            lenderFrame = FRAME_REFRESH_PERCENT
//        }
//
//        override fun onMediaProgress(progress: Float) {
//            // UtLogger.debug("AmvTranscoder: progressing ${(progress*100).roundToInt()}")
//            progressListener.invoke(this@AmvM4mTranscoder, progress)
//
//            if(lenderFrame<(progress*100).toInt()) {
//                lenderFrame += FRAME_REFRESH_PERCENT        // 5%ずつ表示
//                displayVideoFrame(progress)
//            }
//        }
//
//        override fun onMediaDone() {
//            logger.debug("m4m-transcoder: done.")
//            completionListener.invoke(this@AmvM4mTranscoder, !error.hasError)
//            dispose()
//        }
//
//        override fun onMediaPause() {
//            logger.debug("m4m-transcoder: paused.")
//        }
//
//        override fun onMediaStop() {
//            logger.debug("m4m-transcoder: stopped.")
//            dispose()
//        }
//
//        override fun onError(exception: Exception?) {
//            if(exception!=null) {
//                error.setError(exception)
//                logger.stackTrace(exception, "m4m-transcoder: error\n")
//            } else {
//                error.setError("transcode/trimming error.")
//                logger.error("m4m-transcoder: error\n${error.message}")
//            }
//            completionListener.invoke(this@AmvM4mTranscoder, false)
//            dispose()
//        }
//    }
//
//    private fun disposeHalfBakedFile() {
//        if (error.hasError) {
//            mDstFile?.apply {
//                if (exists() && isFile) {
//                    delete()
//                }
//                mDstFile = null
//            }
//        }
//    }
//
//    /**
//     * Transcode/Truncate共通のコンポーザー初期化処理
//     */
//    private fun prepare(distFile:File, fn:MediaComposer.()->Unit) {
//        try {
//            if (!mediaInfo.hasVideo) {
//                error.setError("no video")
//                completionListener.invoke(this@AmvM4mTranscoder, false)
//                return
//            }
//            val context = mContext.get() ?: throw AmvException("Context has gone")
//            val composer = MediaComposer(AndroidMediaObjectFactory(context), mListener)
//            mMediaComposer = composer
//            mediaInfo.applyHD720TranscodeParameters(composer, distFile)
//
////        if(null==surfaceView) {
////            mInternalSurfaceView = AmvWorkingSurfaceView(context)
////            mInternalSurfaceView!!.setVideoSize(mediaInfo.hd720Size.width, mediaInfo.hd720Size.height)
////            mediaInfo.mediaFileInfo.setOutputSurface(AndroidMediaObjectFactory.Converter.convert(mInternalSurfaceView!!.holder.surface))
////        }
//            mDstFile = distFile
//            composer.fn()
//        } catch(e:Throwable) {
//            logger.stackTrace(e)
//            error.setError("media error.")
//            completionListener.invoke(this, false)
//            return
//        }
//    }
//
//    // endregion -----------------------------------------------------------------------------
//
//    // region SurfaceHolder.Callback i/f
//
//    override fun surfaceCreated(holder: SurfaceHolder) {
//        displayVideoFrame(0.01f)
//        logger.debug()
//    }
//
//    override fun surfaceDestroyed(holder: SurfaceHolder) {
//        logger.debug()
//    }
//
//    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
//        logger.debug()
//    }

    // endregion -----------------------------------------------------------------------------

    override fun cancel() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

}