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

    private val controls = Controls()
    private val models = Models()

    private inner class Controls {
        val imageContainer: LinearLayout by lazy {
            findViewById<LinearLayout>(R.id.imageList)
        }
        val scroller: AmvHorzScrollView by lazy {
            findViewById<AmvHorzScrollView>(R.id.scroller)
        }
        val knob: View by lazy {
            findViewById<View>(R.id.knob)
        }
        val leftTruncated: View by lazy {
            findViewById<View>(R.id.leftTruncated)
        }
        val rightTruncated: View by lazy {
            findViewById<View>(R.id.rightTruncated)
        }
        val leftTruncatedBar:View by lazy {
            findViewById<View>(R.id.leftTruncatedBar)
        }
        val rightTruncatedBar:View by lazy {
            findViewById<View>(R.id.rightTruncatedBar)
        }

    }

    private inner class Models {
        var trimmingEnabled:Boolean = false
        var naturalDuration: Long = 1000

        var position:Long = 0
            set(v) {
                if (field != v) {
                    field = v
                    updateScroll()
                }
            }

        var trimStart:Long = 0
            set(v) {
                if(field!=v) {
                    field = v
                    if(trimmingEnabled) {
                        updateTrimStart()
                    }
                }
            }
        var trimEnd:Long = 0
            set(v) {
                if(field!=v) {
                    field = v
                    if(trimmingEnabled) {
                        updateTrimEnd()
                    }
                }
            }

        fun setDuration(duration:Long) {
            naturalDuration = duration
            position = 0L
            trimStart = 0L
            trimEnd = duration
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
            models.trimmingEnabled = sa.getBoolean(R.styleable.AmvFrameListView_trimmingMode, false)
        } finally {
            sa.recycle()
        }

        if(models.trimmingEnabled) {
            controls.knob.background = context.getDrawable(R.drawable.ic_slider_trim_guide)
            controls.leftTruncated.visibility = View.VISIBLE
            controls.rightTruncated.visibility = View.VISIBLE
            controls.leftTruncatedBar.visibility = View.VISIBLE
            controls.rightTruncatedBar.visibility = View.VISIBLE
        }
    }

    fun prepare(frameCount: Int, frameWidth: Int, frameHeight: Int) {
        controls.imageContainer.removeAllViews()
        controls.imageContainer.setLayoutWidth(frameCount * frameWidth)
        this.setLayoutHeight(frameHeight)
        updateScroll()
        if(models.trimmingEnabled) {
            updateTrimStart()
            updateTrimEnd()
        }
    }

    fun add(bmp: Bitmap) {
        ImageView(context).apply {
            setImageBitmap(bmp)
            layoutParams = FrameLayout.LayoutParams(bmp.width, bmp.height)
            controls.imageContainer.addView(this)
            UtLogger.debug("AmvFrameList: added ... count=${controls.imageContainer.childCount}")
        }
    }

    private val totalRange: Long
        get() = models.naturalDuration

    fun setTotalRange(v: Long) {
        models.setDuration(v)
    }

    var position: Long
        get() = models.position
        set(v) {
            models.position = v
        }

    var trimStart: Long
        get() = models.trimStart
        set(v) {
            models.trimStart = v
        }

    var trimEnd: Long
        get() = models.trimEnd
        set(v) {
            models.trimEnd = v
        }

    private fun updateScroll() {
        val range = getLayoutWidth()
        val amount = controls.imageContainer.getLayoutWidth() - range
//        val amount2 = scroller.maxScrollAmount   これだと、amountより大きい値が返ってきてしまう
        val scr = Math.round((amount * position) / totalRange.toFloat())

        controls.scroller.smoothScrollTo(scr, 0)

        val knobPos = if(models.trimmingEnabled) {
            (range * position / totalRange.toFloat())-controls.knob.width/2f
        } else {
            (range - controls.knob.width) * position / totalRange.toFloat()
        }
        controls.knob.setMargin(knobPos.roundToInt(), 0, 0, 0)
    }

    private fun updateTrimStart() {
        val range = getLayoutWidth()
        var pos =   (range * trimStart / totalRange.toFloat()).roundToInt()
        if(pos<0) {
            pos = 0
        }
        controls.leftTruncated.setLayoutWidth(pos)
        controls.leftTruncatedBar.setMargin(pos - controls.leftTruncatedBar.getLayoutWidth(),0,0,0)
    }

    private fun updateTrimEnd() {
        val range = getLayoutWidth()
        var pos =   (range * trimEnd / totalRange.toFloat()).roundToInt()

        if(pos>range) {
            pos = range
        }
        controls.rightTruncated.setMargin(pos,0,0,0)
        controls.rightTruncated.setLayoutWidth(range-pos)
        controls.rightTruncatedBar.setMargin(pos,0,0,0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScroll()
        if(models.trimmingEnabled) {
            updateTrimStart()
            updateTrimEnd()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (null != event) {
            touchFriendListener.invoke(event)
        }
        return true
    }
}

