/**
 * マーカー表示/設定用ビュー
 *
 * @author M.TOYOTA 2018.07.24 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 *
 * マーカーリストビューだけは、再生ポジションを Double型で保持する。（iOS版との相互運用に配慮）
 */
package com.michael.video.v2.elements

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.michael.utils.SortedList
import com.michael.video.AmvSettings
import com.michael.video.R
import com.michael.video.v2.models.FullControlPanelModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.utils.dp2px
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.*

class AmvMarkerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object {
        val logger = AmvSettings.logger
    }

    // dimensions
    private val mDrMarker: Drawable
    private val mDrMarkerHL: Drawable
    private val mNaturalHeight: Int
    private val mMarkerWidth: Int
    private val mMarkerHitLuckyZone : Int

    private var mViewWidth = 0
    private val mLeftInert: Int
    private val mRightInert: Int

    lateinit var viewModel: FullControlPanelModel
    private var mTotalRange = 0L //viewModel.playerViewModel.naturalDuration.value

    init {
        val sa = context.theme.obtainStyledAttributes(attrs, R.styleable.AmvMarkerView,defStyleAttr,0)

        try {
            mDrMarker = sa.getDrawable(R.styleable.AmvMarkerView_marker) ?: ContextCompat.getDrawable(context, R.drawable.ic_marker_pin)!!
            mDrMarkerHL = sa.getDrawable(R.styleable.AmvMarkerView_markerHighLight) ?: ContextCompat.getDrawable(context, R.drawable.ic_marker_pin_hl)!!
            mLeftInert = sa.getDimensionPixelSize(R.styleable.AmvMarkerView_leftInert, 0)
            mRightInert = sa.getDimensionPixelSize(R.styleable.AmvMarkerView_rightInert, 0)
            mMarkerWidth = mDrMarker.intrinsicWidth
            mMarkerHitLuckyZone = (context.dp2px(24) - mMarkerWidth ) / 2
            mNaturalHeight = mDrMarker.intrinsicHeight
        } finally {
            sa.recycle()
        }

        val touchManager = TouchManager()
        setOnTouchListener(touchManager)
        setOnClickListener(touchManager)
        setOnLongClickListener(touchManager)
    }

    fun bindViewModel(viewModel: FullControlPanelModel, binder: Binder) {
        this.viewModel = viewModel
        val lifecycleOwner = lifecycleOwner()!!
        val scope = lifecycleOwner.lifecycleScope
        this.viewModel.playerModel.naturalDuration.onEach {
            mTotalRange = it
            invalidate()
        }.launchIn(scope)

        this.viewModel.markerListModel.markerEvent.onEach {
            invalidate()
        }.launchIn(scope)
    }

    /**
     * サイズ計算
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)

        val width = when(widthMode) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> widthSize
            MeasureSpec.UNSPECIFIED -> 200
            else -> 200
        }

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val height = when(heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST-> min(mNaturalHeight, heightSize)
            MeasureSpec.UNSPECIFIED->mNaturalHeight
            else -> mNaturalHeight
        }
        setMeasuredDimension(width,height)
    }

    private fun getMarkerLeft(marker:Long) : Float {
        return (( marker.toFloat() / mTotalRange ) * (mViewWidth-mLeftInert-mRightInert)) - mMarkerWidth / 2 + mLeftInert
    }

    private fun getMarkerCenter(marker:Long) : Float {
        return getMarkerLeft(marker) + mMarkerWidth/2f
    }


    private fun hitTestSub(xPos:Float, marker:Long) : Float {
        val l = getMarkerLeft(marker) - mMarkerHitLuckyZone
        val r = l + mMarkerWidth + mMarkerHitLuckyZone*2
//        UtLogger.debug("AmvMarkerView:hitTest: x=${xPos} (${l.roundToInt()} ... ${r.roundToInt()}) : marker=${marker}")
        if(xPos in l..r) {
            return max(xPos-l, r-xPos)
        }
        return -1f
    }

    private val mListPos:SortedList.Position by lazy { SortedList.Position() }
    private fun hitTestIndex(xPos:Int) : Int {
        val mk = (mTotalRange * (xPos - mLeftInert) / (mViewWidth - mLeftInert - mRightInert).toDouble()).roundToLong()
        viewModel.markerListModel.find(mk, mListPos)
        return if (mListPos.hit >= 0) {
            mListPos.hit
        } else {
            var candidate = -1
            var diff = Float.MAX_VALUE
            if (mListPos.next >= 0) {
                val d = hitTestSub(xPos.toFloat(), viewModel.markerListModel[mListPos.next])
                if (d >= 0f) {
                    diff = d
                    candidate = mListPos.next
                }
            }
            if (mListPos.prev >= 0) {
                val d = hitTestSub(xPos.toFloat(), viewModel.markerListModel[mListPos.prev])
                if (d >= 0f && d < diff) {
                    candidate = mListPos.prev
                }
            }
            candidate
        }
    }

    override fun onDraw(canvas: Canvas?) {
        if(null==canvas||mViewWidth<=0||mTotalRange<=0) {
            return
        }

        viewModel.markerListModel.markerList.forEach { v->
            val left = getMarkerLeft(v).roundToInt()
            val drawable =
                if(v ==highlightMarker) {
                    mDrMarkerHL
                } else {
                    mDrMarker
                }
            drawable.setBounds(left, 0, left+mMarkerWidth, mNaturalHeight)
            drawable.draw(canvas)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(mViewWidth != w) {
            mViewWidth = w
        }
    }

//    fun contextMenuOn(marker:Long, clientData:Any?) {
//        viewModel.scope.launch {
//            viewModel.markerViewModel.withHighlightMarker(marker) {
//                viewModel.onMarkerLongPress?.invoke(marker, getMarkerCenter(marker))
//            }
//        }
//    }

    var highlightMarker:Long = -1L
    fun flashMarker(marker:Long, duration:Long = 300L /*ms*/) {
        setHighlight(marker)
        lifecycleOwner()!!.lifecycleScope.launch {
            delay(duration)
            resetHighLight()
        }
    }
    fun setHighlight(marker:Long) {
        if(marker!=highlightMarker) {
            highlightMarker = marker
            invalidate()
        }
    }
    fun resetHighLight() {
        if(highlightMarker>=0) {
            highlightMarker = -1L
            invalidate()
        }
    }
    fun resetHighLight(duration: Long) {
        if(highlightMarker>=0) {
            lifecycleOwner()!!.lifecycleScope.launch {
                delay(duration)
                resetHighLight()
            }
        }
    }
    inline fun withHighlightMarker(marker:Long, fn:()->Unit) {
        setHighlight(marker)
        fn()
        resetHighLight()
    }


