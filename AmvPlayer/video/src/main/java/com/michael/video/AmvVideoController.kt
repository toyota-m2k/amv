package com.michael.video

import android.content.Context
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.BindingAdapter
import android.databinding.DataBindingUtil
import android.os.Handler
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import com.michael.utils.UtLogger
import com.michael.video.databinding.VideoControllerBinding


class AmvVideoController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : ConstraintLayout(context,attrs,defStyleAttr), IAmvVideoController {

    // Constants
    private val cFrameCount = 10            // フレームサムネイルの数
    private val cFrameHeight = 160f         // フレームサムネイルの高さ(dp)

    companion object {
        @JvmStatic
        @BindingAdapter("srcCompat")
        fun srcCompat(view: ImageButton, resourceId: Int) {
            view.setImageResource(resourceId)
        }


//        @JvmStatic
//        @BindingAdapter("android:layout_width")
//        fun setLayoutWidth(view: View, width:Int) {
//            val params = view.layoutParams
//            params.width = width
//            view.layoutParams = params
//        }

    }

    private var mBinding : VideoControllerBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.video_controller, this, true)
    private val mBindingParams = BindingParams()
    private lateinit var mPlayer:IAmvVideoPlayer
    private val mHandler = Handler()
    private var mFrameExtractor :AmvFrameExtractor? = null
    private var mDuration = 0L

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

        val hasPrev : Boolean = false

        val hasNext : Boolean = true

        @get:Bindable
        var showingFrames:Boolean = true
            set(v) {
                if(field!=v) {
                    field = v
                    notifyPropertyChanged(BR.showingFrames)
                }
            }

        @get:Bindable
        val minControllerWidth : Int = 325

