package com.michael.video

import android.content.Context
import android.graphics.drawable.Drawable
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageButton
import com.michael.utils.UtLogger

data class TrimmingRange(val start:Long, val end:Long)

class AmvTrimmingController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : ConstraintLayout(context,attrs,defStyleAttr), IAmvVideoController {

    override var isReadOnly: Boolean = true

    private val cFrameCount = 10            // フレームサムネイルの数
    private val cFrameHeight = 160f         // フレームサムネイルの高さ(dp)
    private val mDataModel = DataModel()
    private val mControls = Controls()
    private val listenerName = "trimmingController"
    private val drPlay:Drawable
    private val drPause:Drawable

    // Sliderの操作
    private var pausingOnTracking = false       // スライダー操作中は再生を止めておいて、操作が終わったときに必要に応じて再生を再開する
    private lateinit var mPlayer:IAmvVideoPlayer
    private var mFrameExtractor :AmvFrameExtractor? = null

    // region Manual bindings

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
                    IAmvVideoPlayer.PlayerState.Paused -> mPlayer.play()
                    IAmvVideoPlayer.PlayerState.Playing -> mPlayer.pause()
                    else -> {}
                }
            }

            slider.currentPositionChanged.set { _, position, state ->
                sliderPositionChanged(position, state, false)
            }
            slider.trimStartPositionChanged.set { _, position, state ->
                sliderPositionChanged(position, state, true)
            }
            slider.trimEndPositionChanged.set { _, position, state ->
                sliderPositionChanged(position, state, true)
            }
        }

        fun resetWithDuration(duration:Long) {
            slider.resetWithValueRange(duration, true)      // スライダーを初期化
            frameList.resetWithTotalRange(duration)
        }
    }

    inner class DataModel {
        var playerState:IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None
            set(v) {
                field = v
                mControls.onUpdatePlayerState(v)
            }
        var playerWidth: Int = 0
            set(v) {
                field = v
            }
        var naturalDuration: Long = 0

        var isPrepared: Boolean = false

        val isPlaying: Boolean
            get() = playerState == IAmvVideoPlayer.PlayerState.Playing
    }

    // endregion


    init {
        LayoutInflater.from(context).inflate(R.layout.video_trimming_controller, this)
        drPlay = context.getDrawable(R.drawable.ic_play)
        drPause = context.getDrawable(R.drawable.ic_pause)

        mControls.initialize()
    }

    override fun setVideoPlayer(player: IAmvVideoPlayer) {
        mPlayer = player
        mDataModel.playerState = player.playerState

        // Player Event
        // 再生状態が変化したときのイベント
        mPlayer.playerStateChangedListener.add(listenerName) { _, state ->
            if (!pausingOnTracking) {
                mDataModel.playerState = state
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
                    UtLogger.debug("AmvFrameExtractor:Bitmap($index): width=${bmp.width}, height=${bmp.height}")
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
    }

    fun updateSeekPosition(pos:Long, seek:Boolean, slider:Boolean) {
        if(seek) {
            mPlayer.seekTo(pos)
        }
        if(slider) {
            mControls.slider.currentPosition = pos
        }
        mControls.frameList.position = pos
    }

    fun sliderPositionChanged(position:Long, dragState: AmvSlider.SliderDragState, stopPlay:Boolean) {
        UtLogger.debug("CurrentPosition: $position ($dragState)")
        when(dragState) {
            AmvSlider.SliderDragState.BEGIN-> {
                pausingOnTracking = !stopPlay && mDataModel.isPlaying
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
        //mBindingParams.updateCounterText(position)
    }

    val trimmingRange:TrimmingRange
        get() = TrimmingRange(mControls.slider.trimStartPosition, mControls.slider.trimEndPosition)

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

}