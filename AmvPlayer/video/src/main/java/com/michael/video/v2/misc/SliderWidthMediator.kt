package com.michael.video.v2.misc

import android.view.View
import android.widget.FrameLayout
import com.michael.video.getLayoutWidth
import com.michael.video.setLayoutWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

class SliderWidthMediator(contentWidth: Flow<Int>, val sliderPanel: View, sliderViewExtentWidth:Int, scope: CoroutineScope) {
    val containerWidth = MutableStateFlow<Int>(0)
    val mediator = combine(containerWidth, contentWidth) { containerWiddth, contentWidth ->
        if(containerWiddth>0 && contentWidth>0 ) {
            val maxSliderWidth = contentWidth + sliderViewExtentWidth
            if(maxSliderWidth<containerWiddth) {
                maxSliderWidth
            } else {
                FrameLayout.LayoutParams.MATCH_PARENT
            }
        } else {
            0
        }
    }

    init {
        mediator.onEach {
            if(it!=0 && sliderPanel.getLayoutWidth()!=it) {
                sliderPanel.setLayoutWidth(it)
            }
        }.launchIn(scope)
    }

    fun onContainerSizeChanged(width:Int) {
        containerWidth.value = width
    }
}