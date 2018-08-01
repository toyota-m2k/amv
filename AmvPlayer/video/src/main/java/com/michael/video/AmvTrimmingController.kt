package com.michael.video

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
import com.michael.utils.UtLogger

class AmvTrimmingController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : ConstraintLayout(context,attrs,defStyleAttr), IAmvVideoController {

    // region Public

    /**
     * トリミング範囲を取得
     */
    val trimmingRange: IAmvVideoPlayer.Clipping
        get() = IAmvVideoPlayer.Clipping(mControls.slider.trimStartPosition, mControls.slider.trimEndPosition)

    val isTrimmed : Boolean
        get() = mDataModel.isPrepared && mDataModel.naturalDuration>0 && mControls.slider.isTrimmed

    // endregion

    // region Private fields

    // constants
    private val cFrameCount = 10            // フレームサムネイルの数
    private val cFrameHeight = 160f         // フレームサムネイルの高さ(dp)
    private val listenerName = "trimmingController"

    private val mDataModel = DataModel()
    private val mControls = Controls()

    private val drPlay:Drawable
    private val drPause:Drawable

    // Sliderの操作
    private var pausingOnTracking = false       // スライダー操作中は再生を止めておいて、操作が終わったときに必要に応じて再生を再開する
    private lateinit var mPlayer:IAmvVideoPlayer
    private var mFrameExtractor :AmvFrameExtractor? = null
    private var mClipping : IAmvVideoPlayer.Clipping? = null

    private val mHandler = Handler()
    private var mHandlingKnob:AmvSlider.Knob = AmvSlider.Knob.NONE

    init {
        LayoutInflater.from(context).inflate(R.layout.video_trimming_controller, this)
        drPlay = context.getDrawable(R.drawable.ic_play)
        drPause = context.getDrawable(R.drawable.ic_pause)

        mControls.initialize()
    }

    // endregion

    // region Bindings

