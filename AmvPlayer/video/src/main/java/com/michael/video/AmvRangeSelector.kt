package com.michael.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

@Suppress("unused")
class AmvRangeSelector @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val handleSize = 48
    private val barHeight = 8

    interface RangeSelectorEvents {
        fun onStartPositionChanged(position: Int)

        fun onEndPositionChanged(position: Int)
    }

    private var mHandles: Array<Handle>
    private var mHandleToMove: Handle? = null

    private var mBarPaint: Paint
    private var mHandlePaint: Paint
    private var mHandleHaloPaint: Paint

    private var mWidth: Int = 0
    private var mHeight: Int = 0

    private val mBarRect: Rect = Rect()

    private var mEventsListener: RangeSelectorEvents? = null

    init {
        isFocusable = true

        mBarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mBarPaint.color = Color.GRAY

        mHandleHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mHandleHaloPaint.color = Color.BLACK
        mHandleHaloPaint.alpha = 0x50

        mHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mHandlePaint.color = 0x8285E1

        mHandles = arrayOf(
                Handle().apply {
                    id = 0
                    position = 0
                },
                Handle().apply {
                    id = 1
                    position = 100
                }
        )
    }

    fun setEventsListener(listener: RangeSelectorEvents) {
        mEventsListener = listener
    }

    fun getStartPosition(): Int {
        return mHandles[0].position
    }

    fun setStartPosition(position: Int) {
        mHandles[0].position = position

        invalidate()
    }

    fun getEndPosition(): Int {
        return mHandles[1].position
    }

    fun setEndPosition(position: Int) {
        mHandles[1].position = position

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if(mBarRect.isEmpty) {
            return
        }

        val cy = mBarRect.top + (mBarRect.bottom - mBarRect.top) / 2
        var cx: Int

        canvas.drawRect(mBarRect, mBarPaint)

        for (handle in mHandles) {
            cx = positionToX(handle.position)

            canvas.drawCircle(cx.toFloat(), cy.toFloat(), (handleSize / 2).toFloat(), mHandleHaloPaint)
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), (handleSize / 4).toFloat(), mHandlePaint)
        }

        canvas.drawRect(positionToX(mHandles[0].position).toFloat(), mBarRect.top.toFloat(), positionToX(mHandles[1].position).toFloat(), mBarRect.bottom.toFloat(), mHandlePaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        mWidth = w
        mHeight = h

        mBarRect.left = handleSize / 2
        mBarRect.right = mWidth - handleSize / 2
        mBarRect.top = mHeight / 2 - barHeight / 2
        mBarRect.bottom = mBarRect.top + barHeight / 2
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.action

        val x = event.x.toInt()
        val y = event.y.toInt()

        val cy = mBarRect.top + (mBarRect.bottom - mBarRect.top) / 2
        var cx: Int

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mHandleToMove = null

                for (handle in mHandles) {
                    cx = positionToX(handle.position)

                    if (pointInsideRect(cx - handleSize, cx + handleSize, cy - handleSize, cy + handleSize, x, y)) {
                        mHandleToMove = handle

                        break
                    }
                }

                // Hit test on handles failed
                // let's pick handle what is more
                // close to touch point
                if (mHandleToMove == null) {
                    mHandleToMove = if (Math.abs(x - positionToX(mHandles[0].position)) < Math.abs(x - positionToX(mHandles[1].position))) {
                        mHandles[0]
                    } else {
                        mHandles[1]
                    }
                }
            }

        //Touch drag with the knob
            MotionEvent.ACTION_MOVE -> {
                if (mHandleToMove != null) {
                    var position = x * 100 / mWidth

                    @Suppress("ConvertTwoComparisonsToRangeCheck")
                    if (position > 0 && position < 100) {
                        if (mHandleToMove!!.id == 0) {
                            if (x + handleSize * 2 > positionToX(mHandles[1].position)) {
                                position = -1
                            }
                        } else if (mHandleToMove!!.id == 1) {
                            if (x - handleSize * 2 < positionToX(mHandles[0].position)) {
                                position = -1
                            }
                        }
                    } else {
                        position = -1
                    }

                    if (position != -1) {
                        mHandleToMove!!.position = position

                        if (mEventsListener != null) {
                            if (mHandleToMove!!.id == 0) {
                                mEventsListener!!.onStartPositionChanged(position)
                            } else if (mHandleToMove!!.id == 1) {
                                mEventsListener!!.onEndPositionChanged(position)
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                mHandleToMove = null
            }
        }

        invalidate()

        return true
    }

    private fun positionToX(position: Int): Int {
        val barWidth = mBarRect.width()

        return position * barWidth / 100 + mBarRect.left
    }

    private fun pointInsideRect(l: Int, r: Int, t: Int, b: Int, pointX: Int, pointY: Int): Boolean {
        @Suppress("ConvertTwoComparisonsToRangeCheck")
        return pointX > l && pointX < r && pointY > t && pointY < b

    }

    private inner class Handle {
        var position: Int = 0

        var id: Int = 0
    }

}