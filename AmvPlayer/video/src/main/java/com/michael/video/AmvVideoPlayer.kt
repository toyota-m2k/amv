package com.michael.video

import android.content.Context
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.DataBindingUtil
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.os.Handler
import android.renderscript.ScriptGroup
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.VideoView
import com.michael.video.databinding.VideoPlayerBinding
import kotlinx.android.synthetic.main.video_player.view.*
import java.io.File


class AmvVideoPlayer @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class LayoutMode {
        Width,       // 指定された幅になるように高さを調整
        Height,      // 指定された高さになるよう幅を調整
        Inside,      // 指定された矩形に収まるよう幅、または、高さを調整
        Fit          // 指定された矩形にリサイズする
    }

    enum class PlayerState {
        None,       // 何もしていない
        Loading,
        Playing,
        Paused
    }

    data class Size (var width:Float=0f, var height:Float=0f) {
        fun copyFrom(s:Size) {
            width = s.width
            height = s.height
        }
        fun set(width:Float, height:Float) {
            this.width = width
            this.height = height
        }
    }

    private var mMediaPlayer: MediaPlayer? = null
    private val mVideoSize : Size = Size(100f, 100f)                  // 動画のNatural Size
    private val mLayoutSize : Size = Size(100f, 100f)                 // Layout Hint
    private var mLayoutMode : LayoutMode = LayoutMode.Fit
    private val mPlayerSize : Size = Size(0f,0f)
    private lateinit var mBinding : VideoPlayerBinding
    private val mBindingParams = BindingParams()

    private var mAutoPlay : Boolean = false

    inner class BindingParams : BaseObservable() {
        private var mPlayerState = PlayerState.None
        private var mErrorMessage : String? = null

        @get:Bindable
        val isReady: Boolean
            get() {
                return when(mPlayerState) { PlayerState.Paused, PlayerState.Playing -> true else -> false }
            }

        @get:Bindable
        val isLoading: Boolean
            get() {
                return mPlayerState == PlayerState.Loading
            }


        @get:Bindable
        val hasError: Boolean
            get() {
                return !isReady && mErrorMessage!=null
            }

        @get:Bindable
        val errorMessage: String
            get() {
                val m = mErrorMessage;
                return if (null != m) m else ""
            }

        val playerWidth: Float
            get() = mPlayerSize.width

        val playerHeight: Float
            get() =  mPlayerSize.height

        fun updateLayout() {
            val lp = videoView.layoutParams
            lp.height = Math.round(playerHeight)
            lp.width = Math.round(playerWidth)
            videoView.layoutParams = lp;
        }

//        fun updateStatus() {
//            notifyPropertyChanged(BR.ready)
//            notifyPropertyChanged(BR.hasError)
//        }

        var playerState : PlayerState
            get() = mPlayerState
            set(state) {
                if (state != mPlayerState) {
                    mPlayerState = state;
                    notifyPropertyChanged(BR.ready)
                    notifyPropertyChanged(BR.loading)
                    notifyPropertyChanged(BR.hasError)
                }
            }

        var error :String?
            get() = mErrorMessage
            set(message) {
                mErrorMessage = message
                notifyPropertyChanged(BR.hasError)
                notifyPropertyChanged(BR.errorMessage)
            }
    }

    init {
        mBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.video_player, this, true)
        // val v = LayoutInflater.from(context).inflate(R.layout.video_player,this)
        mBinding.player.apply {
            setOnPreparedListener {  mp ->
                // 動画がロードされた
                mMediaPlayer = mp
                mVideoSize.height = mp.videoHeight.toFloat()
                mVideoSize.width = mp.videoWidth.toFloat()
                fitSize()

                mBindingParams.error = null
                mBindingParams.playerState = PlayerState.Paused

                if(mAutoPlay) {
                    Handler().postDelayed( Runnable {
                        start()
                    }, 100)
                }
            }
            setOnCompletionListener {  mp ->
                // 動画の最後まで再生が終わった
                Log.d("Amv", "MediaPlayer Completed.")
                mBindingParams.playerState = PlayerState.Paused
            }
            setOnErrorListener { mp, what, extra ->
                Log.d("Amv", "MediaPlayer Error: what=${what}, extra=${extra}")
                if(mBindingParams.isLoading) {
                    mBindingParams.playerState = PlayerState.None
                }
                mBindingParams.error = when(what) {
                    MEDIA_ERROR_UNKNOWN -> "Unknown error."
                    MEDIA_ERROR_SERVER_DIED -> "Server error (${extra})."
                    MEDIA_ERROR_IO -> "IO Error."
                    MEDIA_ERROR_MALFORMED->"Malformed."
                    MEDIA_ERROR_UNSUPPORTED->"Unsupported."
                    MEDIA_ERROR_TIMED_OUT->"Timeout."
                    -2147483648->"Low-level system error."      // MEDIA_ERROR_SYSTEM と書いたら、なぜかunresolved になってしまう
                    else -> "error."
                }
                true    // falseを返すと OnCOmpletionが呼ばれる
            }
            setOnInfoListener { mp, what, extra ->
                Log.d("Amv", "MediaPlayer Info: what=${what}, extra=${extra}")
                false
            }
        }
    }

    val videoView : VideoView
        get() = mBinding.player

    fun setLayoutHint(mode:LayoutMode, width:Float, height:Float) {
        mLayoutMode = mode
        mLayoutSize.width = width
        mLayoutSize.height = height
    }


    fun reset(state:PlayerState=PlayerState.None) {
        mMediaPlayer?.stop()
        mMediaPlayer = null
        mBindingParams.error = null
        mBindingParams.playerState = state
    }

    fun setSource(source: File) {
        reset(PlayerState.Loading)
        videoView.setVideoPath(source.path)
    }

    fun play() {
        if(mBindingParams.isReady) {
            videoView.start()
            mBindingParams.playerState = PlayerState.Playing
        }
    }

