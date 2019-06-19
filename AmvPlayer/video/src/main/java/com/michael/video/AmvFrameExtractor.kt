/**
 * フレームビットマップ抽出ユーティリティ
 *
 * @author M.TOYOTA 2018.07.11 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */

package com.michael.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import com.michael.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.lang.IllegalArgumentException
import java.util.*

/**
 * Usage
 *
 * val source = File("path to movie file")
 * val extractor = AmvFrameExtractor()
 * extractor.setSizingHint(fitMode, widthHint, heightHint)  // see AmvFitter
 * extractor.onVideoInfoRetrievedListener.set { ex ->
 *  // 以下のプロパティが利用可能
 *  //   ex.duration            動画の総再生時間
 *  //   ex.videoSize           動画のサイズ(in pixel)
 *  //   ex.thumbnailSize       サムネイルのサイズ(in pixel)
 *  //   ex.targetFramePosition 取得するフレーム位置（getThumbnailの場合のみ）
 * extractor.onThumbnailRetrievedListener.set { ex, index, bitmap ->
 *  // index: フレームインデックス (0 .. count-1)
 *  // bitmap: フレームビットマップ
 * }
 * extractor.onFinishedListener.set { ex, result ->
 *  Handler().post {
 *     extractor.dispose()     // すべて終了したらdisposeする
 *  }
 * }
 *
 * // フレームリストを抽出
 * extractor.extract(source, count)     // count: フレーム数
 *
 * // or 指定位置のサムネイルを取得
 * extractor.getThumbnail(source, framePosition)     // framePosition: フレーム位置(ms)
 */
class AmvFrameExtractor : UtAsyncTask() {

    // region Public APIs

    fun setSizingHint(mode:FitMode, width:Float, height:Float) {
        mFitter.setHint(mode, width, height)
    }

    fun setSizingHint(fitter:AmvFitter) {
        mFitter.setHint(fitter.fitMode, fitter.layoutWidth, fitter.layoutHeight)
    }

    /**
     * フレームサムネイル抽出実行
     */
    fun extract(source:File, count:Int) {
        mFile = source
        mThumbnailCount = count

        execute()
    }

    fun getThumbnail(source:File, position:Long=-1) {
        mFile = source
        mThumbnailCount = 1
        mTargetFramePosition = position

        execute()
    }

    // endregion

    // region Properties

    @Suppress("MemberVisibilityCanBePrivate")
    var creationDate: Date? = null
    var duration:Long = 0           // ms
    val videoSize = MuSize()
    val thumbnailSize : Size
        get() = mThumbnailSize.asSize
    private val targetFramePosition : Long
        get() = if(mTargetFramePosition<0) Math.min(2000, duration / 100) else mTargetFramePosition
    private val pausing = UtResetableEvent(initialSignaled = true, mAutoReset = false)

    // endregion

    // region Listeners

    // ビデオ情報（videoSize/durationが取得されたときのイベント（UIスレッドから呼び出されることを保証）
    val onVideoInfoRetrievedListener = Funcies1<AmvFrameExtractor, Unit>()
    // フレームサムネイルが取得されたときのイベント（UIスレッドから呼び出されることを保証）
    val onThumbnailRetrievedListener = Funcies3<AmvFrameExtractor, Int, Bitmap, Unit>()

    // setSizingHint()を呼ぶラストチャンス・・・上のリスナーと異なり、非UIスレッドから同期的に呼び出される
    val chanceForSettingThumbnailSize = Funcies2<AmvFrameExtractor, Size, Unit>()

    private fun MediaMetadataRetriever.getLong(key:Int, def:Long = 0) :Long {
        val s = extractMetadata(key)
        if(null!=s) {
            return s.toLong()
        }
        return def
    }

    private fun MediaMetadataRetriever.getDate(key:Int) : Date? {
        val s = extractMetadata(key)
        return if(null!=s) {
            parseIso8601DateString(s)
        } else {
            null
        }
    }

