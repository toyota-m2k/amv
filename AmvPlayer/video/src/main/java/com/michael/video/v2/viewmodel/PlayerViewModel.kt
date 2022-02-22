package com.michael.video.v2.viewmodel

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Size
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ClippingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.video.VideoSize
import com.michael.video.*
import com.michael.video.v2.elements.AmvExoVideoPlayer
import io.github.toyota32k.utils.SuspendableEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable
import java.lang.Runnable
import kotlin.math.absoluteValue

/**
 * 動画プレーヤーと、それに関するプロパティを保持するビューモデル
 * ExoPlayerは（何と！）Viewではなく、ActivityやViewのライフサイクルから独立しているので、ビューモデルに持たせておくのが一番しっくりくるのだ。
 * しかも、ダイアログのような一時的な画面で使うのでなく、PinPや全画面表示などを有効にするなら、このビューモデルはApplicationスコープのようなライフサイクルオブジェクトに持たせるのがよい。
 * @param context   Application Context
 */
class PlayerViewModel(
    context: Context,                   // application context が必要
) : Closeable {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)          // dispose()まで有効なコルーチンスコープ
    val context = context.applicationContext
    private val listener =  PlayerListener()
    // ExoPlayer
    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(listener)
    }

    /**
     * 動画のソース
     */
    val source = MutableStateFlow<IAmvSource?>(null)
    var sourceClipping: IAmvVideoPlayer.Clipping? = null
    var pseudoClipping: IAmvVideoPlayer.Clipping? = null

    val videoSize = MutableStateFlow<VideoSize?>(null)
    val rootViewSize = MutableStateFlow<Size?>(null)
    val state = MutableStateFlow<IAmvVideoPlayer.PlayerState>(IAmvVideoPlayer.PlayerState.None)
    val errorMessage = MutableStateFlow<String?>(null)
    val naturalDuration = MutableStateFlow<Long>(0L)


    var stretchVideoToView = false

    private val mVideoSize = MuSize()
    private val mPlayerSize = MuSize()
    private val mFitter = AmvFitter()
    val playerSizeFlow = combine(videoSize.filterNotNull(),rootViewSize.filterNotNull()) { videoSize, rootViewSize->
        mVideoSize.set(videoSize.width.toFloat(), videoSize.height.toFloat())
        mFitter.setHint(FitMode.Inside, rootViewSize.width.toFloat(), rootViewSize.height.toFloat())
        mFitter.fit(mVideoSize, mPlayerSize)
        mPlayerSize.asSize
    }

    val isLoadingFlow = state.map { it== IAmvVideoPlayer.PlayerState.Loading }
    val isReadyFlow = state.map { it== IAmvVideoPlayer.PlayerState.Playing || it== IAmvVideoPlayer.PlayerState.Paused }
    val isErrorFlow = errorMessage.map { !it.isNullOrBlank() }
    val isPlayingFlow = state.map { it==IAmvVideoPlayer.PlayerState.Playing }

    val isLoading get() = state.value == IAmvVideoPlayer.PlayerState.Loading
    val isReady:Boolean get() = state.value.let { it== IAmvVideoPlayer.PlayerState.Playing || it== IAmvVideoPlayer.PlayerState.Paused }
    val isPlaying get() = state.value == IAmvVideoPlayer.PlayerState.Playing
    val isError get() = !errorMessage.value.isNullOrBlank()

    private val playerSeekPositionInternalFlow = MutableStateFlow(0L)
    val playerSeekPosition: Flow<Long> =  playerSeekPositionInternalFlow

    var ended = false
        private set
    var isDisposed:Boolean = false
    val watchPositionEvent = SuspendableEvent(signal = false, autoReset = false)

    init {
        isPlayingFlow.onEach {
            if(it) {
                watchPositionEvent.set()
            }
        }.launchIn(scope)

        scope.launch {
            while(!isDisposed) {
                watchPositionEvent.waitOne()
                val pos = player.currentPosition
                if(!seekManager.isSeeking) {
                    playerSeekPositionInternalFlow.value = pos
                }
                if(!isPlaying) {
                    watchPositionEvent.reset()
                }
                else if(pseudoClipping!=null) {
                    val clip = pseudoClipping?.clipPos(pos) ?:pos
                    if(clip<pos) {
                        pause()
                        ended = true
                        clippingSeekTo(clip)
                        watchPositionEvent.reset()
                    }
                }
                delay(50)
            }
        }
    }

    override fun close() {
        player.removeListener(listener)
        player.release()
        scope.cancel()
        isDisposed = true
    }

    fun reset() {
        source.value = null
        sourceClipping = null
        pseudoClipping = null
        ended = false
        state.value = IAmvVideoPlayer.PlayerState.None
        videoSize.value = null
        errorMessage.value = null
        naturalDuration.value = 0L
    }

    fun setVideoSource(source: IAmvSource?, sourceClipping: IAmvVideoPlayer.Clipping? = null) {
        reset()
        if(source==null) {
            return
        }

        this.source.value = source
        this.sourceClipping = sourceClipping

        CoroutineScope(Dispatchers.IO).launch {
            val uri = source.getUriAsync()
            if(uri==null) {
                errorMessage.value = source.error.toString()
            } else {
                withContext(Dispatchers.Main) {
                    if(isDisposed) return@withContext
                    val mediaSource: MediaSource = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(MediaItem.fromUri(uri)).run {
                        if (sourceClipping != null && sourceClipping.isValid) {
                            ClippingMediaSource(this, sourceClipping.start*1000, sourceClipping.end * 1000)
                        } else {
                            this
                        }
                    }
                    player.setMediaSource(mediaSource, true)
                    player.prepare()
                    seekManager.reset()
                }
            }
        }
    }

    fun clipPosition(pos:Long):Long {
        val clipped = pseudoClipping?.clipPos(pos)?:pos
        return if(clipped<0) {
            0
        } else if(clipped>naturalDuration.value) {
            naturalDuration.value
        } else clipped
    }

    fun clippingSeekTo(pos:Long) {
        val clippedPos = clipPosition(pos)
        val end = pseudoClipping?.end ?: naturalDuration.value

        player.seekTo(clippedPos)
        if(clippedPos == end) {
            pause()
            ended = true
        } else {
            ended = false
        }
    }

    fun togglePlay() {
        if(player.playWhenReady) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        if(isDisposed) return
        if(ended) {
            // 動画ファイルの最後まで再生して止まっている場合は、先頭にシークしてから再生を開始する
            ended = false
            seekManager.exactSeek(0L)
        }
        player.playWhenReady = true
    }

    fun pause() {
        if(isDisposed) return
        player.playWhenReady = false
    }

    fun seekTo(pos:Long) {
        if(isDisposed) return
        if(ended) {
            // 動画ファイルの最後まで再生して止まっているとき、Playerの内部状態は、playWhenReady == true のままになっている。
            // そのまま、シークしてしまうと、シーク後に勝手に再生が再開されてしまう。
            // これを回避するため、シークのタイミングで、mEndedフラグが立っていれば、再生を終了してからシークすることにする。
            ended = false
            player.playWhenReady = false
        }
        seekManager.request(pos)
    }

    inner class PlayerListener :  Player.Listener
    {

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            this@PlayerViewModel.videoSize.value = videoSize
        }