//    fun resetWithTotalRange(duration: Long) {
//        mTotalRange = duration
//        //mMarkers.clear()
//        invalidate()
//    }
//
//    fun setHighLightMarker(marker:Long) {
//        mHighLightMarker = marker
//        invalidate()
//    }
//
//    fun resetHighLightMarker() {
//        if(null!=mHighLightMarker) {
//            mHighLightMarker = null
//            invalidate()
//        }
//    }
//
//    fun resetHighLightMarker(delay:Long) {
//        if(null!=mHighLightMarker) {
//            handler.postDelayed({
//                resetHighLightMarker()
//            }, delay)
//        }
//    }
//

    inner class TouchManager : OnTouchListener, OnLongClickListener, OnClickListener {
        private var xOrg = -1f
        private var yOrg = -1f
        private var x = -1f
        private var y = -1f
        private var tapping = false
        private var markerIndex:Int = -1

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            when(event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    markerIndex =  hitTestIndex(event.x.roundToInt())
                    if(markerIndex>=0) {
                        x = event.x
                        y = event.y
                        xOrg = x
                        yOrg = y
                        tapping = true
                        setHighlight(viewModel.markerListModel[markerIndex])
                    }
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    if(markerIndex>=0) {
                        x = event.x
                        y = event.y
                        if ((x - xOrg).absoluteValue > mMarkerWidth || (y - yOrg).absoluteValue > mNaturalHeight / 2) {
                            tapping = false     // タップではなくドラッグ？
                            resetHighLight()
                        }
                    }
                }
                else -> {}
            }
            return false
        }

        private fun selectMarker() : Long {
            val marker = viewModel.markerListModel[markerIndex]
            if(marker>=0) {
                viewModel.commandSelectMarker.invoke(marker)
            }
            return marker
        }

        override fun onClick(v: View?) {
            if(tapping && markerIndex>=0) {
                selectMarker()
                resetHighLight(300L)
            }
        }

        override fun onLongClick(v: View?): Boolean {
            if(tapping && markerIndex>=0) {
                tapping = false
                val marker = selectMarker()
                if(marker>=0) {
                    lifecycleOwner()!!.lifecycleScope.launch {
                        viewModel.onMarkerLongPress?.invoke(marker, getMarkerCenter(marker))
                        resetHighLight()
                    }
                }
            }
            return false
        }
    }
}