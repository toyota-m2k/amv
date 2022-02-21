/**
 * 動画からの静止画抽出用にフレーム位置を指定するためのビュー
 *
 * @author M.TOYOTA 2018.08.07 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.michael.video.viewmodel.AmvFrameListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min


class AmvFrameSelectorView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // region Public APIs
    fun setSource(source: File?) {
        controls.player?.setSource(AmvFileSource(source), false, 0)
    }

    fun dispose() {
        mFrameListViewModel.clear()
    }

    private var requestedFramePosition:Long = -1L

    /**
     * フレーム位置の取得/設定
     */
    var framePosition:Long
        get() = if(requestedFramePosition>=0) requestedFramePosition else controls.slider.currentPosition
        set(v) {
            if(v>0) {
                if (models.isPlayerPrepared && models.isVideoInfoPrepared) {
                    controls.player?.seekTo(v)
                    controls.slider.currentPosition = v
                    controls.frameListView.position = v
                    requestedFramePosition = -1
                } else {
                    requestedFramePosition = v
                }
            }
        }


    // endregion

    // region Internals

    // Constants
    companion object {
        const val FRAME_COUNT = 10            // フレームサムネイルの数
        const val FRAME_HEIGHT_IN_DP = 80f         // フレームサムネイルの高さ(dp)
        const val LISTENER_NAME = "frameSelectorView"
        val logger = AmvSettings.logger
    }

    private var mFrameHeight = 160f

    // Control
    private inner class Controls {
        val player : AmvExoVideoPlayer? by lazy {
            findViewById(R.id.vfs_player)
        }

        val slider: AmvSlider by lazy {
            findViewById(R.id.vfs_slider)
        }

        val sliderGroup: FrameLayout by lazy {
            findViewById(R.id.vfs_sliderGroup)
        }

        val frameListView: AmvFrameListView by lazy {
            findViewById(R.id.vfs_frameList)
        }
    }
    private val controls = Controls()

    // Model
    private inner class Models {
        var isPlayerPrepared: Boolean = false
        var isVideoInfoPrepared: Boolean = false
        var duration = 0L
    }
    private val models = Models()

    // Private fields
//    private var mFrameExtractor :AmvFrameExtractor? = null
    private val mFrameListViewModel : AmvFrameListViewModel