//    fun stop() {
//        if(mBindingParams.isReady) {
//            videoView.stopPlayback()
//            mBindingParams.playerState = PlayerState.Paused
//        }
//    }

    fun pause() {
        if(mBindingParams.isReady) {
            videoView.pause()
            mBindingParams.playerState = PlayerState.Paused
        }
    }

    fun seekTo(pos:Int) {
        videoView.seekTo(pos)
    }

    private fun fitSize() {
        try {
            when (mLayoutMode) {
                LayoutMode.Fit -> mPlayerSize.copyFrom(mLayoutSize)
                LayoutMode.Width -> mPlayerSize.set(mLayoutSize.width, mVideoSize.height * mLayoutSize.width / mVideoSize.width)
                LayoutMode.Height -> mPlayerSize.set(mVideoSize.width * mLayoutSize.height / mVideoSize.height, mLayoutSize.height)
                LayoutMode.Inside -> {
                    val rw = mLayoutSize.width / mVideoSize.width
                    val rh = mLayoutSize.height / mVideoSize.height
                    if (rw < rh) {
                        mPlayerSize.set(mLayoutSize.width, mVideoSize.height * rw)
                    } else {
                        mPlayerSize.set(mVideoSize.width * rh, mLayoutSize.height)
                    }
                }
                else -> {
                    val rw = mLayoutSize.width / mVideoSize.width
                    val rh = mLayoutSize.height / mVideoSize.height
                    if (rw < rh) {
                        mPlayerSize.set(mLayoutSize.width, mVideoSize.height * rw)
                    } else {
                        mPlayerSize.set(mVideoSize.width * rh, mLayoutSize.height)
                    }
                }
            }
        } catch(e:Exception) {
            mPlayerSize.set(0f,0f)
        }
        mBindingParams.updateLayout()
    }
}