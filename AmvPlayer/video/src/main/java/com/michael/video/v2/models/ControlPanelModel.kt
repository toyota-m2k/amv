package com.michael.video.v2.models

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.annotation.MainThread
import com.michael.video.v2.common.AmvSettings
import com.michael.video.v2.common.IAmvSource
import com.michael.video.v2.core.AmvFrameExtractor
import com.michael.video.v2.util.*
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.utils.UtLazyResetableValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * コントローラーの共通実装
 * ControlPanelModel              基本実装（AmvFrameSelectorViewで使用）
 *   + FullControlPanelModel      フル機能プレイヤー（AmvPlayerUnitView）用
 *   + TrimmingControlPanelModel  トリミング画面(AMvTrimmingPlayerView)用
 */
open class ControlPanelModel(
    val playerModel: PlayerModel,
    val thumbnailCount:Int,
    thumbnailHeightInDp:Int
) : Closeable {
    companion object {
        val logger get() = AmvSettings.logger
        fun create(context: Context, thumbnailCount: Int, thumbnailHeight: Int): ControlPanelModel {
            val playerViewModel = PlayerModel(context)
            return ControlPanelModel(playerViewModel, thumbnailCount, thumbnailHeight)
        }
    }

    /**
     * コントローラーのCoroutineScope
     * playerModel.scope を継承するが、ライフサイクルが異なるので、新しいインスタンスにしておく。
     */
    val scope:CoroutineScope = CoroutineScope(playerModel.scope.coroutineContext)

    /**
     * ApplicationContext参照用
     */
    val context: Context get() = playerModel.context

    /**
     * サムネイルの高さ（定数）
     * FullControlPanel --> 50dp
     * それ以外 --> 80dp
     */
    private val thumbnailHeight = context.dp2px(thumbnailHeightInDp)

    /**
     * フレーム（サムネイル）リストの管理クラス
     */
    class FrameList(val count:Int, val height:Int, val context:Context, val scope:CoroutineScope) : Closeable {
        val extractor = AmvFrameExtractor(AmvFitter(FitMode.Height, MuSize(0f,height.toFloat())),scope)
        private var frameListCache:MutableList<Bitmap>? = null
        val isReady = MutableStateFlow<Boolean>(false)
        val contentWidth = MutableStateFlow(0)     // AmvFrameListViewのコンテントの幅
        private var source:Uri? = null

        fun isSameSource(uri:Uri):Boolean {
            return source == uri
        }

        fun setUri(uri: Uri) {
            if(isSameSource(uri)) {
                return
            }
            reset()
            source = uri
            isReady.value = true
        }

        private suspend fun openSource() {
            if(extractor.open(source ?: throw IllegalStateException("source is null."), context)) {
                contentWidth.value = extractor.thumbnailSize().width * count
            }
        }

        suspend fun getThumbnailSize(): Size {
            openSource()
            return extractor.thumbnailSize()
        }

        suspend fun getDuration():Long {
            openSource()
            return extractor.properties.duration
        }

        suspend fun getFrames() : Flow<Bitmap> {
            val cache = frameListCache
            return if(!cache.isNullOrEmpty()) {
                // cached
                cache.asFlow()
            } else {
                // need to be cached
                val newCache = mutableListOf<Bitmap>()
                frameListCache = newCache
                openSource()
                extractor.extractFrames(count, autoClose = true).map { it.apply { newCache.add(this) } }
            }
        }

        @MainThread
        fun reset() {
            isReady.value = false
            source = null
            val cache = frameListCache
            frameListCache = null
            extractor.close()
            cache?.forEach { it.recycle() }
            cache?.clear()
            contentWidth.value = 0
        }

        override fun close() {
            reset()
        }
    }

    /**
     * AmvExoVideoPlayerのbindViewModelで、playerをplayerView.playerに設定するか？
     * 通常は true。ただし、FullControlPanelのように、PinP/FullScreenモードに対応する場合は、
     * どのビューに関連付けるかを個別に分岐するため、falseにする。
     */
    open val autoAssociatePlayer:Boolean = true

    // region Frame List
    private val frameListRef = UtLazyResetableValue<FrameList> { FrameList(thumbnailCount, thumbnailHeight, context, scope) }
    val frameList get() = frameListRef.value
    val showFrameList = MutableStateFlow(true)
    val controllerMinWidth = MutableStateFlow(0)
    open val showKnobBeltOnFrameList:Flow<Boolean> = flow { emit(true) }
    // endregion

    // region Commands
    val commandPlay = Command { playerModel::play }
    val commandPause = Command { playerModel.pause() }
    val commandTogglePlay = Command { playerModel.togglePlay() }
    val commandShowFrameList = Command { showFrameList.value = !showFrameList.value }
    // endregion

    // region Slider

    /**
     * スライダーのトラッカー位置
     */
    val sliderPosition = MutableStateFlow(0L)

    /**
     * プレーヤーの再生位置
     * 通常は、sliderPosition == presentingPosition だが、トリミングスライダーの場合は、左右トリミング用トラッカーも候補となる。
     * （最後に操作したトラッカーの位置が、presentingPosition となる。）
     */
    open val presentingPosition:Flow<Long> = sliderPosition

    fun seekAndSetSlider(pos:Long) {
        val clipped = playerModel.clipPosition(pos)
        sliderPosition.value = clipped
        playerModel.seekTo(clipped)
    }
    /**
     * スライダーのカウンター表示文字列
     */
    val counterText:Flow<String> = combine(sliderPosition, playerModel.naturalDuration) { pos, duration->
        "${formatTime(pos,duration)} / ${formatTime(duration,duration)}"
    }

    // endregion

    init {
        playerModel.source.onEach(this::onSourceChanged).launchIn(scope)
        playerModel.playerSeekPosition.onEach(this::onPlayerSeekPositionChanged).launchIn(scope)
    }

    /**
     * タイマーによって監視されるプレーヤーの再生位置（playerModel.playerSeekPosition）に応じて、スライダーのシーク位置を合わせる。
     */
    open fun onPlayerSeekPositionChanged(pos:Long) {
        sliderPosition.value = pos
    }

    /**
     * サムネイルを取得
     */
    suspend fun getThumbnails(fn:(Bitmap)->Unit) {
        frameList.getFrames().onEach {
            fn(it)
        }.launchIn(scope)
    }

    /**
     * 動画ソースが変わった時に、
     */
    open fun onSourceChanged(source: IAmvSource?) {
        if(source==null) return
        scope.launch(Dispatchers.IO) {
            val uri = source.getUriAsync() ?: return@launch
            frameList.setUri(uri)
        }
    }

    override fun close() {
        scope.cancel()
        frameListRef.reset { it.close() }
        playerModel.close()
    }
}
