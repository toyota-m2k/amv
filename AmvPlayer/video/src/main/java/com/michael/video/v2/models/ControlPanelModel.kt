package com.michael.video.v2.models

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.annotation.MainThread
import com.michael.video.*
import com.michael.video.v2.elements.AmvFrameExtractor
import io.github.toyota32k.bindit.Command
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
    thumbnailCount:Int,
    thumbnailHeightInDp:Int
) : Closeable {
    companion object {
        val logger get() = AmvSettings.logger
        fun create(context: Context, thumbnailCount: Int, thumbnailHeight: Int): ControlPanelModel {
            val playerViewModel = PlayerModel(context)
            return ControlPanelModel(playerViewModel, thumbnailCount, thumbnailHeight)
        }
    }

    val scope:CoroutineScope = CoroutineScope(playerModel.scope.coroutineContext)
    val context: Context get() = playerModel.context
    private val thumbnailHeight = context.dp2px(thumbnailHeightInDp)

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
    open val autoAssociatePlayer:Boolean = true

    val frameList = FrameList(thumbnailCount, thumbnailHeight, context, scope)
    val showFrameList = MutableStateFlow(true)
    val controllerMinWidth = MutableStateFlow(0)
    open val showKnobBeltOnFrameList:Flow<Boolean> = flow { emit(true) }
    val commandPlay = Command { playerModel::play }
    val commandPause = Command { playerModel.pause() }
    val commandTogglePlay = Command { playerModel.togglePlay() }
    val commandShowFrameList = Command { showFrameList.value = !showFrameList.value }

//
//    data class LongPressInfo(val marker:Long, val pressX:Float)
//    val markerLongPressCommand = Listeners<LongPressInfo>().apply {
//        addForever { info->
//            onMarkerLongPress?.invoke(info.marker, info.pressX)
//        }
//    }


    val sliderPosition = MutableStateFlow(0L)

    fun seekAndSetSlider(pos:Long) {
        val clipped = playerModel.clipPosition(pos)
        sliderPosition.value = clipped
        playerModel.seekTo(clipped)
    }


    open val presentingPosition:Flow<Long> = sliderPosition

    val counterText:Flow<String> = combine(sliderPosition, playerModel.naturalDuration) { pos, duration->
        "${formatTime(pos,duration)} / ${formatTime(duration,duration)}"
    }

    init {
        playerModel.source.onEach(this::onSourceChanged).launchIn(scope)
        playerModel.playerSeekPosition.onEach {
            onPlayerSeekPositionChanged(it)
        }.launchIn(scope)
    }

    open fun onPlayerSeekPositionChanged(pos:Long) {
        sliderPosition.value = pos
    }

    suspend fun getThumbnails(fn:(Bitmap)->Unit) {
        frameList.getFrames().onEach {
            fn(it)
        }.launchIn(scope)
    }

    open fun onSourceChanged(source: IAmvSource?) {
        if(source==null) return
        scope.launch(Dispatchers.IO) {
            val uri = source.getUriAsync() ?: return@launch
            frameList.setUri(uri)
        }
    }

    override fun close() {
        scope.cancel()
        frameList.close()
        playerModel.close()
    }
}
