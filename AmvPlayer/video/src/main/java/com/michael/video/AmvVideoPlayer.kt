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
import com.michael.utils.Funcies
import com.michael.utils.Funcies2
import com.michael.utils.UtLogger
import com.michael.video.databinding.VideoPlayerBinding
import kotlinx.android.synthetic.main.video_player.view.*
import java.io.File

class AmvVideoPlayer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), IAmvVideoPlayer {

    // Event Listeners

    /**
     * プレーヤーの状態が変化したことを通知するイベント
     */
    override val playerStateChangedListener = IAmvVideoPlayer.PlayerStateChangedListener()

    // Public Properties
    val videoView : VideoView
        get() = mBinding.player

    /**
     * MutableなSize型（内部利用専用）
     */
    private data class Size (var width:Float=0f, var height:Float=0f) {
        fun copyFrom(s:Size) {
            width = s.width
            height = s.height
        }
        fun set(width:Float, height:Float) {
            this.width = width
            this.height = height
        }
    }

    // Private Properties
    private var mMediaPlayer: MediaPlayer? = null
    private val mVideoSize : Size = Size(100f, 100f)                  // 動画のNatural Size
    private val mLayoutSize : Size = Size(100f, 100f)                 // Layout Hint
    private var mLayoutMode : IAmvVideoPlayer.LayoutMode = IAmvVideoPlayer.LayoutMode.Fit
    private val mPlayerSize : Size = Size(0f,0f)
    private var mBinding : VideoPlayerBinding
    private val mBindingParams = BindingParams()
    private var mAutoPlay : Boolean = false


    /**
     * Binding Data
     */
    inner class BindingParams : BaseObservable() {
        private var mPlayerState = IAmvVideoPlayer.PlayerState.None
        private var mErrorMessage : String? = null

        @get:Bindable
        val isReady: Boolean
            get() = when(mPlayerState) { IAmvVideoPlayer.PlayerState.Paused, IAmvVideoPlayer.PlayerState.Playing -> true else -> false }

        @get:Bindable
        val isLoading: Boolean
            get() = mPlayerState == IAmvVideoPlayer.PlayerState.Loading


        @get:Bindable
        val isError: Boolean
            get() = mPlayerState == IAmvVideoPlayer.PlayerState.Error

        @get:Bindable
        val errorMessage: String
            get() = mErrorMessage?:""

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

        var playerState : IAmvVideoPlayer.PlayerState
            get() = mPlayerState
            set(state) {
                if (state != mPlayerState) {
                    mPlayerState = state;
                    notifyPropertyChanged(BR.ready)
                    notifyPropertyChanged(BR.loading)
                    notifyPropertyChanged(BR.error)
                    playerStateChangedListener.invoke(this@AmvVideoPlayer, state)
                }
            }

        var error :String?
            get() = mErrorMessage
            set(message) {
                mErrorMessage = message
                notifyPropertyChanged(BR.errorMessage)
            }
    }

    override val playerState : IAmvVideoPlayer.PlayerState
        get() = mBindingParams.playerState

    // Construction

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
                mBindingParams.playerState = IAmvVideoPlayer.PlayerState.Paused

                if(mAutoPlay) {
                    Handler().postDelayed( Runnable {
                        start()
                    }, 100)
                }
            }
            setOnCompletionListener {  mp ->
                // 動画の最後まで再生が終わった
                Log.d("Amv", "MediaPlayer Completed.")
                mBindingParams.playerState = IAmvVideoPlayer.PlayerState.Paused
            }
            setOnErrorListener { mp, what, extra ->
                // 動画ロード中、または、再生中にエラーが発生した
                // --> エラー状態にして、これ以降、再生などの操作を禁止する
                Log.d("Amv", "MediaPlayer Error: what=${what}, extra=${extra}")
//                if(mBindingParams.isLoading) {
//                    mBindingParams.playerState = IAmvVideoPlayer.PlayerState.None
//                }
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
                mBindingParams.playerState = IAmvVideoPlayer.PlayerState.Error
                true    // falseを返すと OnCOmpletionが呼ばれる
            }
            setOnInfoListener { mp, what, extra ->
                Log.d("Amv", "MediaPlayer Info: what=${what}, extra=${extra}")
                false
            }
        }
    }

    // IAmvVideoPlayer i/f

    override fun setLayoutHint(mode: IAmvVideoPlayer.LayoutMode, width:Float, height:Float) {
        mLayoutMode = mode
        mLayoutSize.width = width
        mLayoutSize.height = height
    }


    override fun reset(state: IAmvVideoPlayer.PlayerState) {
        mMediaPlayer?.stop()
        mMediaPlayer = null
        mBindingParams.error = null
        mBindingParams.playerState = state
    }

    override fun setSource(source: File, autoPlay:Boolean) {
        reset(IAmvVideoPlayer.PlayerState.Loading)
        mAutoPlay = autoPlay
        videoView.setVideoPath(source.path)
    }

    override fun play() {
        if(mBindingParams.isReady) {
            videoView.start()
            mBindingParams.playerState = IAmvVideoPlayer.PlayerState.Playing
        }
    }

//    override fun stop() {
//        if(mBindingParams.isReady) {
//            videoView.stopPlayback()
//            mBindingParams.playerState = PlayerState.Paused
//        }
//    }

    override fun pause() {
        if(mBindingParams.isReady) {
            videoView.pause()
            mBindingParams.playerState = IAmvVideoPlayer.PlayerState.Paused
        }
    }

    override fun seekTo(pos:Int) {
        videoView.seekTo(pos)
    }

    // Privates

    private fun fitSize() {
        try {
            when (mLayoutMode) {
                IAmvVideoPlayer.LayoutMode.Fit -> mPlayerSize.copyFrom(mLayoutSize)
                IAmvVideoPlayer.LayoutMode.Width -> mPlayerSize.set(mLayoutSize.width, mVideoSize.height * mLayoutSize.width / mVideoSize.width)
                IAmvVideoPlayer.LayoutMode.Height -> mPlayerSize.set(mVideoSize.width * mLayoutSize.height / mVideoSize.height, mLayoutSize.height)
                IAmvVideoPlayer.LayoutMode.Inside -> {
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

    // View implementation

    /**
     * Windowがビューツリーから切り離される≒ビューが死ぬ（？）
     */
    override fun onDetachedFromWindow() {
        UtLogger.debug("AmvVideoPlayer ... disposed")
        super.onDetachedFromWindow()
        playerStateChangedListener.clear()
    }
}