package com.michael.video

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout

class AmvFrameListView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr) {

    private val mImageContainer:LinearLayout
    private val mScroller:HorizontalScrollView
    private val mKnob: View

    init {
        LayoutInflater.from(context).inflate(R.layout.frame_list_view, this) as FrameLayout
        mImageContainer = findViewById(R.id.imageList)
        mScroller = findViewById(R.id.scroller)
        mKnob = findViewById(R.id.knob)
    }

    fun prepare(frameCount:Int, frameWidth:Int, frameHeight:Int) {
        mImageContainer.setLayoutWidth(frameCount * frameWidth)
        mKnob.setLayoutHeight(frameHeight)
    }

    fun add(bmp: Bitmap) {
        ImageView(context).apply {
            setImageBitmap(bmp)
            layoutParams = FrameLayout.LayoutParams(bmp.width, bmp.height)
            mImageContainer.addView(this)
        }
    }

    fun reset() {
        mImageContainer.removeAllViews()
    }

    var position : Int = 0
        set(v) {
            if(field != v) {
                field = v
                updateScroll()
            }
        }


    fun updateScroll() {
        val range = mScroller.width
        val amount = mImageContainer.width - range
//        val amount2 = mScroller.maxScrollAmount   これだと、amountより大きい値が返ってきてしまう
        val scr = Math.round((amount * position) / 1000f)

        mScroller.smoothScrollTo(scr,0)

        val knobPos =Math.round((range-mKnob.width)*position/1000f)
        mKnob.setMargin(knobPos, 0,0,0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScroll()
    }



}