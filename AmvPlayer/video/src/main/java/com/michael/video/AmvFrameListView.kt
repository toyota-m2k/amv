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

class AmvFrameListView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr) {

    private val mImageContainer: LinearLayout
    private val mScroller: HorizontalScrollView
    private val mKnob: View
    private val mLeftTruncated: View

    /**
     * お友達（AmvSlider）にタッチイベントを伝えるためのリスナー
     * これにより、このビュー上でのドラッグ操作を、AmvSlider上でのタッチ操作と同じように扱うことができる。
     */
    val touchFriendListener = FuncyListener1<MotionEvent, Unit>()

//    var knobWidth: Int
//        get() = mKnob.getLayoutWidth()
//        set(v) {
//            if (v != mKnob.getLayoutWidth()) {
//                mKnob.setLayoutWidth(v)
//            }
//        }

    init {
        LayoutInflater.from(context).inflate(R.layout.frame_list_view, this) as FrameLayout
        mImageContainer = findViewById(R.id.imageList)
        mScroller = findViewById(R.id.scroller)
        mKnob = findViewById(R.id.knob)
        mLeftTruncated = findViewById(R.id.leftTruncated)
    }

    fun prepare(frameCount: Int, frameWidth: Int, frameHeight: Int) {
        mImageContainer.setLayoutWidth(frameCount * frameWidth)
        mKnob.setLayoutHeight(frameHeight)

//        mLeftTruncated.setLayoutHeight(frameHeight)
//        mLeftTruncated.setLayoutWidth(frameCount * frameWidth /4)
//        mLeftTruncated.visibility=View.VISIBLE
    }

    fun add(bmp: Bitmap) {
        ImageView(context).apply {
            setImageBitmap(bmp)
            layoutParams = FrameLayout.LayoutParams(bmp.width, bmp.height)
            mImageContainer.addView(this)
        }
    }

    private fun reset() {
        mImageContainer.removeAllViews()
    }

    private var mTotalRange: Long = 1000L

    private val totalRange: Long
        get() = mTotalRange

    fun resetWithTotalRange(v: Long) {
        mTotalRange = v
        position = 0
        reset()
    }

    var position: Long = 0
        set(v) {
            if (field != v) {
                field = v
                updateScroll()
            }
        }


    private fun updateScroll() {
        val range = getLayoutWidth()
        val amount = mImageContainer.getLayoutWidth() - range
//        val amount2 = mScroller.maxScrollAmount   これだと、amountより大きい値が返ってきてしまう
        val scr = Math.round((amount * position) / totalRange.toFloat())

        mScroller.smoothScrollTo(scr, 0)

        val knobPos = Math.round((range - mKnob.width) * position / totalRange.toFloat())
        mKnob.setMargin(knobPos, 0, 0, 0)
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

