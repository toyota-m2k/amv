package com.michael.video.v2.viewmodel

import android.content.Context
import com.michael.video.AmvSettings
import com.michael.video.IAmvSource
import com.michael.video.IAmvVideoPlayer
import com.michael.video.formatTime
import kotlinx.coroutines.flow.*

class TrimmingControllerViewModel(playerViewModel: PlayerViewModel, thumbnailCount:Int, thumbnailHeight:Int) : ControllerViewModel(playerViewModel, thumbnailCount, thumbnailHeight) {
    companion object {
        val logger get() = AmvSettings.logger
        fun create(context: Context, thumbnailCount: Int, thumbnailHeight: Int):TrimmingControllerViewModel {
            val playerViewModel = PlayerViewModel(context)
            return TrimmingControllerViewModel(playerViewModel, thumbnailCount, thumbnailHeight)
        }
    }
    val trimmingStart = MutableStateFlow<Long>(0)
    val trimmingEnd = MutableStateFlow<Long>(-1)
    val isTrimmed:Boolean get() = playerViewModel.isReady && 0<trimmingStart.value || trimmingEnd.value < playerViewModel.naturalDuration.value
    val trimmingRange: IAmvVideoPlayer.Clipping?
        get() = if(isTrimmed) {
                IAmvVideoPlayer.Clipping(trimmingStart.value, trimmingEnd.value)
            } else {
                null
            }

    fun applyTrimmingRange() {
        playerViewModel.pseudoClipping = trimmingRange
    }

    override val presentingPosition: Flow<Long> = merge(trimmingStart,trimmingEnd,sliderPosition)

    override val showKnobBeltOnFrameList: Flow<Boolean> = flow { emit(false) }

    val trimmingStartText = combine(trimmingStart,playerViewModel.naturalDuration) { start,duration->
        if(duration>0) {
            formatTime(start, duration)
        } else {
            "0"
        }
    }
    val trimmingEndText = trimmingEnd.map { end->
        if(end>=0) {
            formatTime(end, playerViewModel.naturalDuration.value)
        } else {
            ""
        }
    }
    val trimmingSpanText = combine(trimmingStart, trimmingEnd) { start, end->
        if(end>start) {
            val range = end - start
            formatTime(range, playerViewModel.naturalDuration.value)
        } else {
            ""
        }
    }

    init {
        playerViewModel.naturalDuration.onEach {
            logger.debug("duration = $it")
            if(it>0 && (trimmingEnd.value<0 || it<trimmingEnd.value)) {
                trimmingEnd.value = it
            }
        }.launchIn(scope)
    }

    override fun onSourceChanged(source: IAmvSource?) {
        logger.debug()
        super.onSourceChanged(source)
        trimmingStart.value = 0L
        trimmingEnd.value = -1L
    }
}