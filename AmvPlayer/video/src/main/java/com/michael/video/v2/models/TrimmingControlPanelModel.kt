package com.michael.video.v2.models

import android.content.Context
import com.michael.video.v2.common.AmvSettings
import com.michael.video.v2.common.IAmvSource
import com.michael.video.v2.util.AmvClipping
import com.michael.video.v2.util.formatTime
import kotlinx.coroutines.flow.*

class TrimmingControlPanelModel(playerModel: PlayerModel, thumbnailCount:Int, thumbnailHeight:Int) : ControlPanelModel(playerModel, thumbnailCount, thumbnailHeight) {
    companion object {
        val logger get() = AmvSettings.logger
        fun create(context: Context, thumbnailCount: Int, thumbnailHeight: Int): TrimmingControlPanelModel {
            val playerViewModel = PlayerModel(context)
            return TrimmingControlPanelModel(playerViewModel, thumbnailCount, thumbnailHeight)
        }
    }
    val trimmingStart = MutableStateFlow<Long>(0)
    val trimmingEnd = MutableStateFlow<Long>(-1)
    val isTrimmed:Boolean get() = playerModel.isReady.value && AmvClipping.isValidClipping(trimmingStart.value, trimmingEnd.value, playerModel.naturalDuration.value)
    val trimmingRange: AmvClipping?
        get() = if(isTrimmed) {
            AmvClipping.make(trimmingStart.value, trimmingEnd.value, playerModel.naturalDuration.value)
        } else {
            null
        }

    fun applyTrimmingRange() {
        playerModel.pseudoClipping = trimmingRange
    }

    override val presentingPosition: Flow<Long> = merge(trimmingStart,trimmingEnd,sliderPosition)

    override val showKnobBeltOnFrameList: Flow<Boolean> = flow { emit(false) }

    val trimmingStartText = combine(trimmingStart,playerModel.naturalDuration) { start, duration->
        if(duration>0) {
            formatTime(start, duration)
        } else {
            "0"
        }
    }
    val trimmingEndText = trimmingEnd.map { end->
        if(end>=0) {
            formatTime(end, playerModel.naturalDuration.value)
        } else {
            ""
        }
    }
    val trimmingSpanText = combine(trimmingStart, trimmingEnd) { start, end->
        if(end>start) {
            val range = end - start
            formatTime(range, playerModel.naturalDuration.value)
        } else {
            ""
        }
    }

    init {
        playerModel.naturalDuration.onEach {
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