//    private val mHandler = Handler()

    // endregion

    // region Initialization

    init {
        LayoutInflater.from(context).inflate(R.layout.video_frame_selector, this)
        controls.slider.currentPositionChanged.set(this::onSliderChanged)
        controls.frameListView.touchFriendListener.set(controls.slider::onTouchAtFriend)
        mFrameHeight = context.dp2px(FRAME_HEIGHT_IN_DP)

        controls.player?.apply {

            // 動画の画面サイズが変わったときのイベント
//            sizeChangedListener.add(LISTENER_NAME) { _, width, _ ->
//                // layout_widthをBindingすると、どうしてもエラーになるので、直接変更
//                 controls.sliderGroup.setLayoutWidth(Math.max(width, 300))
//            }

            // プレーヤー上のビデオの読み込みが完了したときのイベント
            videoPreparedListener.add(LISTENER_NAME) { _, _ ->
                models.isPlayerPrepared = true
                restoringData?.tryRestoring()

                if(requestedFramePosition>0) {
                    framePosition = requestedFramePosition
                }
            }
            // 動画ソースが変更されたときのイベント
            sourceChangedListener.add(LISTENER_NAME) { _, source ->
                models.isPlayerPrepared = false
                models.isVideoInfoPrepared = false
                extractFrameOnSourceChanged(source)
            }
        }
        mFrameListViewModel = AmvFrameListViewModel.getInstance(this)
    }

    private var mFrameListObserver: Observer<AmvFrameListViewModel.IFrameListInfo>? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mFrameListObserver = mFrameListViewModel.setObserver(this, ::updateFrameListByViewModel)
    }

    override fun onDetachedFromWindow() {
        mFrameListViewModel.resetObserver(mFrameListObserver)
        mFrameListObserver = null
        super.onDetachedFromWindow()
    }


    /**
     * ソースが切り替わったタイミングで、フレームサムネイルリストを作成する
     */
    private fun extractFrameOnSourceChanged(source: IAmvSource) {
        mFrameListViewModel.viewModelScope.launch {
            val file = source.getFileAsync()
            withContext(Dispatchers.Main) {
                val info = mFrameListViewModel.frameListInfo.value!!
                if(info.status==AmvFrameListViewModel.IFrameListInfo.Status.INIT) {
                    models.duration = 0L  // ViewModelから読み込むとき、Durationがゼロかどうかで初回かどうか判断するので、ここでクリアする
                }
                if (null != file) {
                    if (!mFrameListViewModel.extractFrame(file, FRAME_COUNT, FitMode.Height, 0f, mFrameHeight)) {
                        // 抽出条件が変更されていない場合はfalseを返してくるのでキャッシュから構築する
                        updateFrameListByViewModel(info)
                    }
                }
            }
        }
    }

    /**
     * ViewModel経由でフレームサムネイルリストを更新する
     */
    private fun updateFrameListByViewModel(info: AmvFrameListViewModel.IFrameListInfo) {
        if(null!=info.error) {
            restoringData?.onFatalError()
        } else if(info.status != AmvFrameListViewModel.IFrameListInfo.Status.LOADED && info.duration>0L) {
            if(models.duration==0L) {
                models.duration = info.duration
                val thumbnailSize = info.size
                controls.frameListView.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                controls.slider.resetWithValueRange(info.duration, true)      // スライダーを初期化
                controls.frameListView.totalRange = info.duration
                adjustSliderPosition()
                models.isVideoInfoPrepared = true
                restoringData?.tryRestoring()
                framePosition = requestedFramePosition
            }
            if(info.count>0) {
                controls.frameListView.setFrames(info.frameList)
            }
        }
    }

    // endregion

    // region Slider manipulation

    private fun onSliderChanged(@Suppress("UNUSED_PARAMETER") slider:AmvSlider, position:Long, dragState:AmvSlider.SliderDragState) {
        logger.debug("current position: $position ($dragState)")
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

    /**
     * 各コントロール（Player/Slider/FrameList/Counter）のシーク位置を揃える
     */
    private fun updateSeekPosition(position:Long) {
        controls.player?.seekTo(position)
        controls.frameListView.position = position
    }

    // endregion

    // region Rendering

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val sliderHeight = controls.sliderGroup.measuredHeight + context.dp2px(16)
        controls.player?.setLayoutHint(FitMode.Inside, w.toFloat(), (h-sliderHeight).toFloat())
        if(models.isVideoInfoPrepared) {
            adjustSliderPosition()
        }
    }

    /**
     * スライダーの幅を決定 --> xmlのレイアウト指定によりセンタリングされる。
     * ~~~~~~~~~~~~
     *    親の幅とframeListのコンテントの幅（スクロールしなくなる幅）の小さい方
     */
    private fun adjustSliderPosition() {
        val width = measuredWidth
        val max = controls.frameListView.contentWidth +controls.frameListView.leftExtentWidth + controls.frameListView.rightExtentWidth
        controls.sliderGroup.setLayoutWidth(min(width, max))
    }

    // endregion

    // region Saving / Restoring

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
            logger.error("abort restoring.")
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
    internal class SavedData(val seekPosition:Long) {
        fun writeToParcel(parcel:Parcel) {
            parcel.writeLong(seekPosition)
        }
        constructor(parcel:Parcel) : this(parcel.readLong())
    }

    override fun onSaveInstanceState(): Parcelable {
        logger.debug()
        val parent =  super.onSaveInstanceState()
        return SavedState(parent, restoringData?.data ?: SavedData(controls.slider.currentPosition))
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        logger.debug()
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
    internal class SavedState : BaseSavedState {
        val data : SavedData?

        /**
         * Constructor called from [AmvSlider.onSaveInstanceState]
         */
        constructor(superState: Parcelable?, savedData: SavedData) : super(superState) {
            this.data = savedData
        }

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(parcel: Parcel) : super(parcel) {
            @Suppress("UNCHECKED_CAST")
            data = SavedData(parcel)
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            data?.writeToParcel(parcel)
        }

        companion object {
            @Suppress("unused")
            @JvmField
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

    // endregion

}