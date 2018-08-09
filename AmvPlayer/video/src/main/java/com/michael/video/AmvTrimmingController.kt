/**
 * トリミング操作用コントローラービュー
 *
 * @author M.TOYOTA 2018.07.26 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import com.michael.utils.UtLogger
import com.michael.utils.readParceler
import com.michael.utils.writeParceler
import com.michael.video.viewmodel.AmvFrameListViewModel
import org.parceler.ParcelConstructor
import java.io.File
import kotlin.math.roundToInt

class AmvTrimmingController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : ConstraintLayout(context,attrs,defStyleAttr), IAmvVideoController {

    // region Public

    /**
     * トリミング範囲を取得
     */
    val trimmingRange: IAmvVideoPlayer.Clipping
        get() = IAmvVideoPlayer.Clipping(controls.slider.trimStartPosition, controls.slider.trimEndPosition)

    /**
     * トリミングされているか
     */
    val isTrimmed : Boolean
        get() = models.isPlayerPrepared && models.naturalDuration>0 && controls.slider.isTrimmed

    val leftExtentWidth
        get() = controls.slider.leftExtentWidth
    val rightExtentWidth
        get() = controls.slider.rightExtentWidth
    val extentWidth
        get() = controls.slider.extentWidth

    /**
     * フレームリストのコンテント幅（スクローラー内コンテントの幅）が確定したときにコールバックされる
     */