//        @get:Bindable
//        var controllerWidth : Int = minControllerWidth
//            set(v) {
//                val w = if(v<minControllerWidth) minControllerWidth else v
//                if(field != w) {
//                    field = w
//                    notifyPropertyChanged(BR.controllerWidth)
//                }
//            }

        var playerState:IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None
            set(v) {
                if(field != v) {
                    field = v
                    notifyPropertyChanged(BR.playing)
                    notifyPropertyChanged(BR.ready)
                    UtLogger.debug("PlayState: $v")
                    if(v==IAmvVideoPlayer.PlayerState.Playing) {
                        val startPos = mPlayer.seekPosition
                        UtLogger.debug("Start Playing --> Seek($startPos)")
                        mHandler.post (object : Runnable {
                            override fun run() {
                                if(!pausingOnTracking) {
                                    val seek = mPlayer.seekPosition
                                    if(seek>=seekTarget) {
                                        seekTarget = 0
                                        val pos = seekPos2SliderPos(seek)
                                        UtLogger.debug("Playing Pos --> Slider ($pos), Seek($seek)")
                                        mBinding.slider.currentPosition = pos
                                        mBinding.frameList.position = pos
                                    }
                                }
                                if(playerState==IAmvVideoPlayer.PlayerState.Playing) {
                                    mHandler.postDelayed(this, 200)     // Win版は10msで動かしていたが、Androidでは動画が動かなくなるので、200msくらいにしておく。ガタガタなるけど。
                                }
                            }
                        })
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
            var current = AmvTimeSpan(if(pos>mDuration) mDuration else pos)


            counterText =
                if (total.hours > 0) {
                    String.format("%02d:%02d:%02d / %02d:%02d:%02d", current.hours, current.minutes, current.seconds, total.hours, total.minutes, total.seconds)
                } else if (total.minutes > 0) {
                    String.format("%02d:%02d / %02d:%02d", current.minutes, current.seconds, total.minutes, total.seconds)
                } else {
                    String.format("%02d.%02d / %02d.%02d", current.seconds, current.milliseconds/10, total.seconds, total.milliseconds/ 10)
                }
        }
    }

    init {
        mBinding.handlers = this
        mBinding.params = mBindingParams
        isSaveFromParentEnabled = false         // このビューの状態は、IAmvPlayerView からのイベントによって復元される

        // フレーム一覧のドラッグ操作をSliderのドラッグ操作と同様に扱うための小さな仕掛け
        mBinding.frameList.touchFriendListener.set(mBinding.slider::onTouchAtFriend)

//        mBinding.backButton.isEnabled = false
//        mBinding.forwardButton.isEnabled = true
//        mBinding.backButton
    }

    val listenerName = "videoController"

    override fun setVideoPlayer(player:IAmvVideoPlayer) {
        mPlayer = player
        mBindingParams.playerState = player.playerState

        // Player Event
        mPlayer.apply {
            // 再生状態が変化したときのイベント
            playerStateChangedListener.add(listenerName) { _, state ->
                mBindingParams.playerState = state
            }

            // 動画の画面サイズが変わったときのイベント
            sizeChangedListener.add(listenerName) { _, width, _ ->
                // layout_widthをBindingすると、どうしてもエラーになるので、直接変更
                mBinding.controllerRoot.setLayoutWidth(width)
                mBinding.frameList.setLayoutWidth(width)
            }

            // プレーヤー上のビデオの読み込みが完了したときのイベント
            videoPreparedListener.add(listenerName) { mp, duration ->
                mDuration = duration
                mBindingParams.prevPosition = -1
                mBindingParams.updateCounterText(mp.seekPosition)
                mBinding.slider.resetWithValueRange(duration, true)      // スライダーを初期化
                mBinding.frameList.resetWithTotalRange(duration)
            }
            // 動画ソースが変更されたときのイベント
            sourceChangedListener.add(listenerName) { _, source ->

                // フレームサムネイルを列挙する
                mFrameExtractor = AmvFrameExtractor().apply {
                    setSizingHint(FitMode.Height, 0f, cFrameHeight)
                    onVideoInfoRetrievedListener.add(null) {
                        UtLogger.debug("AmvFrameExtractor:duration=${it.duration} / ${it.videoSize}")
                        val thumbnailSize = it.thumbnailSize
                        mBinding.frameList.prepare(cFrameCount, thumbnailSize.width, thumbnailSize.height)
                    }
                    onThumbnailRetrievedListener.add(null) { _, index, bmp ->
                        UtLogger.debug("AmvFrameExtractor:Bitmap($index): width=${bmp.width}, height=${bmp.height}")
                        mBinding.frameList.add(bmp)
                    }
                    onFinishedListener.add(null) { _, _ ->
                        mHandler.post {
                            // リスナーの中でdispose()を呼ぶのはいかがなものかと思われるので、次のタイミングでお願いする
                            dispose()
                            mFrameExtractor = null
                        }
                    }
                    extract(source, cFrameCount)
                }
            }
            seekCompletedListener.add(listenerName) { _, pos ->
                if(!mBinding.slider.isDragging) {
                    mBinding.slider.currentPosition = pos
                    mBinding.frameList.position = pos
                }
            }
        }
    }

    override var isReadOnly: Boolean
        get() = mBindingParams.isReadOnly
        set(v) { mBindingParams.isReadOnly=v }

    @Suppress("UNUSED_PARAMETER")
    fun onPlayClicked(view: View) {
        when(mPlayer.playerState) {
            IAmvVideoPlayer.PlayerState.Paused -> mPlayer.play()
            IAmvVideoPlayer.PlayerState.Playing -> mPlayer.pause()
            else -> {}
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onShowFramesClick(view:View) {
        mBindingParams.showingFrames = !mBindingParams.showingFrames
        if(!mBindingParams.showingFrames) {
            mBinding.frameList.visibility = View.GONE
            mBinding.slider.showThumbBg = false
        } else {
            mBinding.frameList.visibility = View.VISIBLE
            mBinding.slider.showThumbBg = true
        }

        // XML で、android:selected という属性は警告(Unknown attribute)がでるが、ちゃんとバインドできているという謎。
        // mBinding.showFramesButton.isSelected = mBindingParams.showingFrames
    }

    @Suppress("unused")
    val seekPosition : Long
        get() = mPlayer.seekPosition

    private fun sliderPos2SeekPos(sliderPos:Long) : Long {
        return sliderPos
    }

    private fun seekPos2SliderPos(seekPos:Long) : Long {
        return seekPos
    }

    // Sliderの操作
    private var pausingOnTracking = false       // スライダー操作中は再生を止めておいて、操作が終わったときに必要に応じて再生を再開する
    private var seekTarget : Long = 0            // seekTo()が成功しても、再生を開始すると、何やら3～5秒くらい戻ることがあるので、ターゲット位置を覚えておいて、それ以前に戻る動作を見せないようにしてみる

//    /**
//     * Sliderの値が変化した
//     */
//    @Suppress("UNUSED_PARAMETER")
//    fun onSeekBarValueChanged(seekBar: SeekBar, progressValue: Int, fromUser: Boolean) {
//        if(fromUser) {
//            mBinding.frameList.position = progressValue
//            seekTarget = sliderPos2SeekPos(progressValue)
//            mPlayer.seekTo(seekTarget)
//            UtLogger.debug("Tracking - Pos = Slider=$progressValue, Seek=${sliderPos2SeekPos(progressValue)}")
//        }
//    }
//
//    /**
//     * Sliderのトラッキングが開始される
//     */
//    @Suppress("UNUSED_PARAMETER")
//    fun onSeekBarStartTracking(bar:SeekBar) {
//        pausingOnTracking = mBindingParams.isPlaying
//        UtLogger.debug("Tracking - Start (playing = $pausingOnTracking) --> pausing")
//        mPlayer.pause()
//    }
//
//    /**
//     * Sliderのトラッキングが終了する
//     */
//    @Suppress("UNUSED_PARAMETER")
//    fun onSeekBarEndTracking(bar:SeekBar) {
//        UtLogger.debug("Tracking - End (restore playing = $pausingOnTracking) <-- pausing")
//        if(pausingOnTracking) {
//            mPlayer.play()
//            pausingOnTracking = false
//        }
//    }

    fun onCurrentPositionChanged(@Suppress("UNUSED_PARAMETER") caller:AmvSlider, position:Long, dragState: AmvSlider.SliderDragState) {
        UtLogger.debug("CurrentPosition: $position ($dragState)")
        when(dragState) {
            AmvSlider.SliderDragState.BEGIN-> {
                pausingOnTracking = mBindingParams.isPlaying
                mPlayer.pause()
                mPlayer.setFastSeekMode(true)
            }
            AmvSlider.SliderDragState.MOVING->{
                mBinding.frameList.position = position
                seekTarget = sliderPos2SeekPos(position)
                mPlayer.seekTo(seekTarget)
            }
            AmvSlider.SliderDragState.END-> {
                mBinding.frameList.position = position
                seekTarget = sliderPos2SeekPos(position)
                mPlayer.setFastSeekMode(false)
                mPlayer.seekTo(seekTarget)

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
}