    /**
     * 指定オフセット位置のフレーム画像を取得
     * API-27以降なら、サムネイルサイズにリサイズした画像が取得され、API-26以下なら、生サイズのやつが取得される。
     *
     * @param tm        オフセット位置
     * @param option    OPTION_CLOSEST（WinのNearestFrame）/ OPTION_CLOSEST_SYNC (WinのNearestKeyFrame)
     */
    private fun MediaMetadataRetriever.getBitmapAt(tm:Long, option:Int) : Bitmap? {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getScaledFrameAtTime(tm*1000, option, mThumbnailSize.width.toInt(), mThumbnailSize.height.toInt())

        } else {
            getFrameAtTime(tm*1000, option)
        }
    }

    /**
     * ビットマップのサイズを変更する
     */
    private fun fitBitmapScale(src:Bitmap, width:Int, height:Int) : Bitmap {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return src
        }
        if(src.width==width && src.height==height) {
            return src
        }
        val bmp = Bitmap.createScaledBitmap(src, width, height, true)
        src.recycle()
        return bmp
    }

    /**
     * 指定オフセット位置のフレーム画像を取得
     */
    private fun MediaMetadataRetriever.getBitmapAt(tm:Long) : Bitmap {
        // フレームリスト作成時（mThumbnailCount>1)は、高速化のため、OPTION_CLOSEST_SYNC（キーフレームを取得）、
        // １枚だけ画像生成する場合は、OPTION_CLOSEST （正確なやつを取得）を指定する。
        val option = if(mThumbnailCount>1) MediaMetadataRetriever.OPTION_CLOSEST_SYNC else MediaMetadataRetriever.OPTION_CLOSEST
        var bmp = getBitmapAt(tm, option)
        // OPTION_CLOSESTで、tm==durationを渡した場合ｍ、getBitmapAt()が失敗することがある（ていうか、Chromebookの場合しか再現していないけど）。
        // この場合は、optionにOPTION_CLOSEST_SYNCで、再試行する。（これで、Chromebookもうまくいった）
        if(bmp==null && option == MediaMetadataRetriever.OPTION_CLOSEST) {
            bmp = getBitmapAt(tm, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
        // 再試行してもだめならやはりエラー
        if(bmp==null) {
            throw IllegalArgumentException("MediaMetadataRetriever:cannot extract frame.")
        }
        // 必要に応じてリサイズ
        return fitBitmapScale(bmp, mThumbnailSize.width.toInt(), mThumbnailSize.height.toInt())
    }


    // endregion

    // region Internals

    override fun task() {
        val path = mFile?.path ?: return
        val analyzer = prepareAnalyzer(path) ?: return
        try {
            chanceForSettingThumbnailSize.invoke(this, videoSize.asSize)
            mFitter.fit(videoSize, mThumbnailSize)

            // duration / videoSize が利用可能
            runOnUiThread(onVideoInfoRetrievedListener, this)
            mFitter.fit(videoSize, mThumbnailSize)  // onVideoInfoRetrievedListenerで fitterの設定が変更される可能性があるので、もう一度実行。

            // キャンセルチェック
            if (isCancelled) {
                return
            }

            if (mThumbnailCount == 1) {
                // 主サムネイルを取得するモード
                val bmp = analyzer.getBitmapAt(targetFramePosition)
                runOnUiThread(onThumbnailRetrievedListener, this, 0, bmp)
            } else if(mThumbnailCount>1){
                // フレームリストを取得するモード
                val step = duration / mThumbnailCount
                var tm = step / 2
                for (idx in 0.until(mThumbnailCount)) {
                    UtLogger.debug("AmvFrameExtractor: processing ${idx + 1} - frame.")
                    assert(tm < duration)
                    if (isCancelled) {
                        return
                    }
                    val bmp = analyzer.getBitmapAt(tm)
                    runOnUiThread(onThumbnailRetrievedListener, this, idx, bmp)
                    tm += step
                    pausing.waitOne()
                }
            }
        } catch (e:Throwable) {
            UtLogger.stackTrace(e, "AmvFrameExtractor: error.")
            // エラーをスローしておけば親クラスで適切にに処理する
            throw e
        } finally {
            UtLogger.debug("AmvFrameExtractor: analyzer releasing.")
            analyzer.release()
        }
    }

    // Working parameters
    private var mFile:File? = null
    private var mFitter:AmvFitter = AmvFitter(FitMode.Height, MuSize(160f))
    private var mThumbnailCount:Int = 0
    private val mThumbnailSize = MuSize()
    private var mTargetFramePosition = -1L

    /**
     * 破棄・・・UIスレッドから呼び出すこと
     */
    override fun dispose() {
        resume()
        super.dispose()
        onVideoInfoRetrievedListener.clear()
        onThumbnailRetrievedListener.clear()
        chanceForSettingThumbnailSize.clear()
    }

    fun pause() {
        pausing.reset()
    }

    fun resume() {
        pausing.set()
    }

    override fun cancel() {
        resume()
        super.cancel()
    }

    /**
     * MediaMetadataRetrieverインスタンスを作成する。
     * その過程で、duration, videoSize などのフィールドに動画ファイルの情報がセットされる。
     *
     * @param path  動画ファイルのパス
     * @return MediaMetadataRetrieverのインスタンス (extractOneの引数として使う）
     */
    fun prepareAnalyzer(path:String) : MediaMetadataRetriever? {
        val analyzer = MediaMetadataRetriever()
        return try {
            // ソースをセット
            analyzer.setDataSource(path)

            // 基本プロパティを取得
            creationDate = analyzer.getDate(MediaMetadataRetriever.METADATA_KEY_DATE)
            duration = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_DURATION)
            videoSize.height = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toFloat()
            videoSize.width = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toFloat()
            val rotate = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (rotate == 90L || rotate == 270L) {
                videoSize.rotate()
            }
            mFitter.fit(videoSize, mThumbnailSize)
            analyzer
        } catch(e:Throwable) {
            analyzer.release()
            null
        }
    }

    /**
     * 単純にサムネイルを１つ取得する
     * Coroutineブロックなどから呼び出されることを想定。
     * １つの動画ファイルから、複数のサムネイルを取り出すときは、第一引数にMediaMetadataRetrieverを渡すバージョンを利用するとよい。
     *
     * @param path  動画ファイルパス
     * @param position サムネイルを取得するシーク位置(-1なら自動的に先頭付近のサムネイルをいい感じに取得）
     * @return 生成したビットマップ （null:失敗）
     */
    fun extractOne (path:String, position:Long=-1) : Bitmap? {
        val analyzer = prepareAnalyzer(path) ?: return null
        return extractOne(analyzer, position)
    }
    /**
     * 単純にサムネイルを１つ取得する
     * Coroutineブロックなどから呼び出されることを想定。
     *
     * @param analizer 必ずprepareAnalyzer()メソッドで取得したMediaMetadataRetrieverインスタンスを渡す。
     * @param position サムネイルを取得するシーク位置(-1なら自動的に先頭付近のサムネイルをいい感じに取得）
     * @return 生成したビットマップ （null:失敗）
     */
    fun extractOne (analyzer:MediaMetadataRetriever, position:Long=-1) : Bitmap? {
        try {
            // 主サムネイルを取得するモード
            return analyzer.getBitmapAt(if(position<0) Math.min(2000, duration / 100) else position)
        } catch (e:Throwable) {
            UtLogger.stackTrace(e, "AmvFrameExtractor: error.")
            return null
        } finally {
            UtLogger.debug("AmvFrameExtractor: analyzer releasing.")
            analyzer.release()
        }
    }
}