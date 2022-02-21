/**
 * ExoPlayer を使った IAmvVideoPlayerの実装クラス
 *
 * @author M.TOYOTA 2018.07.26 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.video.VideoSize
import kotlinx.coroutines.*
import java.lang.Runnable
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


class AmvExoVideoPlayer @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), IAmvVideoPlayer {

    companion object {
        val logger = AmvSettings.logger
    }

    // region Private Properties

    private val mBindings = Bindings()
    private var mSource : IAmvSource? = null
    private val mPlayer : ExoPlayer = ExoPlayer.Builder(context).build()
    private var mEnded : Boolean = false                // 動画ファイルの最後まで再生が終わって停止した状態から、Playボタンを押したときに、先頭から再生を開始する動作を実現するためのフラグ
    private var mMediaSource:MediaSource? = null
    private var mClipping : IAmvVideoPlayer.Clipping? = null
    private val mFitParent : Boolean
    private val mHandler : Handler by lazy {
        Handler(Looper.getMainLooper())
    }

    // endregion

    // region ExoPlayer - Event Handler

    private val mPlayerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            mBindings.setVideoSize(videoSize.width.toFloat(), videoSize.height.toFloat())
        }

//        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
//            // speed, pitch, skipSilence, scaledUsPerMs
//        }

        override fun onSeekProcessed() {
            if(!seekManager.isSeeking && !mBindings.isPlaying) {
                seekCompletedListener.invoke(this@AmvExoVideoPlayer, seekPosition)
            }
        }

        override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
        }

        override fun onPlayerError(error: PlaybackException) {
            logger.stackTrace(error, "ExoPlayer: error")
            mSource?.invalidate()
            mBindings.errorMessage = /*error?.message ?:*/ AmvStringPool[R.string.error] ?: context.getString(R.string.error)
            mBindings.playerState = IAmvVideoPlayer.PlayerState.Error
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            logger.debug("loading = $isLoading")
            if(isLoading && mPlayer.playbackState==Player.STATE_BUFFERING) {
                mBindings.playerState = IAmvVideoPlayer.PlayerState.Loading
            }
        }

//        override fun onPositionDiscontinuity(reason: Int) {
//        }
//
//        override fun onRepeatModeChanged(repeatMode: Int) {
//        }
//
//        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
//        }