//    override val contentWidthChanged = IAmvVideoController.ContentWidthChanged()

    // endregion

    // region Private fields

    // constants
    companion object {
        private const val FRAME_COUNT = 10            // フレームサムネイルの数
        private const val FRAME_HEIGHT = 160f         // フレームサムネイルの高さ(dp)
        private const val LISTENER_NAME = "trimmingController"
    }

    private val models = Models()
    private val controls = Controls()

    private val drPlay:Drawable
    private val drPause:Drawable

    // Sliderの操作
    private var pausingOnTracking = false       // スライダー操作中は再生を止めておいて、操作が終わったときに必要に応じて再生を再開する
    private lateinit var mPlayer:IAmvVideoPlayer
    private var mFrameExtractor :AmvFrameExtractor? = null
    private var mClipping : IAmvVideoPlayer.Clipping? = null

    private val mHandler = Handler()
    private var mHandlingKnob:AmvSlider.Knob = AmvSlider.Knob.NONE
    private val mFrameListViewModel : AmvFrameListViewModel?


    init {
        LayoutInflater.from(context).inflate(R.layout.video_trimming_controller, this)
        drPlay = context.getDrawable(R.drawable.ic_play)
        drPause = context.getDrawable(R.drawable.ic_pause)

        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvTrimmingtController,defStyleAttr,0)
        try {
            val enableViewModel = sa.getBoolean(R.styleable.AmvTrimmingtController_frameCache, true)
            mFrameListViewModel = if (enableViewModel) {
                AmvFrameListViewModel.registerToView(this, this::updateFrameListByViewModel)?.apply {
                    setSizingHint(FitMode.Height, 0f, FRAME_HEIGHT)
                    setFrameCount(FRAME_COUNT)
                }
            } else {
                null
            }
        } finally {
            sa.recycle()
        }

        controls.initialize()
    }

    // endregion

    // region Bindings

    // Views
    inner class Controls {
        // Controls
        val root:ConstraintLayout by lazy {
            findViewById<ConstraintLayout>(R.id.vtc_root)
        }
        val sliderGroup: FrameLayout by lazy {
            findViewById<FrameLayout>(R.id.vtc_sliderGroup)
        }
        val slider: AmvSlider by lazy {
            findViewById<AmvSlider>(R.id.vtc_slider)
        }
        val frameList: AmvFrameListView by lazy {
            findViewById<AmvFrameListView>(R.id.vtc_frameList)
        }
        val playButton: ImageButton by lazy {
            findViewById<ImageButton>(R.id.vtc_playButton)
        }

        val trimStartText: TextView by lazy {
            findViewById<TextView>(R.id.vtc_trimStartText)
        }
        val trimEndText: TextView by lazy {
            findViewById<TextView>(R.id.vtc_trimEndText)
        }

        fun onUpdatePlayerState(state:IAmvVideoPlayer.PlayerState) {
            when(state) {
            // Playボタンの状態を更新
                IAmvVideoPlayer.PlayerState.Paused-> {
                    playButton.alpha = 1f
                    playButton.setImageDrawable(drPlay)
                    playButton.isClickable = true
                    updateKnobVisibility()
                }
                IAmvVideoPlayer.PlayerState.Playing-> {
                    playButton.alpha = 1f
                    playButton.setImageDrawable(drPause)
                    playButton.isClickable = true
                    updateKnobVisibility()
                }
                else -> {
                    playButton.alpha = 0.4f
                    playButton.isClickable = false
                }
            }
        }

        private fun formatTime(time:Long) : String {
            val v = AmvTimeSpan(time)
            val t = AmvTimeSpan(slider.valueRange)
            return when {
                t.hours>0 -> v.formatH()
                t.minutes>0 -> v.formatM()
                else -> v.formatS()
            }
        }

        fun updateTrimStartText() {
            trimStartText.text = formatTime(slider.trimStartPosition)
        }
        fun updateTrimEndText() {
            trimEndText.text = formatTime(slider.trimEndPosition)
        }


        fun initialize() {
            playButton.setOnClickListener {
                when(mPlayer.playerState) {
                    IAmvVideoPlayer.PlayerState.Paused -> {
                        mPlayer.setClip(trimmingRange)
                        mPlayer.play()
                    }
                    IAmvVideoPlayer.PlayerState.Playing -> {
                        mPlayer.pause()
                    }
                    else -> {}
                }
            }

            slider.currentPositionChanged.set { _, position, state ->
                sliderPositionChanged(position, state, AmvSlider.Knob.THUMB)
            }
            slider.trimStartPositionChanged.set { _, position, state ->
                mPlayer.setClip(null)
                sliderPositionChanged(position, state, AmvSlider.Knob.LEFT)
                controls.frameList.trimStart = position
                updateTrimStartText()
            }
            slider.trimEndPositionChanged.set { _, position, state ->
                mPlayer.setClip(null)
                sliderPositionChanged(position, state, AmvSlider.Knob.RIGHT)
                controls.frameList.trimEnd = position
                updateTrimEndText()
            }
            slider.isSaveFromParentEnabled = false         // スライダーの状態は、AmvTrimmingController側で復元する

            frameList.touchFriendListener.set(slider::onTouchAtFriend)
            frameList.trimmingFriendListener.set(slider::onTrimmingAtFriend)
        }

        fun resetWithDuration(duration:Long) {
            slider.resetWithValueRange(duration, true)      // スライダーを初期化
            frameList.totalRange = duration
            updateTrimStartText()
            updateTrimEndText()
        }
    }

    // Models
    inner class Models {
        var playerState:IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None
            set(v) {
                field = v
                controls.onUpdatePlayerState(v)
            }
        var playerWidth: Int = 0

        var naturalDuration: Long = 0

        var isPlayerPrepared = false
        var isVideoInfoPrepared = false

        val isPlaying: Boolean
            get() = playerState == IAmvVideoPlayer.PlayerState.Playing
    }

    // endregion

    // region IAmvVideoController implements

    override var isReadOnly: Boolean = true

    override fun setVideoPlayer(player: IAmvVideoPlayer) {
        mPlayer = player
        models.playerState = player.playerState

        // Player Event
        // 再生状態が変化したときのイベント
        mPlayer.playerStateChangedListener.add(LISTENER_NAME) { _, state ->
            if (!pausingOnTracking) {
                models.playerState = state
            }
            if(state == IAmvVideoPlayer.PlayerState.Playing) {
                mHandler.post(mSliderSeekerOnPlaying)
            }
            else if(state == IAmvVideoPlayer.PlayerState.Error) {
                restoringData?.onFatalError()
            }
        }

        // 動画の画面サイズが変わったときのイベント
        mPlayer.sizeChangedListener.add(LISTENER_NAME) { _, width, _ ->
            // layout_widthをBindingすると、どうしてもエラーになるので、直接変更
            models.playerWidth = width
        }

        // プレーヤー上のビデオの読み込みが完了したときのイベント
        mPlayer.videoPreparedListener.add(LISTENER_NAME) { _, _ ->
            models.isPlayerPrepared = true
            restoringData?.tryRestoring()
        }

            // 動画ソースが変更されたときのイベント
        mPlayer.sourceChangedListener.add(LISTENER_NAME) { _, source ->
//            data = null    // 誤って古い情報をリストアしないように。
            models.isPlayerPrepared = false
            models.isVideoInfoPrepared = false
            extractFrameOnSourceChanged(source)
        }

        mPlayer.clipChangedListener.add(LISTENER_NAME) { _, clipping ->
            mClipping = clipping
        }
    }

    private fun extractFrameOnSourceChanged(source: File) {
        models.naturalDuration = 0L  // ViewModelから読み込むとき、Durationがゼロかどうかで初回かどうか判断するので、ここでクリアする
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
                    val thumbnailSize = it.thumbnailSize
                    controls.frameList.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                    models.naturalDuration = it.duration
                    controls.resetWithDuration(it.duration)
                    models.isVideoInfoPrepared = true
                    restoringData?.tryRestoring()
                    //contentWidthChanged.invoke(this@AmvTrimmingController, controls.frameList.contentWidth)
                    adjustSliderPosition()
                }
                onThumbnailRetrievedListener.add(null) { _, index, bmp ->
                    UtLogger.debug("AmvFrameExtractor:Bitmap(${index+1}): width=${bmp.width}, height=${bmp.height}")
                    controls.frameList.add(bmp)
                }
                onFinishedListener.add(null) { _, result ->
                    post {
                        // リスナーの中でdispose()を呼ぶのはいかがなものかと思われるので、次のタイミングでお願いする
                        dispose()
                        mFrameExtractor = null
                        if(!result) {
                            restoringData?.onFatalError()
                        }
                    }
                }
                extract(source, FRAME_COUNT)
            }
        }
    }

    private fun updateFrameListByViewModel(info:AmvFrameListViewModel.IFrameListInfo) {
        if(null!=info.error) {
            restoringData?.onFatalError()
        } else if(info.status != AmvFrameListViewModel.IFrameListInfo.Status.INIT && info.duration>0L) {
            if(models.naturalDuration==0L) {
                models.naturalDuration = info.duration
                val thumbnailSize = info.size
                controls.frameList.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                controls.slider.resetWithValueRange(info.duration, true)      // スライダーを初期化
                controls.frameList.totalRange = info.duration
                controls.updateTrimStartText()
                controls.updateTrimEndText()
                models.isVideoInfoPrepared = true
                restoringData?.tryRestoring()
//                contentWidthChanged.invoke(this@AmvTrimmingController, controls.frameList.contentWidth)
                adjustSliderPosition()
            }
            if(info.count>0) {
                controls.frameList.setFrames(info.frameList)
            }
        }
    }

    fun adjustSliderPosition() {
        val max = controls.frameList.contentWidth + controls.slider.extentWidth
        val width = controls.root.measuredWidth
        controls.sliderGroup.setLayoutWidth(Math.min(max.roundToInt(),width))
    }

    // endregion

    // region Slider Handling

    /**
     * 再生中、定期的にスライダーのノブ位置を更新する処理（Handler#postDelayを使って疑似タイマー動作を行うためのRunnable)
     */
    private val mSliderSeekerOnPlaying = object : Runnable {
        override fun run() {
            if(!pausingOnTracking) {
                updateSeekPosition(mPlayer.seekPosition, false, AmvSlider.Knob.NONE)
            }
            if(models.playerState==IAmvVideoPlayer.PlayerState.Playing) {
                mHandler.postDelayed(this, 100)     // Win版は10ms・・・Androidでは無理
            }
        }
    }

    /**
     * Player / Slider / FrameList のシーク位置を更新する
     *
     * @param pos           シーク位置
     * @param seekPlayer    true:Playerをシークする
     * @param knob          操作中のスライダーノブ（スライダー外から更新する場合はNONE）
     */
    private fun updateSeekPosition(pos:Long, seekPlayer:Boolean, knob:AmvSlider.Knob) {
        if(seekPlayer) {
            mPlayer.seekTo(pos)
        }
        when(knob) {
            AmvSlider.Knob.LEFT, AmvSlider.Knob.NONE-> controls.slider.currentPosition = pos
            AmvSlider.Knob.RIGHT->controls.slider.currentPosition = controls.slider.trimStartPosition
            else -> {}
        }
        controls.frameList.position = pos
    }

    /**
     * スライダーのノブ位置変更イベントリスナーの処理
     *
     * @param position      シーク位置
     * @param dragState     BEGIN/MOVING/END
     * @param knob          変更のあったノブ種別
     */
    private fun sliderPositionChanged(position:Long, dragState: AmvSlider.SliderDragState, knob:AmvSlider.Knob) {
        if(isRestoring) {
            return
        }

        UtLogger.debug("CurrentPosition: $position ($dragState)")
        when(dragState) {
            AmvSlider.SliderDragState.BEGIN-> {
                mHandlingKnob = knob
                pausingOnTracking = knob == AmvSlider.Knob.THUMB && models.isPlaying
                mPlayer.pause()
                mPlayer.setFastSeekMode(true)
                updateKnobVisibility()
            }
            AmvSlider.SliderDragState.MOVING->{
                if(knob==mHandlingKnob) {
                    updateSeekPosition(position, true, knob)
                }
            }
            AmvSlider.SliderDragState.END-> {
                mPlayer.setFastSeekMode(false)
                updateSeekPosition(position, true, knob)
                mHandlingKnob = AmvSlider.Knob.NONE
                if(pausingOnTracking) {
                    mPlayer.play()
                    pausingOnTracking = false
                }
                updateKnobVisibility(true)
            }
            else -> {

            }
        }
        //mBindingParams.updateCounterText(position)
    }

    fun updateKnobVisibility(dragEnd:Boolean=false) {
        controls.frameList.showKnob = (!dragEnd && controls.slider.isKnobDragging(AmvSlider.Knob.THUMB)) || models.isPlaying
    }

    // endregion

    // region Saving States

    /**
     * 状態を退避するデータクラス
     */
    @org.parceler.Parcel
    internal class SavedData @ParcelConstructor constructor(
            var isPlaying:Boolean,
            var seekPosition: Long,
            var current: Long,
            var trimStart: Long,
            var trimEnd: Long) {

        @Suppress("unused")
        constructor() : this(false, 0,0, 0, -1)
    }

    // 復元中のデータ
    private var restoringData: RestoringData? = null
    private val isRestoring
        get() = restoringData!=null

    /**
     * リストア中のデータを保持するクラス
     * - 再生状態、再生シーク位置は、Playerのロード完了をもってリストアする
     * - スライダー/トリミング状態は、ExtractFrameでdurationなどが得られるのを待ってリストアする
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
            this@AmvTrimmingController.restoringData = null
        }

        fun tryRestoring() {
            if(isPlayerPrepared && !isPlayerRestored) {
                if(data.isPlaying) {
                    mPlayer.setClip(trimmingRange)
                    mPlayer.play()
                }
                mPlayer.seekTo(data.seekPosition)
                isPlayerRestored = true
            }
            if(isVideoInfoPrepared && !isSliderRestored) {
                controls.slider.trimStartPosition = data.trimStart
                if(data.trimEnd>data.trimStart) {
                    controls.slider.trimEndPosition = data.trimEnd
                }
                if(data.trimStart<=data.current && data.current<=data.trimEnd) {
                    controls.slider.currentPosition = data.current
                }
                isSliderRestored = true
            }

            if(isPlayerRestored && isSliderRestored) {
                this@AmvTrimmingController.restoringData = null
            }
        }
    }

    /**
     * 状態を退避
     */
    override fun onSaveInstanceState(): Parcelable {
        UtLogger.debug("LC-TrimmingController: onSaveInstanceState")
        val parent =  super.onSaveInstanceState()
        return SavedState(parent, restoringData?.data ?: SavedData(models.isPlaying, mPlayer.seekPosition, controls.slider.currentPosition, controls.slider.trimStartPosition, controls.slider.trimEndPosition))
    }

    /**
     * 状態を復元
     */
    override fun onRestoreInstanceState(state: Parcelable?) {
        UtLogger.debug("LC-TrimmingController: onRestoreInstanceState")
        if(state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            val data = state.data
            if(null!=data) {
                restoringData = RestoringData(data)
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
    // endregion

}