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
import com.michael.utils.Funcies1
import com.michael.utils.Funcies3
import com.michael.utils.UtAsyncTask
import com.michael.utils.UtLogger
import java.io.File

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
    }

    // endregion

    // region Properties

    var duration:Long = 0           // ms
    val videoSize = MuSize()
    val thumbnailSize : Size
        get() = mThumbnailSize.asSize
    val targetFramePosition : Long
        get() = if(mTargetFramePosition<0) Math.min(2000, duration / 100) else mTargetFramePosition

    // endregion

    // region Listeners

    val onVideoInfoRetrievedListener = Funcies1<AmvFrameExtractor, Unit>()
    val onThumbnailRetrievedListener = Funcies3<AmvFrameExtractor, Int, Bitmap, Unit>()

    private fun MediaMetadataRetriever.getLong(key:Int, def:Long = 0) :Long {
        val s = extractMetadata(key)
        if(null!=s) {
            return s.toLong()
        }
        return def
    }

    private fun MediaMetadataRetriever.getBitmapAt(tm:Long) : Bitmap {
        return if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getScaledFrameAtTime(tm*1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, mThumbnailSize.width.toInt(), mThumbnailSize.height.toInt())
        } else {
            val bmp = getFrameAtTime(tm*1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            Bitmap.createScaledBitmap(bmp, mThumbnailSize.width.toInt(), mThumbnailSize.height.toInt(), true)
        }
    }

    // endregion

    // region Internals

    override fun task() {
        val analyzer = MediaMetadataRetriever()
        try {
            // ソースをセット
            analyzer.setDataSource(mFile?.path)

            // 基本プロパティを取得
            duration = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_DURATION)
            videoSize.height = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toFloat()
            videoSize.width = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toFloat()
            mFitter.fit(videoSize, mThumbnailSize)

            // duration / videoSize が利用可能
            runOnUiThread(onVideoInfoRetrievedListener, this)

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
                }
            }
        } catch (e:Throwable) {
            UtLogger.debug("AmvFrameExtractor: error.")
            // エラーをスローしておけば親クラスで適切にに処理する
            throw e
        } finally {
            UtLogger.debug("AmvFrameExtractor: analizer releasing.")
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
        super.dispose()
        onVideoInfoRetrievedListener.clear()
        onThumbnailRetrievedListener.clear()
    }
}