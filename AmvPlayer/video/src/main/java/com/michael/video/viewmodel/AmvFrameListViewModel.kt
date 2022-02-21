package com.michael.video.viewmodel

import android.graphics.Bitmap
import android.util.Size
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import com.michael.video.*
import java.io.File

class AmvFrameListViewModel : ViewModel() {

    /**
     * ビューからObserve i/f 経由で取得可能なデータi/f（リードオンリー）
     */
    interface IFrameListInfo {
        val frameList: ArrayList<Bitmap>
        val count: Int
        val currentIndex: Int
        val current: Bitmap

        val maxCount: Int
        val error: Throwable?
        val completed: Boolean

        val source: File?
        val size: Size
        val duration: Long

        enum class Status {
            INIT,               // 初期状態
            LOADED,             // ファイルがロードされた（size/durationが有効）
            FRAME,              // フレームのビットマップが取得された(currentが有効）
            TERM,                // フレームの取得がすべて完了した（エラー発生時はerror!=nullになる）
        }

        val status: Status
    }

    /**
     * 実際のデータを保持する内部データクラス
     */
    private inner class FrameListInfo : IFrameListInfo {
        var fitMode: FitMode = FitMode.Height
        var hintWidth = 200f
        var hintHeight = 200f

        override val frameList = ArrayList<Bitmap>(32)
        override val count: Int
            get() = frameList.size
        override val currentIndex: Int
            get() = count - 1
        override val current: Bitmap
            get() = frameList[currentIndex]

        override var maxCount: Int = 10

        override var source: File? = null

        override var error: Throwable? = null

        override val completed: Boolean
            get() = maxCount == count

        override var size = Size(100, 100)

        override var duration: Long = 0

        override var status: IFrameListInfo.Status = IFrameListInfo.Status.INIT
            set(v) {
                if(v!=field) {
                    field = v
                    frameListInfo.value = this // 通知
                }
            }

        private var mFrameExtractor: AmvFrameExtractor? = null

        val isBusy: Boolean
            get() = mFrameExtractor != null

        /**
         * 次のフレーム抽出に備えてパラメータをリセットし、新しいFrameExtractorを返す
         */
        fun reset(file: File, count:Int, mode:FitMode, width:Float, height:Float): AmvFrameExtractor {
            if (BuildConfig.DEBUG && isBusy) {
                error("Assertion failed")
            }
            clear()
            source = file
            maxCount = count
            fitMode = mode
            hintWidth = width
            hintHeight = height
            return AmvFrameExtractor().apply { mFrameExtractor = this }
        }

        /**
         * すべてのパラメータをクリアする。
         * Amv***の利用を終了するときに、リソース（主にビットマップ）を解放する目的で実行する。
         */
        fun clear() {
            source = null
            error = null
            for(bmp in frameList) {
                bmp.recycle()
            }
            frameList.clear()
            status = IFrameListInfo.Status.INIT
            duration = 0L
        }

        fun cancel() {
            mFrameExtractor?.cancel()
            mFrameExtractor?.dispose()
            mFrameExtractor = null
        }

        fun finish() {
            mFrameExtractor = null
        }

        fun pause() {
            mFrameExtractor?.pause()
        }
        fun resume() {
            mFrameExtractor?.resume()
        }
    }

    private val mFrameListInfo = FrameListInfo()

    /**
     * Observableなプロパティ
     */
    val frameListInfo = MutableLiveData<IFrameListInfo>()

    /**
     * getValue()が null になるのを回避
     */
    init {
        frameListInfo.value = mFrameListInfo
    }

    /**
     * フレーム抽出中か？
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val isBusy
        get() = mFrameListInfo.isBusy

    /**
     * 抽出操作をキャンセルする（ビジー状態を解除する）
     */
    fun cancel() {
        mFrameListInfo.cancel()
    }

    /**
     * すべてのパラメータをクリアする。
     * Amv***の利用を終了するときに、リソース（主にビットマップ）を解放する目的で実行する。
     */
    fun clear() {
        cancel()
        mFrameListInfo.clear()
    }

    fun pause() {
        mFrameListInfo.pause()
    }

    fun resume() {
        mFrameListInfo.resume()
    }



    /**
     * 設定値
     */

//    /**
//     * フレーム抽出時のサイズ決定ルールを指定
//     */
//    fun setSizingHint(fitMode: FitMode, width: Float, height: Float) {
//        this.fitMode = fitMode
//        this.hintWidth = width
//        this.hintHeight = height
//    }
//
//    /**
//     * 切り出すフレーム数を指定
//     */
//    fun setFrameCount(count: Int) {
//        mFrameListInfo.maxCount = count
//    }

    /**
     * フレームの抽出を開始する
     */
    fun extractFrame(file: File, count:Int, fitMode:FitMode, width:Float, height:Float): Boolean {
        if(mFrameListInfo.error!=null || (mFrameListInfo.source==file && mFrameListInfo.maxCount == count && mFrameListInfo.fitMode == fitMode && mFrameListInfo.hintWidth==width && mFrameListInfo.hintHeight==height)) {
            // エラー発生時、または、同じ条件でのフレーム抽出要求
            return false
        }

        if (isBusy) {
            cancel()
        }

        mFrameListInfo.reset(file, count, fitMode, width, height).apply {
            setSizingHint(fitMode, width, height)
            onVideoInfoRetrievedListener.add(null) {
                logger.info("onVideoInfoRetrieved: duration=${it.duration} / ${it.videoSize}")
                mFrameListInfo.size = it.thumbnailSize
                mFrameListInfo.duration = it.duration
                mFrameListInfo.status = IFrameListInfo.Status.LOADED
            }
            onThumbnailRetrievedListener.add(null) { _, index, bmp ->
                logger.debug("onThumbnailRetrieved :Bitmap($index): width=${bmp.width}, height=${bmp.height}")
                mFrameListInfo.frameList.add(bmp)
                mFrameListInfo.status = IFrameListInfo.Status.FRAME
            }
            onFinishedListener.add(null) { sender, result ->
                if (!result && sender.hasError) {
                    mFrameListInfo.error = sender.exception
                }
                mFrameListInfo.status = IFrameListInfo.Status.TERM
                mFrameListInfo.finish()
//                Handler().post {
//                    // リスナーの中でdispose()を呼ぶのはいかがなものかと思われるので、次のタイミングでお願いする
//                    dispose()
//                }
            }
            extract(file, mFrameListInfo.maxCount)
        }
        return true
    }

    fun setObserver(view:View,fn:(IFrameListInfo)->Unit) : Observer<IFrameListInfo> {
        val observer = Observer<IFrameListInfo> {
            if(it!=null) {
                fn(it)
            }
        }
        val activity = view.getActivity() as FragmentActivity
        frameListInfo.observe(activity, observer)
        return observer
    }

    fun resetObserver(observer:Observer<IFrameListInfo>?) {
        if(null!=observer) {
            frameListInfo.removeObserver(observer)
        }
    }

    companion object {
        /**
         * ビューをオブザーバーとして登録する
         */
        fun getInstance(view:View) : AmvFrameListViewModel {
            val activity = view.getActivity() as FragmentActivity
            return ViewModelProvider(activity, ViewModelProvider.NewInstanceFactory()).get(AmvFrameListViewModel::class.java)
        }

        val logger = AmvSettings.logger
    }
}