package com.michael.video

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.HorizontalScrollView

/**
 * タッチ操作を受け付けない横スクローラー
 */
class AmvHorzScrollView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
        ) : FrameLayout(context, attrs, defStyleAttr) {

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        return false
    }

    fun smoothScrollTo(x:Int,y:Int) {
        getChildAt(0).setMargin(-x, 0,0,0)
    }
}