//        override fun onSeekProcessed() {
//            playerSeekPositionInternalFlow.value = player.contentPosition
//        }

        override fun onPlayerError(error: PlaybackException) {
            AmvExoVideoPlayer.logger.stackTrace(error)
            source.value?.invalidate()
            if(!isReady) {
                state.value = IAmvVideoPlayer.PlayerState.Error
                errorMessage.value = AmvStringPool[R.string.error] ?: context.getString(R.string.error)
            } else {
                AmvExoVideoPlayer.logger.warn("ignoring exo error.")
            }
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            AmvExoVideoPlayer.logger.debug("loading = $isLoading")
            if (isLoading && player.playbackState == Player.STATE_BUFFERING) {
                if(state.value==IAmvVideoPlayer.PlayerState.None) {
                    state.value = IAmvVideoPlayer.PlayerState.Loading
                } else {
                    scope.launch {
                        for(i in 0..20) {
                            delay(100)
                            if (player.playbackState != Player.STATE_BUFFERING) {
                                break
                            }
                        }
                        if (player.playbackState == Player.STATE_BUFFERING) {
                            // ２秒以上bufferingならロード中に戻す
                            state.value = IAmvVideoPlayer.PlayerState.Loading
                        }
                    }
                }
            }
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            AmvExoVideoPlayer.logger.debug {
                val ppn = {s:Int->
                    when(s) {
                        Player.STATE_IDLE -> "Idle"
                        Player.STATE_BUFFERING -> "Buffering"
                        Player.STATE_READY -> "Ready"
                        Player.STATE_ENDED -> "Ended"
                        else -> "Unknown"
                    }
                }
                "status = ${ppn(playbackState)} / playWhenReady = $playWhenReady"
            }
            when(playbackState) {
                Player.STATE_READY ->  {
                    state.value = if(playWhenReady) IAmvVideoPlayer.PlayerState.Playing else IAmvVideoPlayer.PlayerState.Paused
                    naturalDuration.value = player.duration
                }
                Player.STATE_ENDED -> {
                    player.playWhenReady = false
                    ended = true
                    state.value = IAmvVideoPlayer.PlayerState.Paused
                }
                else -> {}
            }
        }
    }


    fun beginFastSeekMode() {
        val duration = naturalDuration.value
        if(duration==0L) return
        seekManager.begin(duration)
    }

    fun endFastSeekMode() {
        seekManager.end()
    }

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
        private val mHandler = Handler(Looper.getMainLooper())

        // mInterval毎に実行する処理
        private val mLoop = Runnable {
            mCheckCounter++
            checkAndSeek()
        }

        fun reset() {
            mFastMode = false
            mSeekTarget -1L         // 目標シーク位置
            mSeeking = false        // スライダーによるシーク中はtrue / それ以外は false
            mCheckCounter = 0       // チェックカウンタ （この値がmWaitCountを超えたら、EXACTシークする）
            mThreshold = 0L         // 微動とみなす移動量の閾値・・・naturalDuration * mPercent/100 (ms)
            mFastMode = false       // 現在、ExoPlayerに設定しているシークモード（true: CLOSEST_SYNC / false: EXACT）
            player.setSeekParameters(SeekParameters.EXACT)
        }

        /**
         * Loopの中の人
         */
        private fun checkAndSeek() {
            if(mSeeking) {
                if(mCheckCounter>=mWaitCount && mSeekTarget>=0 ) {
                    if(isLoading) {
                        com.michael.video.AmvExoVideoPlayer.logger.debug("seek: checked ok, but loading now")
                    } else {
                        com.michael.video.AmvExoVideoPlayer.logger.debug("seek: checked ok")
                        exactSeek(mSeekTarget)
                        mCheckCounter = 0
                    }
                }
                mHandler.postDelayed(mLoop, mInterval)
            }
        }

        /***
         * スライダーによるシークを開始する
         */
        fun begin(duration:Long) {
            com.michael.video.AmvExoVideoPlayer.logger.debug("seek begin")
            if(!mSeeking) {
                mSeeking = true
                mFastMode = true
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                mSeekTarget = -1L
                mThreshold = (duration * mPercent) / 100
                mHandler.postDelayed(mLoop, 0)
            }
        }

        /***
         * スライダーによるシークを終了する
         */
        fun end() {
            com.michael.video.AmvExoVideoPlayer.logger.debug("seek end")
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
            com.michael.video.AmvExoVideoPlayer.logger.debug("seek request - $pos")
            if(mSeeking) {
                if (mSeekTarget < 0 || (pos - mSeekTarget).absoluteValue > mThreshold) {
                    com.michael.video.AmvExoVideoPlayer.logger.debug("reset check count - $pos ($mCheckCounter)")
                    mCheckCounter = 0
                }
                fastSeek(pos)
                mSeekTarget = pos
            } else {
                exactSeek(pos)
            }
        }

        fun fastSeek(pos:Long) {
            com.michael.video.AmvExoVideoPlayer.logger.debug("fast seek - $pos")
            if(isLoading) {
                return
            }
            if(!mFastMode) {
                com.michael.video.AmvExoVideoPlayer.logger.debug("switch to fast seek")
                mFastMode = true
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
            clippingSeekTo(pos)
        }

        fun exactSeek(pos:Long) {
            com.michael.video.AmvExoVideoPlayer.logger.debug("exact seek - $pos")
            if(mFastMode) {
                com.michael.video.AmvExoVideoPlayer.logger.debug("switch to exact seek")
                mFastMode = false
                player.setSeekParameters(SeekParameters.EXACT)
            }
            clippingSeekTo(pos)
        }

        val isSeeking:Boolean
            get() = mSeeking
    }
    private var seekManager = SeekManager()

}
