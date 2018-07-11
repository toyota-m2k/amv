package com.michael.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Size
import com.michael.utils.Funcies1
import com.michael.utils.Funcies3
import com.michael.utils.UtAsyncTask
import java.io.File


class AmvFrameExtractor : UtAsyncTask() {
    var duration:Long = 0           // ms
    val videoSize = MuSize()
    val thumbnailSize : Size
        get() = mThumbnailSize.asSize

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

            if (mThumbnailCount <= 1) {
                // 主サムネイルを取得するモード
                val tm = Math.min(2000, duration / 100)
                val bmp = analyzer.getBitmapAt(tm)
                runOnUiThread(onThumbnailRetrievedListener, this, 0, bmp)
            } else {
                // フレームリストを取得するモード
                val step = duration / mThumbnailCount
                var tm = step / 2
                for (idx in 0.until(mThumbnailCount)) {
                    assert(tm<duration)
                    if (isCancelled) {
                        return
                    }
                    val bmp = analyzer.getBitmapAt(tm)
                    runOnUiThread(onThumbnailRetrievedListener, this, idx, bmp)
                    tm += step
                }
            }
        } finally {
            analyzer.release()
        }

    }

    // Working parameters
    private var mFile:File? = null
    private var mFitter:AmvFitter = AmvFitter(FitMode.Height, MuSize(150f))
    private var mThumbnailCount:Int = 0
    private val mThumbnailSize = MuSize()

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

}