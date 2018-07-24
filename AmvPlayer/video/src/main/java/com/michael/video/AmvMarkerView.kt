package com.michael.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.michael.utils.FuncyListener2
import com.michael.utils.SortedList
import com.michael.utils.UtLogger
import org.parceler.Parcels
import java.io.File
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class AmvMarkerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var markerAddedListener = FuncyListener2<Long,Any?,Unit>()
    var markerRemovedListener = FuncyListener2<Long,Any?,Unit>()
    var markerSelectedListener = FuncyListener2<Long,Any?,Unit>()
    var markerContextQueryListener = FuncyListener2<Long,Any?,Unit>()

    var markers : ArrayList<Long>
        get() = mMarkers.asArrayList
        set(v) {
            mMarkers.clear()
            mMarkers.addAll(v)
        }

    // dimensions
    private val mDrMarker: Drawable
    private val mNaturalHeight: Int
    private val mMarkerWidth: Int
    private val mMarkerHitLuckyZone : Int

    private var mViewWidth = 0
    private val mLeftInert: Int
    private val mRightInert: Int

    // マーカー情報
    private val mMinMarkerSpan : Long = 100L     // マーカー設定間隔の最小値（100ms）
    private var mTotalRange : Long = 1000L       // naturalDuration
    private val mMarkers = SortedList(32, { _ ->0L}, { o0, o1-> if(o0==o1) 0 else if(o0<o1) -1 else 1 }, false)

    init {
        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvMarkerView,defStyleAttr,0)

        try {
            mDrMarker = sa.getDrawable(R.styleable.AmvMarkerView_marker) ?: context.getDrawable(R.drawable.ic_marker_pin)
            mLeftInert = sa.getDimensionPixelSize(R.styleable.AmvMarkerView_leftInert, 0)
            mRightInert = sa.getDimensionPixelSize(R.styleable.AmvMarkerView_rightInert, 0)
            mMarkerWidth = mDrMarker.intrinsicWidth
            mMarkerHitLuckyZone = (context.dp2px(24) - mMarkerWidth ) / 2
            mNaturalHeight = mDrMarker.intrinsicHeight
            isSaveFromParentEnabled = sa.getBoolean(R.styleable.AmvMarkerView_saveFromParent, true)
        } finally {
            sa.recycle()
        }

        val touchManager = TouchManager()
        setOnTouchListener(touchManager)
        setOnClickListener(touchManager)
        setOnLongClickListener(touchManager)
    }


    /**
     * サイズ計算
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)

        val width = when(widthMode) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> widthSize
            MeasureSpec.UNSPECIFIED -> 200
            else -> 200
        }

        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)

        val height = when(heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST-> Math.min(mNaturalHeight, heightSize)
            MeasureSpec.UNSPECIFIED->mNaturalHeight
            else -> mNaturalHeight
        }
        setMeasuredDimension(width,height)
    }

    private fun getMarkerLeft(marker:Long) : Float {
        return (( marker / mTotalRange.toFloat() ) * (mViewWidth-mLeftInert-mRightInert)) - mMarkerWidth / 2 + mLeftInert
    }

    private val mListPos = SortedList.Position()

    private fun hitTestSub(xPos:Int, marker:Long) : Float {
        val l = getMarkerLeft(marker) - mMarkerHitLuckyZone
        val r = l + mMarkerWidth + mMarkerHitLuckyZone*2
        if(xPos in l..r) {
            return Math.max(xPos-l, r-xPos)
        }
        return -1f
    }

    private fun hitTestIndex(xPos:Int) : Int {
        val mk = (mTotalRange * (xPos-mLeftInert) / (mViewWidth-mLeftInert-mRightInert).toFloat()).roundToLong()
        mMarkers.find(mk, mListPos)
        if(mListPos.hit>=0) {
            return mListPos.hit
        }

        var candidate = -1
        var diff = 0f
        if(mListPos.next>=0) {
            val d = hitTestSub(xPos, mMarkers[mListPos.next])
            if(d>=0f) {
                diff = d
                candidate = mListPos.next
            }
        }
        if(mListPos.prev>=0) {
            val d = hitTestSub(xPos, mMarkers[mListPos.prev])
            if(d>=0f && d < diff) {
                candidate = mListPos.prev
            }
        }
        return candidate
    }

    override fun onDraw(canvas: Canvas?) {
        if(null==canvas||mViewWidth<=0||mTotalRange<=0) {
            return
        }

        for(v in mMarkers) {
            val left = getMarkerLeft(v).roundToInt()
            mDrMarker.setBounds(left, 0, left+mMarkerWidth, mNaturalHeight)
            mDrMarker.draw(canvas)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(mViewWidth != w) {
            mViewWidth = w
        }
    }

    private fun canAddMarker(marker:Long) : Boolean {
        mMarkers.find(marker, mListPos)
        if(mListPos.hit>=0) {
            return false
        } else if(mListPos.prev>=0) {
             if((mMarkers[mListPos.prev] - marker).absoluteValue < mMinMarkerSpan) {
                return false
            }
        } else if(mListPos.next>=0) {
            if((mMarkers[mListPos.next] - marker).absoluteValue < mMinMarkerSpan) {
                return false
            }
        }
        return true
    }

    fun addMarker(marker:Long, clientData:Any?) {
        if(!canAddMarker(marker)) {
            return
        }
        if(mMarkers.add(marker)) {
            invalidate()
            markerAddedListener.invoke(marker, clientData)
        }
    }

    @Suppress("unused")
    fun removeMarker(marker:Long, clientData:Any?) {
        if(mMarkers.remove(marker)) {
            invalidate()
            markerRemovedListener.invoke(marker, clientData)
        }
    }

    /**
     * 次のマーカーを選択する。
     *
     * @event MarkerSelected
     * @return true 成功 / false: これ以上後ろにマーカーは存在しない
     */
    fun nextMark(current:Long, clientData:Any?) : Boolean
    {
        mMarkers.find(current, mListPos)
        if (mListPos.next < 0) {
            return false
        }
        selectMarker(mMarkers[mListPos.next], clientData)
        return true
    }

    /**
     * 前のマーカーを選択する。
     *
     * @event MarkerSelected
     * @return true 成功 / false: これ以上前にマーカーは存在しない
     */
    fun prevMark(current:Long, clientData:Any?) : Boolean
    {
        mMarkers.find(current, mListPos)
        if (mListPos.prev < 0) {
            return false
        }
        selectMarker(mMarkers[mListPos.prev], clientData)
        return true
    }

    fun selectMarker(marker:Long, clientData:Any?) {
        markerSelectedListener.invoke(marker, clientData)
    }

    fun contextMenuOn(marker:Long, clientData:Any?) {
        markerContextQueryListener.invoke(marker, clientData)
    }

    fun resetWithTotalRange(duration: Long) {
        mTotalRange = duration
        mMarkers.clear()
        invalidate()
    }


    inner class TouchManager : View.OnTouchListener, View.OnLongClickListener, View.OnClickListener {
        private var xOrg = -1f
        private var yOrg = -1f
        private var x = -1f
        private var y = -1f
        private var tapping = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when(event?.action) {
                        MotionEvent.ACTION_DOWN -> {
                            x = event.x
                            y = event.y
                            xOrg = x
                            yOrg = y
                            tapping = true
                        }
                        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                            x = event.x
                            y = event.y
                            if((x-xOrg).absoluteValue > mMarkerWidth || (y-yOrg).absoluteValue>mNaturalHeight/2) {
                                tapping = false     // タップではなくドラッグ？
                            }
                        }
                        else -> {}
                }
            return false
        }

        override fun onClick(v: View?) {
            if(tapping) {
                val mk = hitTestIndex(x.roundToInt())
                if(mk>=0) {
                    selectMarker(mMarkers[mk], this@AmvMarkerView)
                }
            }
        }

        override fun onLongClick(v: View?): Boolean {
            if(tapping) {
                val mk = hitTestIndex(x.roundToInt())
                if(mk>=0) {
                    contextMenuOn(mMarkers[mk], this@AmvMarkerView)
                }
            }
            return false
        }
    }


    // region Saving States

    override fun onSaveInstanceState(): Parcelable {
        UtLogger.debug("LC-AmvMarkerView: onSaveInstanceState")
        val parent =  super.onSaveInstanceState()
        return SavedState(parent, mMarkers)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        UtLogger.debug("LC-AmvMarkerView: onRestoreInstanceState")
        if(state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            mMarkers.addAll(state.markersList)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : View.BaseSavedState {
        val markersList : ArrayList<Long>

        /**
         * Constructor called from [AmvSlider.onSaveInstanceState]
         */
        constructor(superState: Parcelable, markers:SortedList<Long>) : super(superState) {
            markersList = markers.asArrayList
        }

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(parcel: Parcel) : super(parcel) {
            @Suppress("UNCHECKED_CAST")
            markersList = parcel.readArrayList(Long::class.java.classLoader) as ArrayList<Long>
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            super.writeToParcel(parcel, flags)
            parcel.writeList(markersList)
        }

        companion object {
            @Suppress("unused")
            @JvmStatic
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(parcel: Parcel): SavedState {
                    return SavedState(parcel)
                }
                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }



    // endregion
}