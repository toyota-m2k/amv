package com.michael.video

import android.content.Context
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.BindingAdapter
import android.databinding.DataBindingUtil
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import com.michael.utils.UtLogger
import com.michael.video.databinding.VideoControllerBinding
import com.michael.video.viewmodel.AmvFrameListViewModel
import java.io.File


class AmvVideoController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : ConstraintLayout(context,attrs,defStyleAttr), IAmvVideoController {

    // Constants
    companion object {
        const val FRAME_COUNT = 10            // フレームサムネイルの数
        const val FRAME_HEIGHT = 160f         // フレームサムネイルの高さ(dp)

        @JvmStatic
        @BindingAdapter("srcCompat")
        fun srcCompat(view: ImageButton, resourceId: Int) {
            view.setImageResource(resourceId)
        }
    }

    private var mBinding : VideoControllerBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.video_controller, this, true)
    private val mBindingParams = BindingParams()
    private lateinit var mPlayer:IAmvVideoPlayer
    private val mHandler = Handler()
    private var mFrameExtractor :AmvFrameExtractor? = null
    private var mDuration = 0L
    private val mFrameListViewModel : AmvFrameListViewModel?


    /**
     * Binding Data
     */
    inner class BindingParams : BaseObservable() {
        @get:Bindable
        val isPlaying
            get() = playerState == IAmvVideoPlayer.PlayerState.Playing

        @get:Bindable
        val isReady : Boolean
            get() {
                return when(playerState) {
                    IAmvVideoPlayer.PlayerState.Playing, IAmvVideoPlayer.PlayerState.Paused->true
                    else -> false
                }
            }

        @get:Bindable
        var isReadOnly : Boolean = true
            set(v) {
                if (field != v) {
                    field = v
                    notifyPropertyChanged(BR.readOnly)
                }
            }

//        val hasPrev : Boolean = false
//
//        val hasNext : Boolean = true

        @get:Bindable
        var showingFrames:Boolean = false
            set(v) {
                if(field!=v) {
                    field = v
                    notifyPropertyChanged(BR.showingFrames)
                }
            }

        @get:Bindable
        val minControllerWidth : Int = context.dp2px(325)

        var isPlayerPrepared : Boolean = false
        var isVideoInfoPrepared: Boolean = false

        var playerState:IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None
            set(v) {
                if(field != v) {
                    field = v
                    notifyPropertyChanged(BR.playing)
                    notifyPropertyChanged(BR.ready)
                    UtLogger.debug("PlayState: $v")
                    when (v) {
                        IAmvVideoPlayer.PlayerState.Playing -> {
                            val startPos = mPlayer.seekPosition
                            UtLogger.debug("Start Playing --> Seek($startPos)")

                            // 再生中は定期的にスライダーの位置を更新する
                            mHandler.post (object : Runnable {
                                override fun run() {
                                    if(!pausingOnTracking) {
                                        updateSeekPosition(mPlayer.seekPosition, false, true)
                                    }
                                    if(playerState==IAmvVideoPlayer.PlayerState.Playing) {
                                        mHandler.postDelayed(this, 100)     // Win版は10msで動かしていたが、Androidでは動画が動かなくなるので、200msくらいにしておく。ガタガタなるけど。
                                    }
                                }
                            })
                        }
                        IAmvVideoPlayer.PlayerState.None -> isPlayerPrepared = false
                        IAmvVideoPlayer.PlayerState.Error -> {
                            isPlayerPrepared = false
                            restoringData?.onFatalError()
                        }
                        else -> {}
                    }
                }
            }

        @get:Bindable
        var counterText:String = ""
            private set(v) {
                if(field!=v) {
                    field = v
                    notifyPropertyChanged(BR.counterText)
                }
            }

        var prevPosition = -1L
        
        fun updateCounterText(pos:Long) {
            if(mDuration<=0 || prevPosition==pos) {
                return
            }

            val total = AmvTimeSpan(mDuration)
            val current = AmvTimeSpan(if(pos>mDuration) mDuration else pos)

            counterText =
                    when {
                        total.hours > 0 -> "${current.formatH()} / ${total.formatH()}"
                        total.minutes > 0 -> "${current.formatM()} / ${total.formatM()}"
                        else -> "${current.formatS()} / ${total.formatS()}"
                    }
        }
    }

    init {
        mBinding.handlers = this
        mBinding.params = mBindingParams
        mBinding.slider.isSaveFromParentEnabled = false         // スライダーの状態は、AmvVideoController側で復元する

        // フレーム一覧のドラッグ操作をSliderのドラッグ操作と同様に扱うための小さな仕掛け
        mBinding.frameList.touchFriendListener.set(mBinding.slider::onTouchAtFriend)

        // MarkerView
        mBinding.markerView.markerSelectedListener.set { position, _ ->
            updateSeekPosition(position, true, true)
        }
        mBinding.markerView.markerAddedListener.set { _, _ ->
        }
        mBinding.markerView.markerRemovedListener.set { _, _ ->
        }
        mBinding.markerView.markerContextQueryListener.set { _, _ ->
        }

        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvSlider,defStyleAttr,0)
        try {
            val enableViewModel = sa.getBoolean(R.styleable.AmvVideoController_frameCache, true)
            mFrameListViewModel = if(enableViewModel) {
                AmvFrameListViewModel.registerToView(this, this::updateFrameListByViewModel)?.apply {
                    setSizingHint(FitMode.Height, 0f, FRAME_HEIGHT)
                    setFrameCount(FRAME_COUNT)
                }
            } else {
                null
            }

//            val activity = getActivity() as? FragmentActivity
//            mFrameListViewModel = if (enableViewModel && null != activity) {
//                ViewModelProviders.of(activity).get(AmvFrameListViewModel::class.java).apply {
//                    frameListInfo.observe(activity, Observer<AmvFrameListViewModel.IFrameListInfo> { info ->
//                        if (null != info) {
//                            updateFrameListByViewModel(info)
//                        }
//                    })
//                    setSizingHint(FitMode.Height, 0f, FRAME_HEIGHT)
//                    setFrameCount(FRAME_COUNT)
//                }
//            } else {
//                null
//            }
        } finally {
            sa.recycle()
        }
    }

    private val listenerName = "videoController"

    override fun setVideoPlayer(player:IAmvVideoPlayer) {
        mPlayer = player
        mBindingParams.playerState = player.playerState

        // Player Event
        mPlayer.apply {
            // 再生状態が変化したときのイベント
            playerStateChangedListener.add(listenerName) { _, state ->
                if(!pausingOnTracking) {
                    mBindingParams.playerState = state
                }
            }

            // 動画の画面サイズが変わったときのイベント
            sizeChangedListener.add(listenerName) { _, width, _ ->
                // layout_widthをBindingすると、どうしてもエラーになるので、直接変更
                mBinding.controllerRoot.setLayoutWidth(Math.max(width, mBindingParams.minControllerWidth))
            }

            // プレーヤー上のビデオの読み込みが完了したときのイベント
            videoPreparedListener.add(listenerName) { mp, _ ->
                mBindingParams.prevPosition = -1    // 次回必ずcounterString を更新する
                mBindingParams.updateCounterText(mp.seekPosition)
                mBindingParams.isPlayerPrepared = true
                restoringData?.tryRestoring()
            }
            // 動画ソースが変更されたときのイベント
            sourceChangedListener.add(listenerName) { _, source ->
                mBindingParams.isPlayerPrepared = false
                mBindingParams.isVideoInfoPrepared = false
                extractFrameOnSourceChanged(source)
            }
        }
    }

    private fun extractFrameOnSourceChanged(source: File) {
        mDuration = 0L  // ViewModelから読み込むとき、Durationがゼロかどうかで初回かどうか判断するので、ここでクリアする
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
                    mDuration = it.duration
                    val thumbnailSize = it.thumbnailSize

                    mBinding.frameList.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                    mBinding.slider.resetWithValueRange(it.duration, true)      // スライダーを初期化
                    mBinding.frameList.totalRange = it.duration
                    mBinding.markerView.resetWithTotalRange(it.duration)

                    mBindingParams.isVideoInfoPrepared = true
                    restoringData?.tryRestoring()
                }
                onThumbnailRetrievedListener.add(null) { _, index, bmp ->
                    UtLogger.debug("AmvFrameExtractor:Bitmap($index): width=${bmp.width}, height=${bmp.height}")
                    mBinding.frameList.add(bmp)
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
            if(mDuration==0L) {
                mDuration = info.duration
                val thumbnailSize = info.size
                mBinding.frameList.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                mBinding.slider.resetWithValueRange(info.duration, true)      // スライダーを初期化
                mBinding.frameList.totalRange = info.duration
                mBinding.markerView.resetWithTotalRange(info.duration)

                mBindingParams.isVideoInfoPrepared = true
                restoringData?.tryRestoring()
            }
            if(info.count>0) {
                mBinding.frameList.setFrames(info.frameList)
            }
        }
    }



    override var isReadOnly: Boolean
        get() = mBindingParams.isReadOnly
        set(v) { mBindingParams.isReadOnly=v }

    fun onPlayClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        when(mPlayer.playerState) {
            IAmvVideoPlayer.PlayerState.Paused -> mPlayer.play()
            IAmvVideoPlayer.PlayerState.Playing -> mPlayer.pause()
            else -> {}
        }
    }

    fun onPrevMarker(@Suppress("UNUSED_PARAMETER") view: View) {
        mBinding.markerView.prevMark(mBinding.slider.currentPosition, null)
    }

    fun onNextMarker(@Suppress("UNUSED_PARAMETER") view: View) {
        mBinding.markerView.nextMark(mBinding.slider.currentPosition, null)
    }

    fun onAddMarker(@Suppress("UNUSED_PARAMETER") view: View) {
        mBinding.markerView.addMarker(mBinding.slider.currentPosition, null)
    }

    fun onShowFramesClick(@Suppress("UNUSED_PARAMETER") view:View) {
        mBindingParams.showingFrames = !mBindingParams.showingFrames
    }

    // Sliderの操作
    private var pausingOnTracking = false       // スライダー操作中は再生を止めておいて、操作が終わったときに必要に応じて再生を再開する

    fun onCurrentPositionChanged(@Suppress("UNUSED_PARAMETER") caller:AmvSlider, position:Long, dragState: AmvSlider.SliderDragState) {
        UtLogger.debug("CurrentPosition: $position ($dragState)")
        when(dragState) {
            AmvSlider.SliderDragState.BEGIN-> {
                pausingOnTracking = mBindingParams.isPlaying
                mPlayer.pause()
                mPlayer.setFastSeekMode(true)
            }
            AmvSlider.SliderDragState.MOVING->{
                updateSeekPosition(position, true, false)
            }
            AmvSlider.SliderDragState.END-> {
                mPlayer.setFastSeekMode(false)
                updateSeekPosition(position, true, false)

                if(pausingOnTracking) {
                    mPlayer.play()
                    pausingOnTracking = false
                }
            }
            else -> {
            }
        }
        mBindingParams.updateCounterText(position)
    }

    fun updateSeekPosition(pos:Long, seek:Boolean, slider:Boolean) {
        if(seek) {
            mPlayer.seekTo(pos)
        }
        if(slider) {
            mBinding.slider.currentPosition = pos
        }
        mBinding.frameList.position = pos
    }

    private var restoringData: RestoringData? = null
    private inner class RestoringData(val data:SavedData) {
        private val isPlayerPrepared
            get() = mBindingParams.isPlayerPrepared
        private val isVideoInfoPrepared
            get() = mBindingParams.isVideoInfoPrepared

        private var isFirstRestored = false
        private var isPlayerRestored = false
        private var isSliderRestored = false

        fun onFatalError() {
            UtLogger.error("AmvTrimmingController: abort restoring.")
            this@AmvVideoController.restoringData = null
        }

        fun tryRestoring() {
            if(!isFirstRestored) {
                isFirstRestored = true
                mBindingParams.showingFrames = data.showingFrames
            }
            if(isPlayerPrepared && !isPlayerRestored) {
                mPlayer.seekTo(data.seekPosition)
                if(data.isPlaying) {
                    mPlayer.play()
                }
                isPlayerRestored = true
            }
            if(isVideoInfoPrepared && !isSliderRestored) {
                mBinding.slider.currentPosition = data.seekPosition
                mBinding.frameList.position = data.seekPosition
                mBinding.markerView.markers = data.markers
                isSliderRestored = true
            }

            if(isPlayerRestored && isSliderRestored) {
                this@AmvVideoController.restoringData = null
            }
        }
    }

    // region Saving States
    data class SavedData(val seekPosition:Long, val isPlaying:Boolean, val showingFrames:Boolean, val markers:ArrayList<Long>)


    override fun onSaveInstanceState(): Parcelable {
        UtLogger.debug("LC-View: onSaveInstanceState")
        val parent =  super.onSaveInstanceState()
        return SavedState(parent, restoringData?.data ?: SavedData(mBinding.slider.currentPosition, mBindingParams.isPlaying, mBindingParams.showingFrames, mBinding.markerView.markers))
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        UtLogger.debug("LC-View: onRestoreInstanceState")
        if(state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            restoringData = RestoringData(state.savedData)
            restoringData?.tryRestoring()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : View.BaseSavedState {

        val savedData: SavedData

        /**
         * Constructor called from [AmvSlider.onSaveInstanceState]
         */
        constructor(superState: Parcelable, savedData:SavedData) : super(superState) {
            this.savedData = savedData
        }

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(parcel: Parcel) : super(parcel) {
            @Suppress("UNCHECKED_CAST")
            savedData = SavedData(parcel.readLong(), parcel.readInt() == 1, parcel.readInt()==1, parcel.readArrayList(Long::class.java.classLoader) as ArrayList<Long>)
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeLong(savedData.seekPosition)
            parcel.writeInt(if(savedData.isPlaying) 1 else 0)
            parcel.writeInt(if(savedData.showingFrames) 1 else 0)
            parcel.writeList(savedData.markers)
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

    // endregion
}