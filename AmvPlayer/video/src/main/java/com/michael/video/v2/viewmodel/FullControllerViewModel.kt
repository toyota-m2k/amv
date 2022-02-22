package com.michael.video.v2.viewmodel

import android.content.Context
import com.michael.video.AmvSettings
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.utils.Listeners
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FullControllerViewModel(
    playerViewModel: PlayerViewModel,
    thumbnailCount:Int,
    thumbnailHeightInDp:Int) : ControllerViewModel(playerViewModel, thumbnailCount, thumbnailHeightInDp) {
    companion object {
        val logger get() = AmvSettings.logger
        fun create(context: Context, thumbnailCount: Int, thumbnailHeight: Int):ControllerViewModel {
            val playerViewModel = PlayerViewModel(context)
            return FullControllerViewModel(playerViewModel, thumbnailCount, thumbnailHeight)
        }
    }

    override val showKnobBeltOnFrameList: Flow<Boolean> = showFrameList
    val mininalMode = MutableStateFlow(false)   // only used in AmvVideoController
    val commandSnapshot = Command()
    val commandMute = Command()

    val markerViewModel :MarkerViewModel by lazy { MarkerViewModel(scope) }
    val commandAddMarker = Command {
        scope.launch {
            markerViewModel.addMarker(playerViewModel.player.currentPosition)
        }
    }
    val commandSelectMarker = Listeners<Long>().apply {
        addForever { pos->
            sliderPosition.value = pos
            playerViewModel.seekTo(pos)
        }
    }

    var onMarkerLongPress:(suspend (marker:Long, pressX:Float)->Unit)? = null
    var prevTickCount:Long = 0L
    var lastMarker:Long = 0L
    val commandNextMarker = Command {
        val pos = markerViewModel.nextMark(playerViewModel.player.currentPosition)
        if(pos>0L) {
            lastMarker = pos
            sliderPosition.value = pos
            playerViewModel.seekTo(pos)
            prevTickCount = System.currentTimeMillis()
        }
    }
    val commandPrevMarker = Command {
        val tick = System.currentTimeMillis()
        val pos = markerViewModel.prevMark(
            if(tick-prevTickCount<300) {
                lastMarker
            } else {
                sliderPosition.value
            })
        lastMarker = pos
        sliderPosition.value = pos
        playerViewModel.seekTo(pos)
        prevTickCount = tick
    }

    init {
        playerViewModel.stretchVideoToView = true
        playerViewModel.source.onEach {
            markerViewModel.clearMarkers()
        }
    }
}