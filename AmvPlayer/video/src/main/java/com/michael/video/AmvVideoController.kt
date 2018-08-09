/**
 * 基本プレーヤー用コントローラービュー
 *
 * @author M.TOYOTA 2018.07.05 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.michael.utils.UtLogger
import com.michael.utils.VectorDrawableTinter
import com.michael.video.viewmodel.AmvFrameListViewModel
import java.io.File


class AmvVideoController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : LinearLayout(context,attrs,defStyleAttr), IAmvVideoController {

    // region Constants

    companion object {
        private const val FRAME_COUNT = 30            // フレームサムネイルの数
        private const val FRAME_HEIGHT = 160f         // フレームサムネイルの高さ(dp)
        private const val LISTENER_NAME = "videoController"
    }

    // endregion

    // region Binding -- Controls

    private inner class Controls {
        val root: RelativeLayout by lazy {
            findViewById<RelativeLayout>(R.id.vct_controllerRoot)
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
        val drPlay: Drawable by lazy {
            context.getDrawable(R.drawable.ic_play)
        }
        val drPause: Drawable by lazy {
            context.getDrawable(R.drawable.ic_pause)
        }
        val drShowFrameOff: Drawable by lazy {
            context.getDrawable(R.drawable.ic_frames)
        }
        val drShowFrameOn: Drawable by lazy {
            VectorDrawableTinter.tintDrawable(context.getDrawable(R.drawable.ic_frames), ContextCompat.getColor(context, R.color.trimming_sel))
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
         * isPlayerPrepared 属性が変化したときの更新処理
         */
        fun updatePlayerPrepared() {
            val ready = models.isPlayerPrepared
            val buttons = arrayOf(actualPlayButton, backButton, forwardButton, pinpButton, fullButton)
            buttons.forEach {
                it.enable(ready)
            }
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
                updateSeekPosition(position, true, true)
            }
            markerView.markerAddedListener.set { _, _ ->
            }
            markerView.markerRemovedListener.set { _, _ ->
            }
            markerView.markerContextQueryListener.set { _, _ ->
            }

            // 初回の更新
            updateShowingFrame()
            updatePlayerPrepared()
            updatePlaying()
            updateCounter()
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

