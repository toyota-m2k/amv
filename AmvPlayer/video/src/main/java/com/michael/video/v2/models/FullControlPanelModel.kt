package com.michael.video.v2.models

import android.content.Context
import com.michael.video.v2.common.AmvSettings
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.utils.Listeners
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class FullControlPanelModel(
    playerModel: PlayerModel,
    thumbnailCount:Int,
    thumbnailHeightInDp:Int) : ControlPanelModel(playerModel, thumbnailCount, thumbnailHeightInDp) {
    companion object {
        val logger get() = AmvSettings.logger
        fun create(context: Context, thumbnailCount: Int, thumbnailHeight: Int): ControlPanelModel {
            val playerViewModel = PlayerModel(context)
            return FullControlPanelModel(playerViewModel, thumbnailCount, thumbnailHeight)
        }
    }
    interface IPlayerOwner {
        val isPrimary:Boolean
    }
    override val autoAssociatePlayer: Boolean = false
    override val showKnobBeltOnFrameList: Flow<Boolean> = showFrameList
    val mininalMode = MutableStateFlow(false)   // only used in AmvVideoController
    val commandSnapshot = Command()
    val commandMute = Command()
    val commandPinP = Command()
    val commandFullscreen = Command()
    val commandCloseFullscreen = Command()

    val markerListModel : MarkerListModel by lazy { MarkerListModel(scope) }
    val commandAddMarker = Command {
        scope.launch {
            markerListModel.addMarker(playerModel.player.currentPosition)
        }
    }
    val commandSelectMarker = Listeners<Long>().apply {
        addForever { pos->
            sliderPosition.value = pos
            playerModel.seekTo(pos)
        }
    }

    var onMarkerLongPress:(suspend (marker:Long, pressX:Float)->Unit)? = null
    var prevTickCount:Long = 0L
    var lastMarker:Long = 0L
    val commandNextMarker = Command {
        val pos = markerListModel.nextMark(playerModel.player.currentPosition)
        if(pos>0L) {
            lastMarker = pos
            sliderPosition.value = pos
            playerModel.seekTo(pos)
            prevTickCount = System.currentTimeMillis()
        }
    }
    val commandPrevMarker = Command {
        val tick = System.currentTimeMillis()
        val pos = markerListModel.prevMark(
            if(tick-prevTickCount<300) {
                lastMarker
            } else {
                sliderPosition.value
            })
        lastMarker = pos
        sliderPosition.value = pos
        playerModel.seekTo(pos)
        prevTickCount = tick
    }

    init {
        playerModel.source.onEach {
            markerListModel.clearMarkers()
        }
    }

    var isPinP:Boolean = false

    val ownerCandidates = mutableListOf<IPlayerOwner>()
    val ownership = MutableStateFlow<IPlayerOwner?>(null)
    fun registerPlayerOwner(owner:IPlayerOwner) {
        if(!ownerCandidates.contains(owner)) {
            ownerCandidates.add(owner)
            if(ownership.value==null) {
                ownership.value = owner
                playerModel.stretchVideoToView.value = owner.isPrimary
            }
        }
    }
    fun unregisterPlayerOwner(owner:IPlayerOwner) {
        ownerCandidates.remove(owner)
        if(ownership.value === owner) {
            ownership.value = ownerCandidates.lastOrNull()
            playerModel.stretchVideoToView.value = ownership.value?.isPrimary?:true
        }
    }

    fun requestOwnership(owner:IPlayerOwner) {
        assert(ownerCandidates.contains(owner))
        ownership.value = owner
        playerModel.stretchVideoToView.value = owner.isPrimary
    }
}