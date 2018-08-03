package com.michael.video

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProviders
import android.graphics.Bitmap
import android.os.Handler
import android.support.v4.app.FragmentActivity
import android.util.Size
import android.view.View
import com.michael.utils.UtLogger
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
                field = v
                frameListInfo.value = this // 通知
            }

        private var mFrameExtractor: AmvFrameExtractor? = null

        val isBusy: Boolean
            get() = mFrameExtractor != null

        /**
         * 次のフレーム抽出に備えてパラメータをリセットし、新しいFrameExtractorを返す
         */
        fun reset(file: File): AmvFrameExtractor {
            assert(!isBusy)
            clear()
            source = file
            return AmvFrameExtractor().apply { mFrameExtractor = this }
        }

        /**
         * すべてのパラメータをクリアする。
         * Amv***の利用を終了するときに、リソース（主にビットマップ）を解放する目的で実行する。
         */
        fun clear() {
            source = null
            error = null
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
        mFrameListInfo.clear()
    }

    /**
     * 設定値
     */
    private var fitMode: FitMode = FitMode.Height
    private var hintWidth = 200f
    private var hintHeight = 200f

    /**
     * フレーム抽出時のサイズ決定ルールを指定
     */
    fun setSizingHint(fitMode: FitMode, width: Float, height: Float) {
        this.fitMode = fitMode
        this.hintWidth = width
        this.hintHeight = height
    }

    /**
     * 切り出すフレーム数を指定
     */
    fun setFrameCount(count: Int) {
        mFrameListInfo.maxCount = count
    }

    /**
     * フレームの抽出を開始する
     */
    fun extractFrame(file: File): Boolean {
        if (isBusy) {
            return false
        }

        mFrameListInfo.reset(file).apply {
            setSizingHint(fitMode, hintWidth, hintHeight)
            onVideoInfoRetrievedListener.add(null) {
                UtLogger.debug("AmvFrameExtractor:duration=${it.duration} / ${it.videoSize}")
                mFrameListInfo.size = it.thumbnailSize
                mFrameListInfo.duration = it.duration
                mFrameListInfo.status = IFrameListInfo.Status.LOADED
            }
            onThumbnailRetrievedListener.add(null) { _, index, bmp ->
                UtLogger.debug("AmvFrameExtractor:Bitmap($index): width=${bmp.width}, height=${bmp.height}")
                mFrameListInfo.frameList.add(bmp)
                mFrameListInfo.status = IFrameListInfo.Status.FRAME
            }
            onFinishedListener.add(null) { sender, result ->
                if (!result && sender.hasError) {
                    mFrameListInfo.error = sender.exception
                }
                mFrameListInfo.status = IFrameListInfo.Status.TERM
                mFrameListInfo.finish()
                Handler().post {
                    // リスナーの中でdispose()を呼ぶのはいかがなものかと思われるので、次のタイミングでお願いする
                    dispose()
                }
            }
            extract(file, mFrameListInfo.maxCount)
        }
        return true
    }

    companion object {
        /**
         * ビューをオブザーバーとして登録する
         */
        fun registerToView(view: View, fn: (IFrameListInfo) -> Unit): AmvFrameListViewModel? {
            val activity = view.getActivity() as? FragmentActivity
            return if (null != activity) {
                ViewModelProviders.of(activity).get(AmvFrameListViewModel::class.java).apply {
                    var first = true
                    frameListInfo.observe(activity, Observer<IFrameListInfo> { info ->
                        if (null != info) {
                            if(first) {
                                first = false   // ビュー生成時（新規or回転）に、observeしたタイミングで１回、無効な通知が発生し、リストア処理が空振りしてしまうのを回避するため、最初の１回をスキップする
                            } else {
                                fn(info)
                            }
                        }
                    })
                }
            } else {
                null
            }
        }
    }
}