//        val isReady: Boolean
//            get() {
//                return when (playerState) {
//                    IAmvVideoPlayer.PlayerState.Playing, IAmvVideoPlayer.PlayerState.Paused -> true
//                    else -> false
//                }
//            }

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
                }
            }

        val minControllerWidth: Int by lazy {
            context.dp2px(325)
        }

        var isVideoInfoPrepared: Boolean = false

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
                    UtLogger.debug("PlayState: $state")

                    when (state) {
                        IAmvVideoPlayer.PlayerState.Playing -> {
                            // 再生中は定期的にスライダーの位置を更新する
                            mHandler.post(mSeekLoop)
                        }
                        IAmvVideoPlayer.PlayerState.None -> isPlayerPrepared = false
                        IAmvVideoPlayer.PlayerState.Error -> {
                            isPlayerPrepared = false
                            restoringData?.onFatalError()
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
                field = v
                controls.updateCounter()
            }
    }
    private val models = Models()

    // endregion

    // region Private implements

    private val mSeekLoop = object : Runnable {
        override fun run() {
            if(!mPausingOnTracking) {
                updateSeekPosition(mPlayer.seekPosition, false, true)
            }
            if(models.playerState==IAmvVideoPlayer.PlayerState.Playing) {
                mHandler.postDelayed(this, 100)     // Win版は10msで動かしていたが、Androidでは動画が動かなくなるので、200msくらいにしておく。ガタガタなるけど。
            }
        }
    }
    private lateinit var mPlayer:IAmvVideoPlayer
    private val mHandler = Handler()
    private var mFrameExtractor :AmvFrameExtractor? = null
    private val mFrameListViewModel : AmvFrameListViewModel?
    private var mPausingOnTracking = false       // スライダー操作中は再生を止めておいて、操作が終わったときに必要に応じて再生を再開する
    private var mMinimalMode = false


    init {
        LayoutInflater.from(context).inflate(R.layout.video_controller, this)

        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvVideoController,defStyleAttr,0)
        try {
            mMinimalMode = sa.getBoolean(R.styleable.AmvVideoController_minimal, false)

            val enableViewModel = sa.getBoolean(R.styleable.AmvVideoController_frameCache, true)
            mFrameListViewModel = if(enableViewModel) {
                AmvFrameListViewModel.registerToView(this, this::updateFrameListByViewModel)?.apply {
                    setSizingHint(FitMode.Height, 0f, FRAME_HEIGHT)
                    setFrameCount(if(mMinimalMode) 0 else FRAME_COUNT)
                }

            } else {
                null
            }
        } finally {
            sa.recycle()
        }

        controls.initialize()
    }

    /**
     * ソースが切り替わったタイミングで、フレームサムネイルリストを作成する
     */
    private fun extractFrameOnSourceChanged(source: File) {
        models.duration = 0L  // ViewModelから読み込むとき、Durationがゼロかどうかで初回かどうか判断するので、ここでクリアする
        if(null!=mFrameListViewModel) {
            val info = mFrameListViewModel.frameListInfo.value!!
            if(source != info.source || null!=info.error) {
                // ソースが変更されているか、エラーが発生しているときはフレーム抽出を実行
                mFrameListViewModel.cancel()
                mFrameListViewModel.extractFrame(source)
            } else {
                // それ以外はViewModel（キャッシュ）から読み込む
                updateFrameListByViewModel(info)
            }
        } else {
            // フレームサムネイルを列挙する
            mFrameExtractor = AmvFrameExtractor().apply {
                setSizingHint(FitMode.Height, 0f, FRAME_HEIGHT)
                onVideoInfoRetrievedListener.add(null) {
                    UtLogger.debug("AmvFrameExtractor:duration=${it.duration} / ${it.videoSize}")
                    models.duration = it.duration
                    val thumbnailSize = it.thumbnailSize

                    controls.frameList.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                    controls.slider.resetWithValueRange(it.duration, true)      // スライダーを初期化
                    controls.frameList.totalRange = it.duration
                    controls.markerView.resetWithTotalRange(it.duration)

                    models.isVideoInfoPrepared = true
                    restoringData?.tryRestoring()

//                    contentWidthChanged.invoke(this@AmvVideoController, controls.frameList.contentWidth)
                }
                onThumbnailRetrievedListener.add(null) { _, index, bmp ->
                    UtLogger.debug("AmvFrameExtractor:Bitmap($index): width=${bmp.width}, height=${bmp.height}")
                    controls.frameList.add(bmp)
                }
                onFinishedListener.add(null) { _, _ ->
                    // サブスレッドの処理がすべて終了しても、UIスレッド側でのビットマップ追加処理待ちになっていることがあり、
                    // このイベントハンドラから、dispose()してしまうと、待ち中のビットマップが破棄されてしまう。
                    mHandler.post {
                        // リスナーの中でdispose()を呼ぶのはいかがなものかと思われるので、次のタイミングでお願いする
                        dispose()
                        mFrameExtractor = null
                    }
                }
                extract(source, FRAME_COUNT)
            }
        }
    }

    /**
     * ViewModel経由でフレームサムネイルリストを更新する
     */
    private fun updateFrameListByViewModel(info: AmvFrameListViewModel.IFrameListInfo) {
        if(null!=info.error) {
            restoringData?.onFatalError()
        } else if(info.status != AmvFrameListViewModel.IFrameListInfo.Status.INIT && info.duration>0L) {
            if(models.duration==0L) {
                models.duration = info.duration
                val thumbnailSize = info.size
                controls.frameList.prepare(FRAME_COUNT, thumbnailSize.width, thumbnailSize.height)
                controls.slider.resetWithValueRange(info.duration, true)      // スライダーを初期化
                controls.frameList.totalRange = info.duration
                controls.markerView.resetWithTotalRange(info.duration)

                models.isVideoInfoPrepared = true
                restoringData?.tryRestoring()

//                contentWidthChanged.invoke(this@AmvVideoController, controls.frameList.contentWidth)
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
        if(slider) {
            controls.slider.currentPosition = pos
        }
        controls.frameList.position = pos
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
                controls.root.setLayoutWidth(Math.max(width, models.minControllerWidth))
            }

            // プレーヤー上のビデオの読み込みが完了したときのイベント
            videoPreparedListener.add(LISTENER_NAME) { mp, _ ->
                models.currentPosition = mp.seekPosition
                models.isPlayerPrepared = true
                restoringData?.tryRestoring()
            }
            // 動画ソースが変更されたときのイベント
            sourceChangedListener.add(LISTENER_NAME) { _, source ->
                models.isPlayerPrepared = false
                models.isVideoInfoPrepared = false
                extractFrameOnSourceChanged(source)
            }
        }
    }

    /**
     * リードオンリーモードの取得・設定
     */
    override var isReadOnly: Boolean
        get() = models.isReadOnly
        set(v) { models.isReadOnly=v }

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
        controls.markerView.addMarker(controls.slider.currentPosition, null)
    }

    fun onShowFramesClick(@Suppress("UNUSED_PARAMETER") view:View) {
        models.showingFrames = !models.showingFrames
    }

    fun onPinP(@Suppress("UNUSED_PARAMETER") view: View) {

    }
    fun onFullScreen(@Suppress("UNUSED_PARAMETER") view: View) {

    }

    // Sliderの操作
    fun onCurrentPositionChanged(@Suppress("UNUSED_PARAMETER") caller:AmvSlider, position:Long, dragState: AmvSlider.SliderDragState) {
        UtLogger.debug("CurrentPosition: $position ($dragState)")
        when(dragState) {
            AmvSlider.SliderDragState.BEGIN-> {
                mPausingOnTracking = models.isPlaying
                mPlayer.pause()
                mPlayer.setFastSeekMode(true)
            }
            AmvSlider.SliderDragState.MOVING->{
                updateSeekPosition(position, true, false)
            }
            AmvSlider.SliderDragState.END-> {
                mPlayer.setFastSeekMode(false)
                updateSeekPosition(position, true, false)

                if(mPausingOnTracking) {
                    mPlayer.play()
                    mPausingOnTracking = false
                }
            }
            else -> {
            }
        }
        models.currentPosition = position
    }

    // endregion

    // region Saving / Restoring

    /**
     * リストア中データ
     */
    private inner class RestoringData(val data:SavedData) {
        private val isPlayerPrepared
            get() = models.isPlayerPrepared
        private val isVideoInfoPrepared
            get() = models.isVideoInfoPrepared

        private var isFirstRestored = false
        private var isPlayerRestored = false
        private var isSliderRestored = false

        fun onFatalError() {
            UtLogger.error("AmvTrimmingController: abort restoring.")
            this@AmvVideoController.restoringData = null
        }

        fun tryRestoring() {
            if(!isFirstRestored) {
                isFirstRestored = true
                models.showingFrames = data.showingFrames
            }
            if(isPlayerPrepared && !isPlayerRestored) {
                mPlayer.seekTo(data.seekPosition)
                if(data.isPlaying) {
                    mPlayer.play()
                }
                isPlayerRestored = true
            }
            if(isVideoInfoPrepared && !isSliderRestored) {
                controls.slider.currentPosition = data.seekPosition
                controls.frameList.position = data.seekPosition
                controls.markerView.markers = data.markers
                isSliderRestored = true
            }

            if(isPlayerRestored && isSliderRestored) {
                this@AmvVideoController.restoringData = null
            }
        }
    }
    private var restoringData: RestoringData? = null

    /**
     * 保存データの中身クラス
     */
    internal class SavedData(val seekPosition:Long, val isPlaying:Boolean, val showingFrames:Boolean, val markers:ArrayList<Long>)

    override fun onSaveInstanceState(): Parcelable {
        UtLogger.debug("LC-View: onSaveInstanceState")
        val parent =  super.onSaveInstanceState()
        return SavedState(parent, restoringData?.data ?: SavedData(controls.slider.currentPosition, models.isPlaying, models.showingFrames, controls.markerView.markers))
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        UtLogger.debug("LC-View: onRestoreInstanceState")
        if(state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            restoringData = RestoringData(state.savedData)
            restoringData?.tryRestoring()
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : View.BaseSavedState {

        val savedData: SavedData

        /**
         * Constructor called from [AmvSlider.onSaveInstanceState]
         */
        constructor(superState: Parcelable, savedData:SavedData) : super(superState) {
            this.savedData = savedData
        }

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(parcel: Parcel) : super(parcel) {
            @Suppress("UNCHECKED_CAST")
            savedData = SavedData(parcel.readLong(), parcel.readInt() == 1, parcel.readInt()==1, parcel.readArrayList(Long::class.java.classLoader) as ArrayList<Long>)
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeLong(savedData.seekPosition)
            parcel.writeInt(if(savedData.isPlaying) 1 else 0)
            parcel.writeInt(if(savedData.showingFrames) 1 else 0)
            parcel.writeList(savedData.markers)
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