//        override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {
//        }

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

            logger.debug("status = ${ppn(playbackState)} / playWhenReady = $playWhenReady")

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
        mBindings.playerView.player = mPlayer
        mPlayer.addListener(mPlayerListener)
        isSaveFromParentEnabled = false

        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvExoVideoPlayer,defStyleAttr,0)
        try {
            // タッチで再生/一時停止をトグルさせる動作の有効・無効
            //
            // デフォルト有効
            //      ユニットプレーヤー以外は無効化
            if (sa.getBoolean(R.styleable.AmvExoVideoPlayer_playOnTouch, true)) {

                this.setOnClickListener {
                    if (it is AmvExoVideoPlayer) {
                        it.togglePlay()
                    }
                }
            }
            // ExoPlayerのControllerを表示するかしないか・・・表示する場合も、カスタマイズされたControllerが使用される
            //
            // デフォルト無効
            //      フルスクリーン再生の場合のみ有効
            if(sa.getBoolean(R.styleable.AmvExoVideoPlayer_showControlBar, false)) {
                mBindings.playerView.useController = true
            }

            // AmvExoVideoPlayerのサイズに合わせて、プレーヤーサイズを自動調整するかどうか
            // 汎用的には、AmvExoVideoPlayer.setLayoutHint()を呼び出すことで動画プレーヤー画面のサイズを変更するが、
            // 実装によっては、この指定の方が便利なケースもありそう。
            //
            // デフォルト無効
            //      フルスクリーン再生の場合のみ有効
            mFitParent = sa.getBoolean(R.styleable.AmvExoVideoPlayer_fitParent, false)
        } finally {
            sa.recycle()
        }
    }

    override fun onDetachedFromWindow() {
        mPlayer.removeListener(mPlayerListener)
        mPlayer.release()

        sourceChangedListener.clear()
        videoPreparedListener.clear()
        playerStateChangedListener.clear()
        seekCompletedListener.clear()
        sizeChangedListener.clear()
        clipChangedListener.clear()

        mSource?.release()
        mSource = null

        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if(mFitParent) {
            // このタイミングで setLayoutHint()を呼び出すと、回転時に layout_width/heightによる指定サイズと、
            // 実際の表示サイズが一致しなくなる。おそらく、システムによるレンダリングの途中なので、うまくいかないのだろう。
            // 少し遅延させることで正しく動くようになった。
            mHandler.postDelayed( {
                setLayoutHint(FitMode.Inside, w.toFloat(), h.toFloat())
            }, 200)
        }
    }

    // endregion

    // region Binding to view

    private inner class Bindings : AmvFitter() {
        // Controls
        val playerView: PlayerView by lazy {
             findViewById(R.id.exp_playerView)
        }
        val progressRing: ProgressBar by lazy {
            findViewById(R.id.exp_progressRing)
        }
        private val errorMessageView: TextView by lazy {
            findViewById(R.id.exp_errorMessage)
        }

        // Public properties/methods

        var errorMessage: String
            get() = errorMessageView.text?.toString() ?: ""
            set(v) {
                errorMessageView.text = v
                isError = v.isNotBlank()
            }

        var playerState: IAmvVideoPlayer.PlayerState
            get() = mPlayerState
            set(state) {
                if (state != mPlayerState) {
                    mPlayerState = state
                    updateState()
                }
            }
        val videoSize: Size
            get() = mVideoSize.asSize

        fun setVideoSize(width: Float, height: Float) {
            if (mVideoSize.width != width || mVideoSize.height != height) {
                mVideoSize.set(width, height)
                updateLayout()
            }
        }

        fun reset() {
            mInitial = true
            playerState = IAmvVideoPlayer.PlayerState.None
            errorMessage = ""
        }

        // Rendering Parameters
        private val mVideoSize = MuSize(0f, 0f)              // 動画のNatural Size
        private val mPlayerSize = MuSize(0f, 0f)                 // VideoView のサイズ

        // Player States
        private var mInitial: Boolean = true
        private var mPlayerState = IAmvVideoPlayer.PlayerState.None

        private val isReady: Boolean
            get() = when (mPlayerState) { IAmvVideoPlayer.PlayerState.Paused, IAmvVideoPlayer.PlayerState.Playing -> true
                else -> false
            }

        val isLoading: Boolean
            get() = mPlayerState == IAmvVideoPlayer.PlayerState.Loading


        private var isError: Boolean = false
            set(v) {
                field = v
                updateState()
            }
            get() = field || mPlayerState == IAmvVideoPlayer.PlayerState.Error

        val isPlaying: Boolean
            get() = mPlayerState == IAmvVideoPlayer.PlayerState.Playing

        // Update Views
        private fun updateLayout() {
            val videoSize = if (mVideoSize.isEmpty) MuSize(640f,480f) else mVideoSize
            fit(videoSize, mPlayerSize)
            val w = mPlayerSize.width.roundToInt()
            val h = mPlayerSize.height.roundToInt()
            playerView.setLayoutSize(w, h)
            sizeChangedListener.invoke(this@AmvExoVideoPlayer, w, h)
        }

        inner class ProgressRingManager : Animation.AnimationListener {
            var currentAnimation : Animation? = null

            private val fadeInAnim = AlphaAnimation(0f,1f).apply {
                duration = 3000
                setAnimationListener(this@ProgressRingManager)
            }

            private val fadeOutAnim =  AlphaAnimation(1f,0f).apply {
                duration = 200
                setAnimationListener(this@ProgressRingManager)
            }

            fun show() {
                if(currentAnimation == fadeOutAnim) {
                    fadeOutAnim.cancel()
                } else if (null!=currentAnimation || progressRing.visibility==View.VISIBLE) {
                    return
                }
                currentAnimation = fadeInAnim
                progressRing.startAnimation(fadeInAnim)
            }

            fun hide() {
                if(currentAnimation == fadeInAnim) {
                    fadeInAnim.cancel()
                } else if (null!=currentAnimation || progressRing.visibility==View.INVISIBLE) {
                    return
                }
                currentAnimation = fadeOutAnim
                progressRing.startAnimation(fadeOutAnim)
            }

            override fun onAnimationRepeat(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
                if(currentAnimation === fadeInAnim) {
                    progressRing.visibility = View.VISIBLE
                }
            }

            override fun onAnimationEnd(animation: Animation?) {
                if(currentAnimation === fadeOutAnim) {
                    progressRing.visibility = View.INVISIBLE
                }
                currentAnimation = null
                progressRing.clearAnimation()
            }
        }

        private val progressRingManager = ProgressRingManager()

        // Update States
        private fun updateState() {
            if (mInitial && isReady) {
                mInitial = false
                videoPreparedListener.invoke(this@AmvExoVideoPlayer, naturalDuration)
            }
            if (isLoading&&!isPlaying) {
                progressRingManager.show()
            } else {
                progressRingManager.hide()
            }
            errorMessageView.visibility = if (isError && errorMessage.isNotEmpty()) View.VISIBLE else View.INVISIBLE
            playerStateChangedListener.invoke(this@AmvExoVideoPlayer, mPlayerState)
        }

        fun setHintAndUpdateLayout(fitMode:FitMode, width:Float, height:Float) {
            setHint(fitMode, width, height)
            updateLayout()
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
    override val clipChangedListener = IAmvVideoPlayer.ClipChangedListener()

    // Properties
    override val naturalDuration: Long
        get() = mPlayer.duration

    override val seekPosition: Long
        get() = mPlayer.currentPosition

    override var isMuted:Boolean
        get() = mPlayer.volume != 0f
        set(v) {
            if(v) {
                mPlayer.volume = 0f
            } else {
                mPlayer.volume = 1f
            }
        }


    override val playerState: IAmvVideoPlayer.PlayerState
        get() = mBindings.playerState

    override val isPlayingOrReservedToPlay : Boolean
        get() = mBindings.isPlaying || mPlayer.playWhenReady

    override val videoSize: Size
        get() = mBindings.videoSize

    /**
     * ExoPlayerのデフォルトのコントローラーを表示するか？
     * 通常はAmvVideoControllerなど、Amvで実装したコントローラーを使用するが、
     * FullScreenActivity / PinP では、デフォルトのコントローラーを使用する。
     */
    var showDefaultController: Boolean
        get() = mBindings.playerView.useController
        set(v) {
            mBindings.playerView.useController = v
            if(v) {
                mBindings.playerView.showController()   // 非表示は自動的に隠れるが、表示するときは明示的に表示してやらないダメっぽい
            }
        }

    // Methods
    override fun setLayoutHint(mode: FitMode, width: Float, height: Float) {
        mBindings.setHintAndUpdateLayout(mode, width, height)

    }

    override fun getLayoutHint(): IAmvLayoutHint {
        return mBindings
    }

    override fun reset() {
        mMediaSource = null
        mSource?.release()
        mSource = null
        mEnded = false
        mBindings.reset()
        mPlayer.stop()
    }

    override fun setClip(clipping:IAmvVideoPlayer.Clipping?) {
        if(mClipping == clipping) {
            return
        }
        mClipping = clipping
        val source = createClippingSource()
        if(null!=source) {
//            mPlayer.prepare(source, true, true)
            mPlayer.setMediaSource(source, true)
            mPlayer.prepare()
            if(null!=clipping) {
                playerSeek(clipping.start)
            }
        }
    }

    override val clip:IAmvVideoPlayer.Clipping?
        get() = mClipping

    /**
     * Uriを直接再生する
     * （特殊用途向け・・・AmvExoVideoPlayer単体で使い、イベントとか、あまり気にしない場合にのみ使えるかも）
     */
//    fun setUri(uri:Uri) {
//        reset()
//        val mediaSource = ExtractorMediaSource.Factory(        // ExtractorMediaSource ... non-adaptiveなほとんどのファイルに対応
//                DefaultDataSourceFactory(context, "amv")    //
//        ).createMediaSource(uri)
//        mMediaSource = mediaSource
//        mPlayer.prepare(mediaSource, true, true)
//    }


    override fun setSource(source: IAmvSource, autoPlay: Boolean, playFrom: Long) {
        reset()
        mBindings.progressRing.visibility = View.VISIBLE


        source.addRef()
        mSource = source

        CoroutineScope(Dispatchers.IO).launch {
            val uri = source.getUriAsync()
            withContext(Dispatchers.Main) {
                if (uri == null) {
                    mBindings.errorMessage = source.error.toString()
                } else {
                    sourceChangedListener.invoke(this@AmvExoVideoPlayer, source)
                    val mediaSource = ProgressiveMediaSource.Factory(        // ExtractorMediaSource ... non-adaptiveなほとんどのファイルに対応
                        DefaultDataSource.Factory(context)
                    ).createMediaSource(MediaItem.fromUri(uri))
                    mMediaSource = mediaSource
//                    mPlayer.prepare(createClippingSource(mediaSource), true, true)
                    mPlayer.setMediaSource(mediaSource, true)
                    mPlayer.prepare()
                    if (null != mClipping || playFrom > 0) {
                        playerSeek(playFrom)
                    }
                    mPlayer.playWhenReady = autoPlay
                }
            }
        }
    }

    override val source:IAmvSource?
        get() = mSource


    override fun play() {
        if(mEnded) {
            // 動画ファイルの最後まで再生して止まっている場合は、先頭にシークしてから再生を開始する
            mEnded = false
            playerSeek(0)
        }
        mPlayer.playWhenReady = true
    }

//    fun playFrom(pos:Long) {
//        mEnded = false
//        playerSeek(pos)
//        mPlayer.playWhenReady = true
//    }

    override fun pause() {
        mPlayer.playWhenReady = false
    }

    override fun seekTo(pos: Long) {
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
        if(fast) {
            seekManager.begin(naturalDuration)
        } else {
            seekManager.end()
        }
    }
    // endregion

    // region Private methods

    /**
     * 再生/停止をトグル
     */
    fun togglePlay() {
        if(null!=mSource) {
            when(playerState) {
                IAmvVideoPlayer.PlayerState.Paused -> play()
                IAmvVideoPlayer.PlayerState.Playing->pause()
                else -> {}
            }
        }
    }

    /**
     * シーク位置をクリッピング範囲に制限する
     */
    private fun clipPos(pos:Long) : Long {
        return mClipping?.clipPos(pos) ?: pos
    }

    /**
     * クリップ範囲を考慮してシークする
     * mPlayer.seekTo()を直接呼び出してはいけない。
     */
    private fun playerSeek(pos:Long) {
        mPlayer.seekTo(clipPos(pos))
    }

    /**
     * クリッピングソースを作成する
     * クリッピングが指定されていなければ、元のMediaSourceを返す
     */
    private fun createClippingSource(orgSource:MediaSource) : MediaSource {
        clipChangedListener.invoke(this, mClipping)
        val clipping = mClipping
        return if (null != clipping && clipping.isValid) {
            // ClippingMediaSource に start をセットすると、IllegalClippingExceptionが出て使えないので、
            // endだけを指定し、startは、シークして使うようにする
            ClippingMediaSource(orgSource, 0/*clipping.start * 1000*/, clipping.end * 1000)
        } else {
            orgSource
        }
    }

    private fun createClippingSource() : MediaSource? {
        val orgSource = mMediaSource
        return if(null==orgSource) {
            null
        } else {
            createClippingSource(orgSource)
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
        private val mPercent = 1            // 微動（移動していない）とみなす移動量・・・全Durationに対するパーセント
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
                    if(mBindings.isLoading) {
                        logger.debug("seek: checked ok, but loading now")
                    } else {
                        logger.debug("seek: checked ok")
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
            logger.debug("seek begin")
            if(!mSeeking) {
                mSeeking = true
                mFastMode = true
                mPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                mSeekTarget = -1L
                mThreshold = (duration * mPercent) / 100
                mHandler.postDelayed(mLoop, 0)
            }
        }

        /***
         * スライダーによるシークを終了する
         */
        fun end() {
            logger.debug("seek end")
            if(mSeeking) {
                mSeeking = false
                if(mSeekTarget>=0) {
                    exactSeek(mSeekTarget)
                    mSeekTarget = -1
                }
            }
        }

        /***
         * シークを要求する
         */
        fun request(pos:Long) {
            logger.debug("seek request - $pos")
            if(mSeeking) {
                if (mSeekTarget < 0 || (pos - mSeekTarget).absoluteValue > mThreshold) {
                    logger.debug("reset check count - $pos ($mCheckCounter)")
                    mCheckCounter = 0
                }
                fastSeek(pos)
                mSeekTarget = pos
            } else {
                exactSeek(pos)
            }
        }

        private fun fastSeek(pos:Long) {
            logger.debug("fast seek - $pos")
            if(mBindings.isLoading) {
                return
            }
            if(!mFastMode) {
                logger.debug("switch to fast seek")
                mFastMode = true
                mPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
            playerSeek(pos)
        }

        private fun exactSeek(pos:Long) {
            logger.debug("exact seek - $pos")
            if(mFastMode) {
                logger.debug("switch to exact seek")
                mFastMode = false
                mPlayer.setSeekParameters(SeekParameters.EXACT)
            }
            playerSeek(pos)
        }

        val isSeeking:Boolean
            get() = mSeeking
    }
    private var seekManager = SeekManager()

    // endregion
}