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
import com.michael.video.databinding.VideoControllerBinding
import android.widget.SeekBar
import com.michael.utils.UtLogger


class AmvVideoController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : ConstraintLayout(context,attrs,defStyleAttr), IAmvVideoController {

    companion object {
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

        var playerState:IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None
            set(v) {
                if(field != v) {
                    field = v
                    notifyPropertyChanged(BR.playing)
                    notifyPropertyChanged(BR.ready)
                    UtLogger.debug("PlayState: $v")
                    if(v==IAmvVideoPlayer.PlayerState.Playing) {
                        val startPos = mPlayer.seekPosition;
                        UtLogger.debug("Start Playing --> Seek($startPos)")
                        mHandler.post (object : Runnable {
                            override fun run() {
                                if(!pausingOnTracking) {
                                    val seek = mPlayer.seekPosition
                                    if(seek>=seekTarget) {
                                        seekTarget = 0
                                        val pos = seekPos2SliderPos(seek);
                                        UtLogger.debug("Playing Pos --> Slider ($pos), Seek($seek)")
                                        mBinding.slider.progress = pos
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

    }

    init {
        mBinding.handlers = this
        mBinding.params = mBindingParams
//        mBinding.backButton.isEnabled = false
//        mBinding.forwardButton.isEnabled = true
//        mBinding.backButton
    }

    override fun setVideoPlayer(player:IAmvVideoPlayer) {
        mPlayer = player
        mBindingParams.playerState = player.playerState
        mPlayer.playerStateChangedListener.add("videoController") { _, state ->
            mBindingParams.playerState = state
        }
    }

    override var isReadOnly: Boolean
        get() = mBindingParams.isReadOnly
        set(v) { mBindingParams.isReadOnly=v}

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

        // XML で、android:selected という属性は警告(Unknown attribute)がでるが、ちゃんとバインドできているという謎。
        // mBinding.showFramesButton.isSelected = mBindingParams.showingFrames
    }

    val seekPosition : Int
        get() = mPlayer.seekPosition

    fun sliderPos2SeekPos(sliderPos:Int) : Int {
        return (mPlayer.naturalDuration * sliderPos)/1000
    }

    fun seekPos2SliderPos(seekPos:Int) : Int {
        val nd = mPlayer.naturalDuration;
        return if(nd>0) (1000 * seekPos) / nd else 0
    }

    // Sliderの操作
    private var pausingOnTracking = false       // スライダー操作中は再生を止めておいて、操作が終わったときに必要に応じて再生を再開する
    private var seekTarget : Int = 0            // seekTo()が成功しても、再生を開始すると、何やら3～5秒くらい戻ることがあるので、ターゲット位置を覚えておいて、それ以前に戻る動作を見せないようにしてみる

    /**
     * Sliderの値が変化した
     */
    fun onSeekBarValueChanged(seekBar: SeekBar, progresValue: Int, fromUser: Boolean) {
        if(fromUser) {
            seekTarget = sliderPos2SeekPos(progresValue)
            mPlayer.seekTo(seekTarget)
            UtLogger.debug("Tracking - Pos = Slider=$progresValue, Seek=${sliderPos2SeekPos(progresValue)}")
        }
    }

    /**
     * Sliderのトラッキングが開始される
     */
    fun onSeekBarStartTracking(bar:SeekBar) {
        pausingOnTracking = mBindingParams.isPlaying
        UtLogger.debug("Tracking - Start (playing = $pausingOnTracking) --> pausing")
        mPlayer.pause()
    }

    /**
     * Sliderのトラッキングが終了する
     */
    fun onSeekBarEndTracking(bar:SeekBar) {
        UtLogger.debug("Tracking - End (restore playing = $pausingOnTracking) <-- pausing")
        if(pausingOnTracking) {
            mPlayer.play()
            pausingOnTracking = false
        }
    }
}