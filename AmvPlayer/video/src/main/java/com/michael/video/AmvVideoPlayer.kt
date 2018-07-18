package com.michael.video

import android.content.Context
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.DataBindingUtil
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
//import android.util.SizeF
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.VideoView
import com.michael.utils.UtLogger
import com.michael.video.databinding.VideoPlayerBinding
import org.parceler.Parcels
import java.io.File

class AmvVideoPlayer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr), IAmvVideoPlayer {

    // Event Listeners

    /**
     * プレーヤーの状態が変化したことを通知するイベント
     */
    override val sourceChangedListener = IAmvVideoPlayer.SourceChangedListener()

    override val videoPreparedListener = IAmvVideoPlayer.VideoPreparedListener()

    override val sizeChangedListener = IAmvVideoPlayer.SizeChangedListener()

    override val playerStateChangedListener = IAmvVideoPlayer.PlayerStateChangedListener()

    override val seekCompletedListener= IAmvVideoPlayer.SeekCompletedListener()


    // Public Properties
    val videoView : VideoView
        get() = mBinding.player

    // Private Properties
    private var mMediaPlayer: MediaPlayer? = null
    private val mVideoSize = MuSize(100f, 100f)                  // 動画のNatural Size
    private val mPlayerSize = MuSize(0f,0f)                      // VideoView のサイズ
    private val mFitter = AmvFitter()
    private var mBinding : VideoPlayerBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.video_player, this, true)
    private val mBindingParams = BindingParams()
    private var mAutoPlay = false
    private var mSource : File? = null

    /**
     * Binding Data
     */
    inner class BindingParams : BaseObservable() {
        private var mPlayerState = IAmvVideoPlayer.PlayerState.None

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
        var errorMessage: String = ""
            set(v) {
                if(field != v) {
                    field = v
                    notifyPropertyChanged(BR.errorMessage)
                }
            }

        @Suppress("MemberVisibilityCanBePrivate")
        val playerWidth: Float
            get() = mPlayerSize.width

        @Suppress("MemberVisibilityCanBePrivate")
        val playerHeight: Float
            get() =  mPlayerSize.height

        fun updateLayout() {
            val lp = videoView.layoutParams
            lp.height = Math.round(playerHeight)
            lp.width = Math.round(playerWidth)
            videoView.layoutParams = lp
        }

//        fun updateStatus() {
//            notifyPropertyChanged(BR.ready)
//            notifyPropertyChanged(BR.hasError)
//        }

        var playerState : IAmvVideoPlayer.PlayerState
            get() = mPlayerState
            set(state) {
                if (state != mPlayerState) {
                    mPlayerState = state
                    notifyPropertyChanged(BR.ready)
                    notifyPropertyChanged(BR.loading)
                    notifyPropertyChanged(BR.error)
                    playerStateChangedListener.invoke(this@AmvVideoPlayer, state)
                }
            }

    }

    override val playerState : IAmvVideoPlayer.PlayerState
        get() = mBindingParams.playerState


    override var naturalDuration: Long = 0

    override val seekPosition: Long
        get() = mBinding.player.currentPosition.toLong()

    // Construction

    init {
        // val v = LayoutInflater.from(context).inflate(R.layout.video_player,this)
        mBinding.player.apply {
            setOnPreparedListener {  mp ->
                // 動画がロードされた
                if(null==mMediaPlayer) {
                    // Activityが切り替わって、Activityが破棄される前に戻ってきた場合、同じ動画に対して、preparedがもう一度回呼ばれるので、チェックする
                    mMediaPlayer = mp
                    naturalDuration = mp.duration.toLong()
                    videoPreparedListener.invoke(this@AmvVideoPlayer, naturalDuration.toLong())
                }

                mVideoSize.height = mp.videoHeight.toFloat()
                mVideoSize.width = mp.videoWidth.toFloat()
                fitSize()

                mp.setOnSeekCompleteListener {
                    val pos = it.currentPosition
                    UtLogger.debug("SeekCompleted: $pos")
                    seekCompletedListener.invoke(this@AmvVideoPlayer, pos.toLong())
                }

                mBindingParams.errorMessage = ""
                mBindingParams.playerState = IAmvVideoPlayer.PlayerState.Paused


                if(mAutoPlay) {
                    Handler().postDelayed( {
                        this.start()        // VideoView#start()
                        mBindingParams.playerState = IAmvVideoPlayer.PlayerState.Playing
                    }, 100)
                }
            }
            setOnCompletionListener {
                // 動画の最後まで再生が終わった
                Log.d("Amv", "MediaPlayer Completed.")
                mBindingParams.playerState = IAmvVideoPlayer.PlayerState.Paused
            }
            setOnErrorListener { _, what, extra ->
                // 動画ロード中、または、再生中にエラーが発生した
                // --> エラー状態にして、これ以降、再生などの操作を禁止する
                Log.d("Amv", "MediaPlayer Error: what=$what, extra=$extra")
//                if(mBindingParams.isLoading) {
//                    mBindingParams.playerState = IAmvVideoPlayer.PlayerState.None
//                }
                mBindingParams.errorMessage = when(what) {
                    MEDIA_ERROR_UNKNOWN -> "Unknown error."
                    MEDIA_ERROR_SERVER_DIED -> "Server error ($extra)."
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
            setOnInfoListener { _, what, extra ->
                Log.d("Amv", "MediaPlayer Info: what=$what, extra=$extra")
                false
            }
        }
    }

    // IAmvVideoPlayer i/f

    override fun setLayoutHint(mode: FitMode, width:Float, height:Float) {
        mFitter.setHint(mode, width, height)
    }


    override fun reset(state: IAmvVideoPlayer.PlayerState) {
        mMediaPlayer?.stop()
        mMediaPlayer = null
        mBindingParams.errorMessage = ""
        mBindingParams.playerState = state
    }

    private fun setSource(source: File, autoPlay:Boolean, playFrom:Long, resetBeforeLoad:Boolean) {
        if(resetBeforeLoad) {
            reset(IAmvVideoPlayer.PlayerState.Loading)
        }
        mSource = source
        mAutoPlay = autoPlay
        videoView.setVideoPath(source.path)
        if(playFrom>0) {
            seekTo(playFrom)
        }
        sourceChangedListener.invoke(this, source)
    }

    override fun setSource(source: File, autoPlay:Boolean, playFrom:Long) {
        setSource(source, autoPlay, playFrom, true)
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

    override fun seekTo(pos:Long) {
        videoView.seekTo(pos.toInt())
    }

    // Privates

    private fun fitSize() {
        mFitter.fit(mVideoSize, mPlayerSize)
        mBindingParams.updateLayout()
    }

    // View implementation

    /**
     * Windowがビューツリーから切り離される≒ビューが死ぬ（？）
     */
    override fun onDetachedFromWindow() {
        UtLogger.debug("LC-View: onDetachedFromWindow")
        super.onDetachedFromWindow()
        playerStateChangedListener.clear()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sizeChangedListener.invoke(this, w, h)
    }

    override fun onSaveInstanceState(): Parcelable {
        UtLogger.debug("LC-View: onSaveInstanceState")
        val parent =  super.onSaveInstanceState()
        return SavedState(parent).apply {
            data = SavedState.SavingData(mSource, mBindingParams.playerState==IAmvVideoPlayer.PlayerState.Playing, seekPosition)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        UtLogger.debug("LC-View: onRestoreInstanceState")
        if(state is SavedState) {
            state.apply {
                super.onRestoreInstanceState(superState)
                val d = data
                val source = data?.source
                if(d!=null && null!=source) {
                    setSource(source, d.isPlaying, d.seekPosition, false)
                }
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }


    internal class SavedState : View.BaseSavedState {

        @org.parceler.Parcel
        data class SavingData(
                var source:File?,
                var isPlaying:Boolean,
                var seekPosition:Long
        ) {
            constructor() : this(null,false,0)
        }

        var data : SavingData? = null

        /**
         * Constructor called from [AmvSlider.onSaveInstanceState]
         */
        constructor(superState: Parcelable) : super(superState)

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(parcel: Parcel) : super(parcel) {
            val sd = parcel.readParcelable<Parcelable>(Parcelable::class.java.classLoader)
            if(null!=sd) {
                data = Parcels.unwrap(sd)
            }

//            totalLength = parcel.readLong()
//            trimStartPosition = parcel.readLong()
//            trimEndPosition = parcel.readLong()
//            currentPosition = parcel.readLong()
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            super.writeToParcel(parcel, flags)

            val pc = Parcels.wrap(data)
            parcel.writeParcelable(pc,0)

//            parcel.writeLong(totalLength)
//            parcel.writeLong(trimStartPosition)
//            parcel.writeLong(trimEndPosition)
//            parcel.writeLong(currentPosition)
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