package com.michael.video.v2.viewmodel

import android.content.Context
import com.michael.video.AmvSettings
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.utils.Listeners
import kotlinx.coroutines.flow.MutableStateFlow
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
    val mininalMode = MutableStateFlow(false)   // only used in AmvVideoController
    val commandSnapshot = Command()
    val commandMute = Command()

    val markerViewModel :MarkerViewModel by lazy { MarkerViewModel(scope) }
    val commandAddMarker = Command().apply { bindForever {
        scope.launch {
            markerViewModel.addMarker(playerViewModel.player.currentPosition)
        }
    }
    }
    val commandSelectMarker = Listeners<Long>().apply {
        addForever { pos->
            playerViewModel.seekTo(pos)
        }
    }



    var onMarkerLongPress:(suspend (marker:Long, pressX:Float)->Unit)? = null
    var prevTickCount:Long = 0L
    var prevMarker:Long = -1L
    val commandNextMarker = Command().apply {
        bindForever {
            val pos = markerViewModel.nextMark(playerViewModel.player.currentPosition)
            prevMarker = pos
            playerViewModel.seekTo(pos)
            prevTickCount = System.currentTimeMillis()
        }
    }
    val commandPrevMarker = Command().apply {
        bindForever {
            val tick = System.currentTimeMillis()
            val pos = markerViewModel.prevMark(
                if(tick-prevTickCount<300) {
                    prevMarker
                } else {
                    playerViewModel.player.currentPosition
                })
            prevMarker = pos
            playerViewModel.seekTo(pos)
            prevTickCount = tick
        }
    }

}