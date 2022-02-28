/**
 * 基本プレーヤー用コントローラービュー
 *
 * @author M.TOYOTA 2018.07.05 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import androidx.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.viewModelScope
import com.michael.utils.FuncyListener1
import com.michael.utils.VectorDrawableTinter
import com.michael.video.viewmodel.AmvFrameListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToLong


class AmvVideoController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context,attrs,defStyleAttr), IAmvMarkerEditableController, IAmvMarkerEditableController.IMarkerEditor {

    // region Constants

    companion object {
        val logger = AmvSettings.logger
        private const val FRAME_COUNT = 15            // フレームサムネイルの数
        private const val FRAME_HEIGHT_IN_DP = 50f         // フレームサムネイルの高さ(dp)
        private const val LISTENER_NAME = "VCT"
    }

    private val mFrameHeight = context.dp2px(FRAME_HEIGHT_IN_DP)

    // endregion

    // region Binding -- Controls

    private inner class Controls {
        val root: ViewGroup by lazy {
            findViewById<ViewGroup>(R.id.vct_controllerRoot)
        }
        val playButton: ImageButton by lazy {
            findViewById<ImageButton>(R.id.vct_playButton)
        }
        val playButtonMini: ImageButton by lazy {
            findViewById<ImageButton>(R.id.vct_playButton2)
        }
        val backButton: ImageButton by lazy {
            findViewById<ImageButton>(R.id.vct_backButton)
        }
        val forwardButton: ImageButton by lazy {
            findViewById<ImageButton>(R.id.vct_forwardButton)
        }
        val markButton: ImageButton by lazy {
            findViewById<ImageButton>(R.id.vct_markButton)
        }
        val pinpButton: ImageButton by lazy {
            findViewById<ImageButton>(R.id.vct_pinpButton)
        }
        val fullButton: ImageButton by lazy {
            findViewById<ImageButton>(R.id.vct_fullscreenButton)
        }
        val buttonsGroup:LinearLayout by lazy {
            findViewById<LinearLayout>(R.id.vct_buttons)
        }
        val showFramesButton: ImageButton by lazy {
            findViewById<ImageButton>(R.id.vct_showFramesButton)
        }
        val markerView: AmvMarkerView by lazy {
            findViewById<AmvMarkerView>(R.id.vct_markerView)
        }
        val frameList: AmvFrameListView by lazy {
            findViewById<AmvFrameListView>(R.id.vct_frameList)
        }
        val slider: AmvSlider by lazy {
            findViewById<AmvSlider>(R.id.vct_slider)
        }
        val counterBar: TextView by lazy {
            findViewById<TextView>(R.id.vct_counterBar)
        }
        val drPlay: Drawable? by lazy {
            context.getDrawable(R.drawable.ic_play)
        }
        val drPause: Drawable? by lazy {
            context.getDrawable(R.drawable.ic_pause)
        }
        val drShowFrameOff: Drawable? by lazy {
            context.getDrawable(R.drawable.ic_frames)
        }
        val drShowFrameOn: Drawable? by lazy {
            VectorDrawableTinter.tintDrawable(context.getDrawable(R.drawable.ic_frames)!!, ContextCompat.getColor(context, R.color.trimming_sel))
        }

        val actualPlayButton:ImageButton
            get() = if(mMinimalMode) playButtonMini else playButton

        /**
         * ボタンの有効/無効の変更ヘルパメソッド
         */
        fun ImageButton.enable(enabled:Boolean) {
            if(enabled) {
                this.alpha = 1f
                this.isClickable = true
            } else {
                this.alpha = 0.4f
                this.isClickable = false
            }
        }

        /**
         * ReadOnly 属性が変化したときの更新処理
         */
        fun updateReadOnly() {
            markButton.enable(!models.isReadOnly && models.isPlayerPrepared)
        }

        /**
         * ShowingFrame 属性が変化したときの更新処理
         */
        fun updateShowingFrame() {
            val show = models.showingFrames
            if(show) {
                showFramesButton.setImageDrawable(drShowFrameOn)
                frameList.visibility = View.VISIBLE
            } else {
                showFramesButton.setImageDrawable(drShowFrameOff)
                frameList.visibility = View.GONE
            }
        }

        /**
         * PinP/FullScreen Player の表示状態に応じて、PinP/Fullボタンの有効/無効を切り替える
         */
        fun updatePinPButton(state:AmvFullscreenActivity.State = AmvFullscreenActivity.currentActivityState) {
            val ready = models.isPlayerPrepared
            when(state) {
                AmvFullscreenActivity.State.FULL-> {
                    pinpButton.enable(false)
                    fullButton.enable(false)
                }
                AmvFullscreenActivity.State.PINP-> {
                    pinpButton.enable(false)
                    fullButton.enable(false)
                }
                else -> {
                    pinpButton.enable(ready)
                    fullButton.enable(ready)
                }
            }
        }

        /**
         * isPlayerPrepared 属性が変化したときの更新処理
         */
        fun updatePlayerPrepared() {
            val ready = models.isPlayerPrepared
            val buttons = arrayOf(actualPlayButton, backButton, forwardButton)
            buttons.forEach {
                it.enable(ready)
            }
            updatePinPButton()
            // markButtonは別管理
            updateReadOnly()
        }

        /**
         * isPlaying 属性が変化したときの更新処理
         */
        fun updatePlaying() {
            val isPlaying = models.isPlaying
            if(isPlaying) {
                actualPlayButton.setImageDrawable(drPause)
            } else {
                actualPlayButton.setImageDrawable(drPlay)
            }
        }

        /**
         * カウンター（再生位置）が変化したときの更新処理
         */
        fun updateCounter() {
            val duration = models.duration
            var pos = models.currentPosition
            if(pos<0) {
                pos = 0
            }

            val total = AmvTimeSpan(duration)
            val current = AmvTimeSpan(if(pos>duration) duration else pos)
            counterBar.text = when {
                                total.hours > 0 -> "${current.formatH()} / ${total.formatH()}"
                                total.minutes > 0 -> "${current.formatM()} / ${total.formatM()}"
                                else -> "${current.formatS()} / ${total.formatS()}" }
        }

        /**
         * コントロールの初期化
         */
        fun initialize() {
            root.minimumWidth = models.minControllerWidth

            // slider

            slider.isSaveFromParentEnabled = false         // スライダーの状態は、AmvVideoController側で復元する
            slider.currentPositionChanged.set(this@AmvVideoController::onCurrentPositionChanged)

            if(!AmvSettings.isPinPAvailable(context)) {
                pinpButton.visibility = View.GONE
            }

            // buttons
            if(mMinimalMode) {
                arrayOf(markerView, buttonsGroup, counterBar, showFramesButton).forEach {
                    it.visibility = GONE
                }
                playButtonMini.visibility = VISIBLE
                playButtonMini.setOnClickListener(this@AmvVideoController::onPlayClicked)
            } else {
                playButton.setOnClickListener(this@AmvVideoController::onPlayClicked)
                backButton.setOnClickListener(this@AmvVideoController::onPrevMarker)
                forwardButton.setOnClickListener(this@AmvVideoController::onNextMarker)
                markButton.setOnClickListener(this@AmvVideoController::onAddMarker)
                pinpButton.setOnClickListener(this@AmvVideoController::onPinP)
                fullButton.setOnClickListener(this@AmvVideoController::onFullScreen)
                showFramesButton.setOnClickListener(this@AmvVideoController::onShowFramesClick)
            }

            // フレーム一覧のドラッグ操作をSliderのドラッグ操作と同様に扱うための小さな仕掛け
            frameList.touchFriendListener.set(controls.slider::onTouchAtFriend)

            // MarkerView
            markerView.markerSelectedListener.set { position, _ ->
                updateSeekPosition(position.toLong(), seek = true, slider = true)
            }
            markerView.markerAddedListener.set { position, clientData ->
                mMarkerListener?.onMarkerAdded(this@AmvVideoController, position, clientData)
            }
            markerView.markerRemovedListener.set { position, clientData ->
                mMarkerListener?.onMarkerRemoved(this@AmvVideoController, position, clientData)
            }
            markerView.markerContextQueryListener.set { position, x, clientData ->
                mMarkerListener?.onMarkerContextMenu(this@AmvVideoController, position, x, clientData)
            }

            // 初回の更新
            updateShowingFrame()
            updatePlayerPrepared()
            updatePlaying()
            updateCounter()

            root.setOnClickListener {
                // コントローラーの隙間をタップしたときにプレーヤーが非アクティブになるのを回避するため、タップイベントを消費しておく。
            }
        }
    }
    private val controls = Controls()

    // endregion

    // region Binding -- Models

    private inner class Models {

        var isPlaying= false
            set(v) {
                if(v!=field) {
                    field = v
                    controls.updatePlaying()
                }
            }

        var isReadOnly: Boolean = false
            set(v) {
                if (field != v) {
                    field = v
                    controls.updateReadOnly()
                }
            }

        var showingFrames: Boolean = false
            set(v) {
                if (field != v) {
                    field = v
                    controls.updateShowingFrame()
                    frameVisibilityChanged.invoke(v)
                }
            }

        private val buttonCount
            get() = 6 + if(AmvSettings.isPinPAvailable(context)) 1 else 0

        val minControllerWidth: Int by lazy {
            context.dp2px(40*buttonCount) // ボタンの幅ｘボタンの数
        }

        var isVideoInfoPrepared: Boolean = false
        var isDurationAvailable: Boolean = false

        var isPlayerPrepared: Boolean = false
            set(v) {
                if (field != v) {
                    field = v
                    controls.updatePlayerPrepared()
                }
            }


        var playerState: IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None
            set(state) {
                if (field != state) {
                    field = state
                    logger.debug("PlayState: $state")

                    when (state) {
                        IAmvVideoPlayer.PlayerState.Playing -> {
                            // 再生中は定期的にスライダーの位置を更新する
                            startSeekLoop()
                        }
                        IAmvVideoPlayer.PlayerState.None -> isPlayerPrepared = false
                        IAmvVideoPlayer.PlayerState.Error -> {
                            isPlayerPrepared = false
                        }
                        IAmvVideoPlayer.PlayerState.Paused -> {
                            if(mStopAt>=0) {
                                mPlayer.seekTo(mStopAt)
                                mStopAt = -1L
                            }
                        }
                        else -> {
                        }
                    }
                    isPlaying =  playerState == IAmvVideoPlayer.PlayerState.Playing
                }
            }

        var currentPosition: Long = -1L
            set(v) {
                if (field != v) {
                    field = v
                    controls.updateCounter()
                }
            }

        var duration: Long = 0L
            set(v) {
                if(v!=field) {
                    field = v
                    controls.updateCounter()
                    controls.slider.resetWithValueRange(v, true)      // スライダーを初期化
                    controls.slider.currentPosition = mPlayer.seekPosition
                    isDurationAvailable = true
                }
            }
    }
    private val models = Models()

    // endregion

    // region Private implements

    private val mSeekLoop = object : Runnable {
        override fun run() {
            if(!mPausingOnTracking) {
                val pos = mPlayer.seekPosition
                updateSeekPosition(pos, seek = false, slider = true)
                if(mStopAt in 1..pos) {     // if(mStopAt>0L && pos>=mStopAt) {
                    mPlayer.pause()
                }
            }
            if(mPlayer.isPlayingOrReservedToPlay) {
                mHandler.postDelayed(this, 50)     // Win版は10msで動かしていたが、Androidでは動画が動かなくなるので、200msくらいにしておく。ガタガタなるけど。
            }
        }
    }

    private fun startSeekLoop() {
        mHandler.post(mSeekLoop)
    }

    private lateinit var mPlayer:IAmvVideoPlayer
    private val mHandler = Handler(Looper.getMainLooper())
    private val mFrameListViewModel : AmvFrameListViewModel?
    private var mPausingOnTracking = false       // スライダー操作中は再生を止めておいて、操作が終わったときに必要に応じて再生を再開する
    private var mMinimalMode = false
    private var mSource: IAmvSource? = null


    init {
        LayoutInflater.from(context).inflate(R.layout.video_controller, this)
        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvVideoController,defStyleAttr,0)
        try {
            mMinimalMode = sa.getBoolean(R.styleable.AmvVideoController_minimal, false)
            mFrameListViewModel = AmvFrameListViewModel.getInstance(this)
        } finally {
            sa.recycle()
        }

        controls.initialize()
    }

    var showingFrames:Boolean
        get() = models.showingFrames
        set(v) { models.showingFrames = v }
    val frameVisibilityChanged =  FuncyListener1<Boolean, Unit>()

    private fun onFullScreenActivityStateChanged(state:AmvFullscreenActivity.State, @Suppress("UNUSED_PARAMETER") source:IAmvSource?) {
        controls.updatePinPButton(state)
    }

    private var mFrameListObserver: Observer<AmvFrameListViewModel.IFrameListInfo>? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mFrameListObserver = mFrameListViewModel?.setObserver(this, ::updateFrameListByViewModel)
        AmvFullscreenActivity.onResultListener.add(null, this::onBackFromFullscreen)
        AmvFullscreenActivity.stateListener.add(null, this::onFullScreenActivityStateChanged)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mMarkerListener = null
        mFrameListViewModel?.resetObserver(mFrameListObserver)
        mFrameListViewModel?.clear()
        mFrameListObserver = null
        setSource(null)
        AmvFullscreenActivity.onResultListener.remove(this::onBackFromFullscreen)
        AmvFullscreenActivity.stateListener.remove(this::onFullScreenActivityStateChanged)
    }

    /**
     * 全画面モードから復帰するときのイベントリスナー
     * 再生状態、シーク位置を復元する
     * Fixed: classroom#3217
     */
    private fun onBackFromFullscreen(intent:Intent) {
        val src = intent.getParcelableExtra<IAmvSource>(AmvFullscreenActivity.KEY_SOURCE)
        if(src == mPlayer.source) {
            val pos = intent.getLongExtra(AmvFullscreenActivity.KEY_POSITION, 0L)
            val playing = intent.getBooleanExtra(AmvFullscreenActivity.KEY_PLAYING, false)
            if(pos<models.duration) {
                mPlayer.seekTo(pos)
            }
            if(playing) {
                mPlayer.play()
            }
        }
    }


    /**
     * ソースが切り替わったタイミングで、フレームサムネイルリストを作成する
     */
    private fun extractFrameOnSourceChanged(source: IAmvSource) {
        setSource(source)
        mFrameListViewModel?.viewModelScope?.launch {
            val file = source.getFileAsync()
            withContext(Dispatchers.Main) {
                if (null != file) {
                    models.isVideoInfoPrepared = false
                    mFrameListViewModel.let { viewModel ->
                        viewModel.frameListInfo.value?.let { info ->
                            if (!mFrameListViewModel.extractFrame(file, FRAME_COUNT, FitMode.Height, 0f, mFrameHeight)) {
                                // 抽出条件が変更されていない場合はfalseを返してくるのでキャッシュから構築する
                                updateFrameListByViewModel(info)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * ViewModel経由でフレームサムネイルリストを更新する
     */
    private fun updateFrameListByViewModel(info: AmvFrameListViewModel.IFrameListInfo) {
        if(info.error==null && info.status != AmvFrameListViewModel.IFrameListInfo.Status.INIT && info.duration>0L) {
            if(!models.isVideoInfoPrepared) {
                models.duration = info.duration
                val thumbnailSize = info.size
                controls.frameList.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                controls.frameList.totalRange = info.duration
                initMarkerView(info.duration)

                models.isVideoInfoPrepared = true
            }
            if(info.count>0) {
                controls.frameList.setFrames(info.frameList)
            }
        }
    }

    /**
     * 各コントロール（Player/Slider/FrameList/Counter）のシーク位置を揃える
     */
    private fun updateSeekPosition(pos:Long, seek:Boolean, slider:Boolean) {
        if(seek) {
            mPlayer.seekTo(pos)
        }
        if(slider && models.isDurationAvailable) {
            controls.slider.currentPosition = pos
        }
        if(models.isVideoInfoPrepared) {
            controls.frameList.position = pos
        }
        models.currentPosition = pos
    }

    // endregion


    // region IAmvVideoController i/f implementation

    /**
     * VideoPlayerをコントローラーに接続する
     */
    override fun setVideoPlayer(player:IAmvVideoPlayer) {
        mPlayer = player
        models.playerState = player.playerState

        // Player Event
        mPlayer.apply {
            // 再生状態が変化したときのイベント
            playerStateChangedListener.add(LISTENER_NAME) { _, state ->
                if(!mPausingOnTracking) {
                    models.playerState = state
                }
            }

            // 動画の画面サイズが変わったときのイベント
            sizeChangedListener.add(LISTENER_NAME) { _, width, _ ->
                // layout_widthをBindingすると、どうしてもエラーになるので、直接変更
                controls.root.setLayoutWidth(max(width, models.minControllerWidth))
            }

            // プレーヤー上のビデオの読み込みが完了したときのイベント
            videoPreparedListener.add(LISTENER_NAME) { mp, duration ->
                // models.duration = duration
                models.duration = duration
                models.currentPosition = mp.seekPosition
                models.isPlayerPrepared = true
            }

            // 動画ソースが変更されたときのイベント
            sourceChangedListener.add(LISTENER_NAME) { _, source ->
                models.isPlayerPrepared = false
                models.isVideoInfoPrepared = false
                models.isDurationAvailable = false
                extractFrameOnSourceChanged(source)
            }

            seekCompletedListener.add(LISTENER_NAME) { _, pos ->
                updateSeekPosition(pos, seek = false, slider = true)
            }
        }
    }

    private fun setSource(newSource:IAmvSource?) {
        newSource?.addRef()
        val oldSource = synchronized(this) {
            val old = mSource
            mSource = newSource
            old
        }
        oldSource?.release()
    }

    override fun dispose() {
        mFrameListViewModel?.clear()
        setSource(null)
    }

    /**
     * リードオンリーモードの取得・設定
     */
    override var isReadOnly: Boolean
        get() = models.isReadOnly
        set(v) { models.isReadOnly=v }

    override val isSeekingBySlider: Boolean
        get() = mPausingOnTracking

    // region IMarkerEditor i/f

    override val markerEditor: IAmvMarkerEditableController.IMarkerEditor
        get() = this

    /**
     * しおりの管理
     */
    private var mMarkerWork:Collection<Double>? = null

    override fun setMarkers(markers: Collection<Double>) {
        if(models.isVideoInfoPrepared) {
            controls.markerView.setMarkers(markers)
        } else {
            mMarkerWork=markers
        }
    }

    /**
     * しおりビューの初期化
     */
    private fun initMarkerView(duration:Long) {
        controls.markerView.resetWithTotalRange(duration)
        if(null!=mMarkerWork) {
            controls.markerView.setMarkers(mMarkerWork!!)
            mMarkerWork = null
        }
    }

    override fun addMarker(position: Double, clientData: Any?) {
        controls.markerView.addMarker(position.roundToLong(), clientData)
    }

    override fun removeMarker(position: Double, clientData: Any?) {
        controls.markerView.removeMarker(position.roundToLong(), clientData)
    }

    private var mMarkerListener:IAmvMarkerEditableController.IMarkerListener? = null
    override fun setMarkerListener(listener: IAmvMarkerEditableController.IMarkerListener?) {
        mMarkerListener = listener
    }

    override fun setHighLightMarker(position: Double?) {
        if(null==position) {
            controls.markerView.resetHighLightMarker()
        } else {
            controls.markerView.setHighLightMarker(position.roundToLong())
        }
    }

    override val view: View
        get() = controls.markerView

    // endregion

    /**
     * フレームリストのコンテント幅（スクローラー内コンテントの幅）が確定したときにコールバックされる
     */
//    override val contentWidthChanged = IAmvVideoController.ContentWidthChanged()

    // endregion

    // region Event handlers

    fun onPlayClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        when(mPlayer.playerState) {
            IAmvVideoPlayer.PlayerState.Paused -> mPlayer.play()
            IAmvVideoPlayer.PlayerState.Playing -> mPlayer.pause()
            else -> {}
        }
    }

    fun onPrevMarker(@Suppress("UNUSED_PARAMETER") view: View) {
        controls.markerView.prevMark(controls.slider.currentPosition, null)
    }

    fun onNextMarker(@Suppress("UNUSED_PARAMETER") view: View) {
        controls.markerView.nextMark(controls.slider.currentPosition, null)
    }

    fun onAddMarker(@Suppress("UNUSED_PARAMETER") view: View) {
        addMarker(controls.slider.currentPosition.toDouble(), this)
    }

    fun onShowFramesClick(@Suppress("UNUSED_PARAMETER") view:View) {
        models.showingFrames = !models.showingFrames
    }

    fun onPinP(@Suppress("UNUSED_PARAMETER") view: View) {
        showFullScreenViewer(true)
    }
    fun onFullScreen(@Suppress("UNUSED_PARAMETER") view: View) {
        showFullScreenViewer(false)
    }

    private fun showFullScreenViewer(pinp:Boolean) {
        val activity = getActivity()
        val source = mPlayer.source
        if(null!=activity && null!=source) {
            val seekPos = mPlayer.seekPosition
            val position = if(seekPos<models.duration) seekPos else 0L
            val clipping = mPlayer.clip
            val intent = Intent(activity, AmvFullscreenActivity::class.java)
            intent.putExtra(AmvFullscreenActivity.KEY_SOURCE, source)
            intent.putExtra(AmvFullscreenActivity.KEY_POSITION, position)
            intent.putExtra(AmvFullscreenActivity.KEY_PLAYING, models.isPlaying)
            intent.putExtra(AmvFullscreenActivity.KEY_PINP, pinp)
            if(pinp) {
                val vs = mPlayer.videoSize
                if(vs.width>0 && vs.height>0) {
                    intent.putExtra(AmvFullscreenActivity.KEY_VIDEO_WIDTH, vs.width)
                    intent.putExtra(AmvFullscreenActivity.KEY_VIDEO_HEIGHT, vs.height)
                }
            }
            if(null!=clipping) {
                intent.putExtra(AmvFullscreenActivity.KEY_CLIP_START, clipping.start)
                intent.putExtra(AmvFullscreenActivity.KEY_CLIP_END, clipping.end)
            }
            activity.startActivity(intent)
            mPlayer.pause()
        }
    }

    // Sliderの操作
    fun onCurrentPositionChanged(@Suppress("UNUSED_PARAMETER") caller:AmvSlider, position:Long, dragState: AmvSlider.SliderDragState) {
//        UtLogger.debug("CurrentPosition: $position ($dragState)")
        when(dragState) {
            AmvSlider.SliderDragState.BEGIN-> {
                mPausingOnTracking = models.isPlaying
                mPlayer.pause()
                mPlayer.setFastSeekMode(true)
//                UtLogger.debug("AmvVideoController: drag begin : pausing on tracking = $mPausingOnTracking")
            }
            AmvSlider.SliderDragState.MOVING->{
                updateSeekPosition(position, seek = true, slider = false)
            }
            AmvSlider.SliderDragState.END-> {
                mPlayer.setFastSeekMode(false)
                updateSeekPosition(position, seek = true, slider = false)
//                UtLogger.debug("AmvVideoController: drag end   : pausing on tracking = $mPausingOnTracking")

                if(mPausingOnTracking) {
                    mPlayer.play()
                    mPausingOnTracking = false
                    startSeekLoop()
                }
            }
            else -> {
            }
        }
        models.currentPosition = position
    }

    // endregion

    private var mStopAt: Long = -1L
    fun startAt(pos:Long) {
        mPlayer.play()
        mPlayer.seekTo(pos)
    }
    fun stopAt(pos:Long) {
        when(models.playerState) {
            IAmvVideoPlayer.PlayerState.Playing-> mStopAt = pos
            else -> mPlayer.seekTo(pos)
        }
    }

}