    // Views
    inner class Controls {
        // Controls
        val slider: AmvSlider by lazy {
            findViewById<AmvSlider>(R.id.slider)
        }
        val frameList: AmvFrameListView by lazy {
            findViewById<AmvFrameListView>(R.id.frameList)
        }
        val playButton: ImageButton by lazy {
            findViewById<ImageButton>(R.id.playButton)
        }

        fun onUpdatePlayerState(state:IAmvVideoPlayer.PlayerState) {
            when(state) {
            // Playボタンの状態を更新
                IAmvVideoPlayer.PlayerState.Paused-> {
                    playButton.alpha = 1f
                    playButton.setImageDrawable(drPlay)
                    playButton.isClickable = true
                }
                IAmvVideoPlayer.PlayerState.Playing-> {
                    playButton.alpha = 1f
                    playButton.setImageDrawable(drPause)
                    playButton.isClickable = true
                }
                else -> {
                    playButton.alpha = 0.4f
                    playButton.isClickable = false
                }
            }
        }

        fun initialize() {
            playButton.setOnClickListener {
                when(mPlayer.playerState) {
                    IAmvVideoPlayer.PlayerState.Paused -> {
                        mPlayer.clip(trimmingRange)
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
                mPlayer.clip(null)
                sliderPositionChanged(position, state, AmvSlider.Knob.LEFT)
                mControls.frameList.trimStart = position
            }
            slider.trimEndPositionChanged.set { _, position, state ->
                mPlayer.clip(null)
                sliderPositionChanged(position, state, AmvSlider.Knob.RIGHT)
                mControls.frameList.trimEnd = position
            }
        }

        fun resetWithDuration(duration:Long) {
            slider.resetWithValueRange(duration, true)      // スライダーを初期化
            frameList.totalRange = duration
        }
    }

    // Models
    inner class DataModel {
        var playerState:IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None
            set(v) {
                field = v
                mControls.onUpdatePlayerState(v)
            }
        var playerWidth: Int = 0

        var naturalDuration: Long = 0

        var isPrepared: Boolean = false

        val isPlaying: Boolean
            get() = playerState == IAmvVideoPlayer.PlayerState.Playing
    }

    // endregion

    // region IAmvVideoController implements

    override var isReadOnly: Boolean = true

    override fun setVideoPlayer(player: IAmvVideoPlayer) {
        mPlayer = player
        mDataModel.playerState = player.playerState

        // Player Event
        // 再生状態が変化したときのイベント
        mPlayer.playerStateChangedListener.add(listenerName) { _, state ->
            if (!pausingOnTracking) {
                mDataModel.playerState = state
            }
            if(state == IAmvVideoPlayer.PlayerState.Playing) {
                mHandler.post(mSliderSeekerOnPlaying)
            }
        }

        // 動画の画面サイズが変わったときのイベント
        mPlayer.sizeChangedListener.add(listenerName) { _, width, _ ->
            // layout_widthをBindingすると、どうしてもエラーになるので、直接変更
            mDataModel.playerWidth = width
        }

        // プレーヤー上のビデオの読み込みが完了したときのイベント
        mPlayer.videoPreparedListener.add(listenerName) { _, duration ->
            mDataModel.naturalDuration = duration
            mDataModel.isPrepared = true
            mControls.resetWithDuration(duration)
            tryRestoreState()
        }

            // 動画ソースが変更されたときのイベント
        mPlayer.sourceChangedListener.add(listenerName) { _, source ->
            savedData = null    // 誤って古い情報をリストアしないように。

            // フレームサムネイルを列挙する
            mFrameExtractor = AmvFrameExtractor().apply {
                setSizingHint(FitMode.Height, 0f, cFrameHeight)
                onVideoInfoRetrievedListener.add(null) {
                    UtLogger.debug("AmvFrameExtractor:duration=${it.duration} / ${it.videoSize}")
                    val thumbnailSize = it.thumbnailSize
                    mControls.frameList.prepare(cFrameCount, thumbnailSize.width, thumbnailSize.height)
                }
                onThumbnailRetrievedListener.add(null) { _, index, bmp ->
                    UtLogger.debug("AmvFrameExtractor:Bitmap(${index+1}): width=${bmp.width}, height=${bmp.height}")
                    mControls.frameList.add(bmp)
                }
                onFinishedListener.add(null) { _, _ ->
                    post {
                        // リスナーの中でdispose()を呼ぶのはいかがなものかと思われるので、次のタイミングでお願いする
                        dispose()
                        mFrameExtractor = null
                    }
                }
                extract(source, cFrameCount)
            }
        }

        mPlayer.clipChangedListener.add(listenerName) { _, clipping ->
            mClipping = clipping
        }
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
            if(mDataModel.playerState==IAmvVideoPlayer.PlayerState.Playing) {
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
            AmvSlider.Knob.LEFT, AmvSlider.Knob.NONE-> mControls.slider.currentPosition = pos
            AmvSlider.Knob.RIGHT->mControls.slider.currentPosition = mControls.slider.trimStartPosition
            else -> {}
        }
        mControls.frameList.position = pos
    }

    /**
     * スライダーのノブ位置変更イベントリスナーの処理
     *
     * @param position      シーク位置
     * @param dragState     BEGIN/MOVING/END
     * @param knob          変更のあったノブ種別
     */
    private fun sliderPositionChanged(position:Long, dragState: AmvSlider.SliderDragState, knob:AmvSlider.Knob) {
        UtLogger.debug("CurrentPosition: $position ($dragState)")
        when(dragState) {
            AmvSlider.SliderDragState.BEGIN-> {
                mHandlingKnob = knob
                pausingOnTracking = knob == AmvSlider.Knob.THUMB && mDataModel.isPlaying
                mPlayer.pause()
                mPlayer.setFastSeekMode(true)
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
            }
            else -> {

            }
        }
        //mBindingParams.updateCounterText(position)
    }

    // endregion

    // region Saving States

    data class SavedData(val seekPosition:Long, val isPlaying:Boolean, val trimStart:Long, val trimEnd:Long)

    private var savedData: SavedData? = null

    private fun tryRestoreState() {
//        if(mDataModel.isPrepared) {
//            savedData?.apply {
//                savedData = null
//                updateSeekPosition(seekPosition, true, true)
//                if (isPlaying) {
//                    mPlayer.play()
//                }
//                mBinding.markerView.markers = markers
//            }
//        }
    }

    // endregion

}