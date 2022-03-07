package com.michael.video.v2.misc

import android.view.View
import android.widget.FrameLayout
import com.michael.video.v2.util.getLayoutWidth
import com.michael.video.v2.util.setLayoutWidth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/**
 * コンテナ（スライダーの親）のサイズと、コンテント（ビデオのフレームリストの幅）のサイズから、スライダーのサイズを決定する。
 * - コンテナがフレームリスト全体を表示できる十分な幅を持っているときは、フレームリストの幅をスライダーの幅とする。
 * - フレームリストがコンテナに収まらないときは、コンテナの幅をスライダーの幅とする。
 */
class SliderWidthMediator(contentWidth: Flow<Int>, private val sliderPanel: View, sliderViewExtentWidth:Int, scope: CoroutineScope) {
    private val containerWidth = MutableStateFlow(0)
    private val mediator = combine(containerWidth, contentWidth) { containerWidth, contentWidth ->
        if(containerWidth>0 && contentWidth>0 ) {
            val maxSliderWidth = contentWidth + sliderViewExtentWidth
            if(maxSliderWidth<containerWidth) {
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