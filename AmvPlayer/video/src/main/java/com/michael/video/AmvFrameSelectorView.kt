/**
 * 動画からの静止画抽出用にフレーム位置を指定するためのビュー
 *
 * @author M.TOYOTA 2018.08.07 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import android.content.Context
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.michael.utils.UtLogger
import com.michael.utils.readParceler
import com.michael.utils.writeParceler
import com.michael.video.viewmodel.AmvFrameListViewModel
import java.io.File


class AmvFrameSelectorView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    fun setSource(source:File) {
        controls.player?.setSource(source, false,0)
    }

    @Suppress("unused")
    val framePosition:Long
        get() = controls.slider.currentPosition

    // Constants
    companion object {
        const val FRAME_COUNT = 10            // フレームサムネイルの数
        const val FRAME_HEIGHT = 160f         // フレームサムネイルの高さ(dp)
        const val listenerName = "frameSelectorView"
    }

    private inner class Controls {
        val player : AmvExoVideoPlayer? by lazy {
            findViewById<AmvExoVideoPlayer>(R.id.vfs_player)
        }

        val slider by lazy {
            findViewById<AmvSlider>(R.id.vfs_slider)
        }

        val sliderGroup by lazy {
            findViewById<FrameLayout>(R.id.vfs_sliderGroup)
        }

        val frameListView by lazy {
            findViewById<AmvFrameListView>(R.id.vfs_frameList)
        }
    }

    private inner class Models {
        var isPlayerPrepared: Boolean = false
        var isVideoInfoPrepared: Boolean = false
        var duration = 0L
    }
    private val controls = Controls()
    private val models = Models()

    private var mFrameExtractor :AmvFrameExtractor? = null
    private val mFrameListViewModel : AmvFrameListViewModel?
    private val mHandler = Handler()

    init {
        LayoutInflater.from(context).inflate(R.layout.video_frame_selector, this)
        controls.slider.currentPositionChanged.set(this::onSliderChanged)
        controls.frameListView.touchFriendListener.set(controls.slider::onTouchAtFriend)

        controls.player?.apply {

            // 動画の画面サイズが変わったときのイベント
            sizeChangedListener.add(listenerName) { _, width, _ ->
                // layout_widthをBindingすると、どうしてもエラーになるので、直接変更
                controls.sliderGroup.setLayoutWidth(Math.max(width, 300))
            }

            // プレーヤー上のビデオの読み込みが完了したときのイベント
            videoPreparedListener.add(listenerName) { mp, _ ->
                models.isPlayerPrepared = true
                restoringData?.tryRestoring()
            }
            // 動画ソースが変更されたときのイベント
            sourceChangedListener.add(listenerName) { _, source ->
                models.isPlayerPrepared = false
                models.isVideoInfoPrepared = false
                extractFrameOnSourceChanged(source)
            }
        }

        mFrameListViewModel = AmvFrameListViewModel.registerToView(this, this::updateFrameListByViewModel)?.apply {
                setSizingHint(FitMode.Height, 0f, FRAME_HEIGHT)
                setFrameCount(FRAME_COUNT)
            }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val sliderHeight = controls.sliderGroup.getLayoutHeight() - context.dp2px(16)
        controls.player?.setLayoutHint(FitMode.Inside, w.toFloat(), (h-sliderHeight).toFloat())
    }

    private fun extractFrameOnSourceChanged(source: File) {
        models.duration = 0L  // ViewModelから読み込むとき、Durationがゼロかどうかで初回かどうか判断するので、ここでクリアする
        if(null!=mFrameListViewModel) {
            val info = mFrameListViewModel.frameListInfo.value!!
            if(source != info.source || null!=info.error) {
                // ソースが変更されているか、エラーが発生しているときはフレーム抽出を実行
                mFrameListViewModel.cancel()
                mFrameListViewModel.extractFrame(source)
            } else {
                // それ以外はViewModel（キャッシュ）から読み込む
                updateFrameListByViewModel(info)
            }
        } else {
            // フレームサムネイルを列挙する
            mFrameExtractor = AmvFrameExtractor().apply {
                setSizingHint(FitMode.Height, 0f, FRAME_HEIGHT)
                onVideoInfoRetrievedListener.add(null) {
                    UtLogger.debug("AmvFrameExtractor:duration=${it.duration} / ${it.videoSize}")
                    models.duration = it.duration
                    val thumbnailSize = it.thumbnailSize

                    controls.frameListView.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                    controls.slider.resetWithValueRange(it.duration, true)      // スライダーを初期化
                    controls.frameListView.totalRange = it.duration

                    models.isVideoInfoPrepared = true
                    restoringData?.tryRestoring()
                }
                onThumbnailRetrievedListener.add(null) { _, index, bmp ->
                    UtLogger.debug("AmvFrameExtractor:Bitmap($index): width=${bmp.width}, height=${bmp.height}")
                    controls.frameListView.add(bmp)
                }
                onFinishedListener.add(null) { _, _ ->
                    // サブスレッドの処理がすべて終了しても、UIスレッド側でのビットマップ追加処理待ちになっていることがあり、
                    // このイベントハンドラから、dispose()してしまうと、待ち中のビットマップが破棄されてしまう。
                    mHandler.post {
                        // リスナーの中でdispose()を呼ぶのはいかがなものかと思われるので、次のタイミングでお願いする
                        dispose()
                        mFrameExtractor = null
                    }
                }
                extract(source, FRAME_COUNT)
            }
        }
    }

    private fun updateFrameListByViewModel(info: AmvFrameListViewModel.IFrameListInfo) {
        if(null!=info.error) {
            restoringData?.onFatalError()
        } else if(info.status != AmvFrameListViewModel.IFrameListInfo.Status.INIT && info.duration>0L) {
            if(models.duration==0L) {
                models.duration = info.duration
                val thumbnailSize = info.size
                controls.frameListView.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                controls.slider.resetWithValueRange(info.duration, true)      // スライダーを初期化
                controls.frameListView.totalRange = info.duration

                models.isVideoInfoPrepared = true
                restoringData?.tryRestoring()
            }
            if(info.count>0) {
                controls.frameListView.setFrames(info.frameList)
            }
        }
    }



    private fun onSliderChanged(@Suppress("UNUSED_PARAMETER") slider:AmvSlider, position:Long, dragState:AmvSlider.SliderDragState) {
        UtLogger.debug("CurrentPosition: $position ($dragState)")
        when(dragState) {
            AmvSlider.SliderDragState.BEGIN-> {
                controls.player?.setFastSeekMode(true)
            }
            AmvSlider.SliderDragState.MOVING->{
                updateSeekPosition(position)
            }
            AmvSlider.SliderDragState.END-> {
                controls.player?.setFastSeekMode(false)
                updateSeekPosition(position)
            }
            else -> {
            }
        }
    }

    private fun updateSeekPosition(position:Long) {
        controls.player?.seekTo(position)
        controls.frameListView.position = position
    }



    /**
     * リストア中のデータを保持するクラス
     * - シーク位置は、Playerのロード完了をもってリストアする
     * - スライダーの状態は、ExtractFrameでdurationなどが得られるのを待ってリストアする
     */
    private inner class RestoringData(val data:SavedData) {
        private val isPlayerPrepared
            get() = models.isPlayerPrepared
        private val isVideoInfoPrepared
            get() = models.isVideoInfoPrepared

        private var isPlayerRestored = false
        private var isSliderRestored = false

        fun onFatalError() {
            UtLogger.error("AmvTrimmingController: abort restoring.")
            this@AmvFrameSelectorView.restoringData = null
        }

        fun tryRestoring() {
            if(isPlayerPrepared && !isPlayerRestored) {
                controls.player?.seekTo(data.seekPosition)
                isPlayerRestored = true
            }
            if(isVideoInfoPrepared && !isSliderRestored) {
                controls.slider.currentPosition = data.seekPosition
                controls.frameListView.position = data.seekPosition
                isSliderRestored = true
            }

            if(isPlayerRestored && isSliderRestored) {
                this@AmvFrameSelectorView.restoringData = null
            }
        }
    }
    private var restoringData: RestoringData? = null

    /**
     * 状態を退避するデータクラス
     * 保存するデータは１つだけだけど、他と同じ処理が流用できるので、クラスにしておく
     */
    @org.parceler.Parcel
    internal class SavedData(val seekPosition:Long) {
        constructor() : this(0L)
    }

    override fun onSaveInstanceState(): Parcelable {
        UtLogger.debug("LC-AmvFrameSelectorView: onSaveInstanceState")
        val parent =  super.onSaveInstanceState()
        return SavedState(parent, restoringData?.data ?: SavedData(controls.slider.currentPosition))
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        UtLogger.debug("LC-AmvFrameSelectorView: onRestoreInstanceState")
        if(state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            state.data?.apply {
                restoringData = RestoringData(this)
                restoringData?.tryRestoring()
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    /**
     * onSaveInstanceState用
     */
    internal class SavedState : View.BaseSavedState {
        val data : SavedData?

        /**
         * Constructor called from [AmvSlider.onSaveInstanceState]
         */
        constructor(superState: Parcelable, savedData: SavedData) : super(superState) {
            this.data = savedData
        }

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(parcel: Parcel) : super(parcel) {
            @Suppress("UNCHECKED_CAST")
            data = parcel.readParceler<SavedData>()
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParceler(data)
        }

        companion object {
            @Suppress("unused")
            @JvmStatic
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(parcel: Parcel): SavedState {
                    return SavedState(parcel)
                }
                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }

}