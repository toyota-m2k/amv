package com.michael.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.michael.utils.FuncyListener1
import com.michael.utils.UtLogger
import kotlin.math.roundToInt

class AmvFrameListView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr) {

    private var mKnobPosition = 0L          // 主ノブの位置（TrimStart/Endの管理は、scrollerに任せる
    private val controls = Controls()
//    private val models = Models()

    private inner class Controls {
        val scroller: AmvHorzScrollView by lazy {
            findViewById<AmvHorzScrollView>(R.id.scroller)
        }
        val knob: View by lazy {
            findViewById<View>(R.id.knob)
        }
    }

    /**
     * お友達（AmvSlider）にタッチイベントを伝えるためのリスナー
     * これにより、このビュー上でのドラッグ操作を、AmvSlider上でのタッチ操作と同じように扱うことができる。
     */
    val touchFriendListener = FuncyListener1<MotionEvent, Unit>()

    init {
        LayoutInflater.from(context).inflate(R.layout.frame_list_view, this) as FrameLayout
        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvFrameListView,defStyleAttr,0)
        try {
            controls.scroller.trimmingEnabled = sa.getBoolean(R.styleable.AmvFrameListView_trimmingMode, false)
        } finally {
            sa.recycle()
        }

        if(controls.scroller.trimmingEnabled) {
            controls.knob.background = context.getDrawable(R.drawable.ic_slider_trim_guide)
        }
    }

    fun prepare(frameCount: Int, frameWidth: Int, frameHeight: Int) {
        controls.scroller.prepare(frameCount,frameWidth)
        this.setLayoutHeight(frameHeight)
        updateScroll()
    }

    fun add(bmp: Bitmap) {
        ImageView(context).apply {
            setImageBitmap(bmp)
            layoutParams = FrameLayout.LayoutParams(bmp.width, bmp.height)
            controls.scroller.addImage(this)
        }
    }


    val trimmingEnabled:Boolean
        get() = controls.scroller.trimmingEnabled

    var totalRange: Long
        get() = controls.scroller.totalRange
        set(v) {
            controls.scroller.totalRange = v
        }

    var position: Long
        get() = mKnobPosition
        set(v) {
            mKnobPosition = v
            controls.scroller.position = v
            updateScroll()
        }

    var trimStart: Long
        get() = controls.scroller.trimStart
        set(v) {
            controls.scroller.trimStart = v
            updateScroll()
        }

    var trimEnd: Long
        get() = controls.scroller.trimEnd
        set(v) {
            controls.scroller.trimEnd = v
            updateScroll()
        }

    private fun updateScroll() {
        val range = getLayoutWidth()
        val knobPos = if(trimmingEnabled) {
            (range * position / totalRange.toFloat())-controls.knob.width/2f
        } else {
            (range - controls.knob.width) * mKnobPosition / totalRange.toFloat()
        }
        controls.knob.setMargin(knobPos.roundToInt(), 0, 0, 0)
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScroll()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (null != event) {
            touchFriendListener.invoke(event)
        }
        return true
    }
}

