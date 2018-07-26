package com.michael.video

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.video.VideoListener
import com.michael.utils.UtLogger
import java.io.File
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


class AmvExoVideoPlayer @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), IAmvVideoPlayer {

    // region Private Properties

    private val mBindings = Bindings()
    private var mSource : File? = null
    private var mPlayer : SimpleExoPlayer? = null
    private var mEnded : Boolean = false                // 動画ファイルの最後まで再生が終わって停止した状態から、Playボタンを押したときに、先頭から再生を開始する動作を実現するためのフラグ

    // endregion

    // region ExoPlayer - Event Handler

    private val mVideoListener = object : VideoListener {
        override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
            mBindings.setVideoSize(width.toFloat(), height.toFloat())
        }

        override fun onRenderedFirstFrame() {
        }
    }

    private val mEventListener = object : Player.EventListener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters?) {
            // speed, pitch, skipSilence, scaledUsPerMs
        }

        override fun onSeekProcessed() {
            seekCompletedListener.invoke(this@AmvExoVideoPlayer, seekPosition)
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {
        }

        override fun onPlayerError(error: ExoPlaybackException?) {
            UtLogger.error(error.toString())
            mBindings.errorMessage = error?.message ?: "error"
            mBindings.playerState = IAmvVideoPlayer.PlayerState.Error
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            UtLogger.debug("EXO: loading = $isLoading")
            if(isLoading) {
                mBindings.playerState = IAmvVideoPlayer.PlayerState.Loading
            }
        }

        override fun onPositionDiscontinuity(reason: Int) {
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        }

        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {

            val ppn = {s:Int->
                when(s) {
                    Player.STATE_IDLE -> "Idle"
                    Player.STATE_BUFFERING -> "Buffering"
                    Player.STATE_READY -> "Ready"
                    Player.STATE_ENDED -> "Ended"
                    else -> "Unknown"
                }
            }

            UtLogger.debug("EXO: status = ${ppn(playbackState)} / playWhenReady = $playWhenReady")

            when(playbackState) {
                Player.STATE_READY -> {
                    mBindings.playerState = if(playWhenReady) { IAmvVideoPlayer.PlayerState.Playing } else {IAmvVideoPlayer.PlayerState.Paused}
                }
                Player.STATE_ENDED -> {
                    mBindings.playerState = IAmvVideoPlayer.PlayerState.Paused
                    mEnded = playWhenReady       // 再生しながら動画ファイルの最後に達したことを覚えておく
                }
                else -> {}
            }
        }
    }

    // endregion

    // region Initialization / Termination

    init {
        LayoutInflater.from(context).inflate(R.layout.video_exo_player, this)
        val player  = ExoPlayerFactory.newSimpleInstance(
                                            DefaultRenderersFactory(context),
                                            DefaultTrackSelector(),
                                            DefaultLoadControl())
        mBindings.playerView.player = player
        player.addListener(mEventListener)
        player.addVideoListener(mVideoListener)
        mPlayer = player

        // タッチで再生/一時停止をトグルさせる
        this.setOnClickListener {
            if(it is AmvExoVideoPlayer) {
                it.togglePlay()
            }
        }
//        mBindings.playerView.setOnClickListener {     // ExoPlayer にクリックリスナーをセットしても無駄だった
//            togglePlay()
//        }
    }

    override fun onDetachedFromWindow() {
        val player = mPlayer
        if(null!=player) {
            mPlayer = null
            player.removeListener(mEventListener)
            player.removeVideoListener(mVideoListener)
            player.release()
        }

        super.onDetachedFromWindow()
    }


    // endregion


    // region Binding to view

    inner class Bindings : AmvFitter() {
        // Controls
        val playerView: PlayerView by lazy {
            findViewById<PlayerView>(R.id.playerView)
        }
        val progressBar : ProgressBar by lazy {
            findViewById<ProgressBar>(R.id.progressBar)
        }
        private val errorMessageView: TextView by lazy {
            findViewById<TextView>(R.id.errorMessage)
        }

        // Public properties/methods

        var errorMessage: String
            get() = errorMessageView.text?.toString() ?: ""
            set(v) {
                errorMessageView.text = v
                updateState()
            }

        var playerState : IAmvVideoPlayer.PlayerState
            get() = mPlayerState
            set(state) {
                if (state != mPlayerState) {
                    mPlayerState = state
                    updateState()
                }
            }

        fun setVideoSize(width:Float, height:Float) {
            if(mVideoSize.width!=width || mVideoSize.height!=height) {
                mVideoSize.set(width,height)
                fit(mVideoSize, mPlayerSize)
                updateLayout()
            }
        }

        fun reset() {
            mInitial = true
            errorMessage = ""
            playerState = IAmvVideoPlayer.PlayerState.None
        }

        // Rendering Parameters
        private val mVideoSize = MuSize(100f, 100f)                  // 動画のNatural Size
        private val mPlayerSize = MuSize(0f,0f)                      // VideoView のサイズ

        // Player States
        private var mInitial: Boolean = true
        private var mPlayerState = IAmvVideoPlayer.PlayerState.None

        private val isReady: Boolean
            get() = when(mPlayerState) { IAmvVideoPlayer.PlayerState.Paused, IAmvVideoPlayer.PlayerState.Playing -> true else -> false }

        val isLoading: Boolean
            get() = mPlayerState == IAmvVideoPlayer.PlayerState.Loading


        private val isError: Boolean
            get() = mPlayerState == IAmvVideoPlayer.PlayerState.Error

        @Suppress("unused")
        val isPlaying : Boolean
            get() = mPlayerState == IAmvVideoPlayer.PlayerState.Playing

        // Update Views
        private fun updateLayout() {
            val w = mPlayerSize.width.roundToInt()
            val h = mPlayerSize.height.roundToInt()
            playerView.setLayoutSize(w,h)
            sizeChangedListener.invoke(this@AmvExoVideoPlayer, w,h)
        }

        // Update States
        private fun updateState() {
            if(mInitial && isReady) {
                mInitial = false
                videoPreparedListener.invoke(this@AmvExoVideoPlayer, naturalDuration)
            }
            progressBar.visibility = if(isLoading) View.VISIBLE else View.INVISIBLE
            errorMessageView.visibility = if(isError && errorMessage.isNotEmpty()) View.VISIBLE else View.INVISIBLE
            playerStateChangedListener.invoke(this@AmvExoVideoPlayer, mPlayerState)
        }
    }

    // endregion

    // region Overrides to IAmvVideoPlayer

    // Listeners
    override val sourceChangedListener = IAmvVideoPlayer.SourceChangedListener()
    override val videoPreparedListener = IAmvVideoPlayer.VideoPreparedListener()
    override val playerStateChangedListener = IAmvVideoPlayer.PlayerStateChangedListener()
    override val seekCompletedListener = IAmvVideoPlayer.SeekCompletedListener()
    override val sizeChangedListener = IAmvVideoPlayer.SizeChangedListener()

    // Properties
    override val naturalDuration: Long
        get() = mPlayer?.duration ?: 1000L

    override val seekPosition: Long
        get() = mPlayer?.currentPosition ?: 0L

    override val playerState: IAmvVideoPlayer.PlayerState
        get() = mBindings.playerState

    // Methods
    override fun setLayoutHint(mode: FitMode, width: Float, height: Float) {
        mBindings.setHint(mode, width, height)
    }

    override fun reset() {
        mSource = null
        mEnded = false
        mBindings.reset()
        mPlayer?.apply {
            stop()
        }
    }

    override fun setSource(source: File, autoPlay: Boolean, playFrom: Long) {
        reset()

        mSource = source
        sourceChangedListener.invoke(this, source)
        mPlayer?.apply {
            val mediaSource = ExtractorMediaSource.Factory(        // ExtractorMediaSource ... non-adaptiveなほとんどのファイルに対応
                    DefaultDataSourceFactory(context, "amv")    //
            ).createMediaSource(Uri.fromFile(source))
            prepare(mediaSource, true, true)
            if (playFrom > 0) {
                seekTo(playFrom)
            }
            playWhenReady = autoPlay
        }
    }

    override fun play() {
        mPlayer?.apply {
            if(mEnded) {
                // 動画ファイルの最後まで再生して止まっている場合は、先頭にシークしてから再生を開始する
                mEnded = false
                seekTo(0)
            }
            playWhenReady = true
        }
    }

    override fun pause() {
        mPlayer?.apply {
            playWhenReady = false
        }
    }

    private fun togglePlay() {
        if(null!=mSource) {
            mPlayer?.apply {
                playWhenReady = !playWhenReady
            }
        }
    }

    override fun seekTo(pos: Long) {
//        mPlayer?.apply {
//            seekTo(pos)
//        }
        if(mEnded) {
            // 動画ファイルの最後まで再生して止まっているとき、Playerの内部状態は、playWhenReady == true のままになっている。
            // そのまま、シークしてしまうと、シーク後に勝手に再生が再開されてしまう。
            // これを回避するため、シークのタイミングで、mEndedフラグが立っていれば、再生を終了してからシークすることにする。
            pause()
            mEnded = false  // pause()の中から onPlayerStateChangedが呼ばれて、mEnded は false にクリアされているはずだが。
        }
        seekManager.request(pos)
    }

    override fun setFastSeekMode(fast: Boolean) {
//        mPlayer?.apply {
//            setSeekParameters(if(fast) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
//        }
        if(fast) {
            seekManager.begin(naturalDuration)
        } else {
            seekManager.end()
        }
    }
    // endregion

    // region Seek

    /**
     * 絶望的にどんくさいシークを、少し改善するクラス
     *
     * VideoView をやめて、ExoPlayerを使うようにしたことにより、KeyFrame以外へのシークが可能になった。
     * しかし、KeyFrame以外へのシークはかなり遅く、ExoPlayerのステートが、頻繁に、Loading に変化し、
     * シークバーから指を放すまで、プレーヤー画面の表示が更新されない。
     *
     * 実際、デフォルトのコントローラーのスライダーを操作したときも、同じ動作になる。
     *
     * seekモードをCLOSEST_SYNCにると、キーフレームにしかシークしないが、途中の画面も描画されるので、
     * 激しくスライダーを操作しているときは、CLOSEST_SYNCでシークし、止まっているか、ゆっくり操作すると
     * EXACTでシークするようにしてみる。
     */
    inner class SeekManager {
        private val mInterval = 100L        // スライダーの動きを監視するためのタイマーインターバル
        private val mWaitCount = 5          // 上のインターバルで何回チェックし、動きがないことが確認されたらEXACTシークするか？　mInterval*mWaitCount (ms)
        private val mPercent = 2            // 微動（移動していない）とみなす移動量・・・全Durationに対するパーセント
        private val mHandler = Handler()    // タイマー的に使用するHandler
        private var mSeekTarget: Long = -1L // 目標シーク位置
        private var mSeeking = false        // スライダーによるシーク中はtrue / それ以外は false
        private var mCheckCounter = 0       // チェックカウンタ （この値がmWaitCountを超えたら、EXACTシークする）
        private var mThreshold = 0L         // 微動とみなす移動量の閾値・・・naturalDuration * mPercent/100 (ms)
        private var mFastMode = false       // 現在、ExoPlayerに設定しているシークモード（true: CLOSEST_SYNC / false: EXACT）

        // mInterval毎に実行する処理
        private val mLoop = Runnable {
            mCheckCounter++
            checkAndSeek()
        }

        /**
         * Loopの中の人
         */
        private fun checkAndSeek() {
            if(mSeeking) {
                if(mCheckCounter>=mWaitCount && mSeekTarget>=0 ) {
                    if(!mBindings.isLoading) {
                        UtLogger.debug("EXO-Seek: checked ok, but loading now")
                    } else {
                        UtLogger.debug("EXO-Seek: checked ok")
                        exactSeek(mSeekTarget)
                        mCheckCounter = 0
                    }
                }
                postDelayed(mLoop, mInterval)
            }
        }

        /***
         * スライダーによるシークを開始する
         */
        fun begin(duration:Long) {
            UtLogger.debug("EXO-Seek: begin")
            if(!mSeeking) {
                mSeeking = true
                mPlayer?.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                mSeekTarget = -1L
                mThreshold = (duration * mPercent) / 100
                mHandler.postDelayed(mLoop, 0)
            }
        }

        /***
         * スライダーによるシークを終了する
         */
        fun end() {
            UtLogger.debug("EXO-Seek: end")
            if(mSeeking) {
                mSeeking = false
                exactSeek(mSeekTarget)
            }
        }

        /***
         * シークを要求する
         */
        fun request(pos:Long) {
            UtLogger.debug("EXO-Seek: request - $pos")
            if(mSeeking) {
                if (mSeekTarget < 0 || (pos - mSeekTarget).absoluteValue > mThreshold) {
                    UtLogger.debug("EXO-Seek: reset check count - $pos ($mCheckCounter)")
                    mCheckCounter = 0
                    fastSeek(pos)
                }
                mSeekTarget = pos
            } else {
                exactSeek(pos)
            }
        }

        private fun fastSeek(pos:Long) {
            UtLogger.debug("EXO-Seek: fast seek - $pos")
            if(mBindings.isLoading) {
                return
            }
            if(!mFastMode) {
                UtLogger.debug("EXO-Seek: switch to fast seek")
                mFastMode = true
                mPlayer?.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
            mPlayer?.seekTo(pos)
        }

        private fun exactSeek(pos:Long) {
            UtLogger.debug("EXO-Seek: exact seek - $pos")
            if(mFastMode) {
                UtLogger.debug("EXO-Seek: switch to exact seek")
                mFastMode = false
                mPlayer?.setSeekParameters(SeekParameters.EXACT)
            }
            mPlayer?.seekTo(pos)
        }
    }
    private var seekManager = SeekManager()

    // endregion

    // region Saving States

    override fun onSaveInstanceState(): Parcelable {
        UtLogger.debug("LC-View: onSaveInstanceState")
        val parent =  super.onSaveInstanceState()
        return SavedState(parent, mSource)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        UtLogger.debug("LC-View: onRestoreInstanceState")
        if(state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            val source = state.source
            if(source!=null) {
                setSource(source, false, 0)
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : View.BaseSavedState {

        val source : File?

        /**
         * Constructor called from [AmvSlider.onSaveInstanceState]
         */
        constructor(superState: Parcelable, file:File?) : super(superState) {
            source = file
        }

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(parcel: Parcel) : super(parcel) {
            source = parcel.readSerializable() as? File
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeSerializable(source)
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