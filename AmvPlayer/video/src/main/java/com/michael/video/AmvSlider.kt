package com.michael.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.support.annotation.ColorInt
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.michael.utils.Funcy3
import com.michael.utils.FuncyListener3
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong


//@Suppress("unused")
class AmvSlider @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * スライダーのmax値
     */
    var totalLength : Long = 0      // = Natural Duration
        set(v) {
            field = v
            trimEndPosition = v
            trimStartPosition = 0
            currentPosition = 0
            applyPosition(false)
        }

    /**
     * トリミング開始位置が変更された
     * TrimStart　の値が変更されたときに呼ばれる
     */
    val trimStartPositionChanged = SliderValueChangedListener()

    /**
     * トリミング終了位置が変更された
     * TrimEnd　の値が変更されたときに呼ばれる
     */
    val trimEndPositionChanged = SliderValueChangedListener()

    /**
     * 再生位置が変更された
     * UI操作によって CurrentPositionが変更されたときに呼ばれる。
     * プログラム的に、CurrentPosition, AbsoluteCurrentPositionを変更したときは呼ばれない。
     */
    val currentPositionChanged = SliderValueChangedListener()

    /**
     * 再生位置（再生スライダーの位置）
     */
    var currentPosition : Long = 0
        set(v) {
            val value = v.limit(trimStartPosition, trimEndPosition)
            if(value != field) {
                field = value
                currentPositionChanged.invoke(this, value, mDraggingInfo.draggingStateWithKnob(Knob.THUMB))
                applyPosition(true)
            }
        }

    /**
     * トリミング開始位置
     */
    var trimStartPosition : Long = 0
        set(v) {
            val value = v.limit(0, trimEndPosition)
            if(value != field) {
                field = value
                trimStartPositionChanged.invoke(this, value, mDraggingInfo.draggingStateWithKnob(Knob.LEFT))
                if(currentPosition<value) {
                    currentPosition = value
                }
                applyPosition(true)
            }
        }

    /**
     * トリミング終了位置
     * Win版では、OSのAPIに合わせて、末尾（Natural Duration）を基準（ゼロ）として、末尾からのオフセットの絶対値をtrimEndとしていたが、Androidではどうしよう。。。
     */
    var trimEndPosition : Long = 0
        set(v) {
            val value = v.limit(trimStartPosition, totalLength)
            if(value != field) {
                field = value
                trimEndPositionChanged.invoke(this, value, mDraggingInfo.draggingStateWithKnob(Knob.RIGHT))
                if(currentPosition>value) {
                    currentPosition = value
                }
                applyPosition(true)
            }
        }

    /**
     * トリミングされているか？
     */
    @Suppress("unused")
    val isTrimmed : Boolean
        get() = 0<trimStartPosition || trimEndPosition<totalLength

    /**
     * true: トリミングモード有効
     * false: 通常のスライダー
     */
    private var trimmingEnabled : Boolean = false

    /**
     * スライダー操作の管理用
     */
    private val mDraggingInfo = DraggingInfo()

//    @ColorInt var railColor : Int = Color.WHITE
//    @ColorInt var railLeftColor : Int = Color.GRAY
//    @ColorInt var railNoSelColor: Int = Color.GRAY
//
//    var railHeight: Int = 8
//    var railLeftHeight: Int = 8
//    var railNoSelHeight: Int = 2

    private var drThumb : Drawable
    private var drLeft : Drawable
    private var drRight : Drawable

    private var paintRail: Paint
    private var paintRailLeft: Paint
    private var paintRailNoSel: Paint

    // dimmensions
    private var naturalHeight: Int
    private var railHeight : Int
    private var railLeftHeight : Int
    private var railNoSelHeight : Int
    private var thumbOffset: Int
    private var railOffset : Int
    private var trimmerOffset: Int

    private val maxRailHeight:Int
        get() = if(trimmingEnabled) maxOf(railHeight,railLeftHeight,railNoSelHeight) else max(railHeight,railLeftHeight)

    // updateLayout()で決定するプロパティ
    private val mRailRange = Range()
    private var mRailY: Float = 0f

    // updateLayout()でY座標とサイズを決定し、applyPositionで、X座標を決定するプロパティ
    private val mThumbRect = RectF()
    private val mTrimLeftRect = RectF()
    private val mTrimRightRect = RectF()

    // updateLayout()の結果から計算されるプロパティ
    private val mStartX
        get() = mRailRange.min
    private val mEndX
        get() = mRailRange.max
    private val mCurX
        get() = value2position(currentPosition)
    private val mTrimStartX
        get() = if(trimmingEnabled) value2position(trimStartPosition) else mStartX
    private val mTrimEndX
        get() = if(trimmingEnabled) value2position(trimEndPosition) else mEndX

    init {
        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvSlider,defStyleAttr,0)

        try {
            trimmingEnabled = sa.getBoolean(R.styleable.AmvSlider_trimmingMode, trimmingEnabled)
            totalLength = sa.getInteger(R.styleable.AmvSlider_totalLength, 1000).toLong()

            // drawables
            drThumb = sa.getDrawable(R.styleable.AmvSlider_thumb) ?: context.getDrawable(R.drawable.ic_slider_knob)
            drLeft = sa.getDrawable(R.styleable.AmvSlider_startThumb) ?: context.getDrawable(R.drawable.ic_trim_left)
            drRight = sa.getDrawable(R.styleable.AmvSlider_endThumb) ?: context.getDrawable(R.drawable.ic_trim_right)

            // colors
            val railColor = sa.getColor(R.styleable.AmvSlider_railColor, Color.WHITE)
            val railLeftColor = sa.getColor(R.styleable.AmvSlider_railLeftColor, Color.GRAY)
            val railNoSelColor = sa.getColor(R.styleable.AmvSlider_railNoSelColor, Color.GRAY)

            // dimmensions
            railHeight = sa.getDimensionPixelSize(R.styleable.AmvSlider_railHeight, context.dp2px(8))
            railLeftHeight = sa.getDimensionPixelSize(R.styleable.AmvSlider_railLeftHeight, railHeight)
            railNoSelHeight= sa.getDimensionPixelSize(R.styleable.AmvSlider_railNoSelHeight, context.dp2px(2))

            thumbOffset = sa.getDimensionPixelSize(R.styleable.AmvSlider_thumbOffset, 0)
            railOffset = sa.getDimensionPixelSize(R.styleable.AmvSlider_railOffset, thumbOffset+drThumb.intrinsicHeight)
            trimmerOffset = sa.getDimensionPixelSize(R.styleable.AmvSlider_trimmerOffset, railOffset+maxRailHeight)

            fun colordPaint(@ColorInt c:Int) : Paint {
                val p = Paint()
                p.style = Paint.Style.STROKE
                p.strokeCap = Paint.Cap.BUTT
                p.color = c
                return p
            }

            // paints
            paintRail = colordPaint(railColor)
            paintRailLeft = colordPaint(railLeftColor)
            paintRailNoSel = colordPaint(railNoSelColor)

            naturalHeight = calcNaturalHeight()
        } finally {
            sa.recycle()
        }

    }

    /**
     * 与えられたDrawableやoffset値から、スライダーの中身を表示できる適切な高さを計算する
     *
     */
    fun calcNaturalHeight() : Int {
        return maxOf(
                thumbOffset + drThumb.intrinsicWidth,
                railOffset+maxRailHeight,
                if(trimmingEnabled) trimmerOffset + drLeft.intrinsicHeight else 0
        )
    }

    /**
     * スライダーの最小幅（このサイズにしてしまうと、もはや、スライダーのノブは動かない）
     */
    fun minWidth() : Int {
        return drThumb.intrinsicWidth + if(trimmingEnabled) drLeft.intrinsicWidth else 0
    }


    /**
     * 最小値と最大値を保持するデータクラス
     */
    data class Range(var min:Float, var max:Float) {
        constructor() : this(0f,0f)
        fun set(min:Float, max:Float=min) {
            this.min = min
            this.max = max
        }
        val range: Float
            get() = max - min
    }

    /**
     * スライダー値から、ノブの座標値を求める
     */
    fun value2position(value:Long) : Float {
        return mRailRange.min + mRailRange.range * value.toFloat() / totalLength.toFloat()
    }

    /**
     * 座標値から、スライダー値を求める
     */
    fun position2value(position:Float) : Long {
        return (totalLength * (position - mRailRange.min) / mRailRange.range).roundToLong()
    }

    /**
     * RectFのX方向の中央を指定座標に移動する
     */
    fun RectF.moveHorzCenterTo(x:Float) {
        offsetTo(x-width() /2f, top)
    }
    /**
     * RectFのX方向の左端を指定座標に移動する
     */
    fun RectF.moveLeftTo(x:Float) {
        offsetTo(x, top)
    }
    /**
     * RectFのX方向の右端を指定座標に移動する
     */
    fun RectF.moveRightTo(x:Float) {
        offsetTo(x-width(), top)
    }

    /**
     * 左上隅とサイズを与えて、RectFの値を初期化する
     */
    fun RectF.setOffsetSize(left:Float, top:Float, width:Float, height:Float) {
        this.left = left
        this.top = top
        this.right = left + width
        this.bottom = top + height
    }
//    fun RectF.setWidth(width:Float) {
//        this.right = left + width
//    }

    // updateLayout()の（同じサイズでの）重複実行を回避するために、前回値を覚えておく
    var prevWidth:Int = 0
    var prevHeight:Int = 0

    /**
     * Viewのサイズが変わった場合に、レイアウト用の基本情報を更新する
     * スライダー値((Current|TrimStart|TrimEnd)Position )に依存するプロパティは、applyPosition()で変更する
     */
    fun updateLayout(width:Int, height:Int) {
        if(prevWidth == width && prevHeight==height) {
            // no changed ... return
        }
        prevWidth = width
        prevHeight = height

        // 高さがNaturalHeightと異なる場合は、そのサイズになるよう拡大/縮小する
        val scale = height / naturalHeight.toFloat()

        mThumbRect.setOffsetSize(0f,thumbOffset*scale,drThumb.intrinsicWidth*scale, drThumb.intrinsicHeight*scale)

        mRailY = (railOffset+maxRailHeight/2)*scale
        paintRail.strokeWidth = railHeight*scale
        paintRailLeft.strokeWidth = railLeftHeight*scale

        if(trimmingEnabled) {
            mTrimLeftRect.setOffsetSize(0f, trimmerOffset*scale, drLeft.intrinsicWidth*scale, drLeft.intrinsicHeight*scale)
            mTrimRightRect.set(mTrimLeftRect)
            mRailRange.set(mTrimLeftRect.width(), width-mTrimRightRect.width())
            paintRailNoSel.strokeWidth = railNoSelHeight*scale
        } else {
            mRailRange.set(0f,width.toFloat())
        }

        applyPosition(false)    // そのうち再描画されるはず
    }


    /**
     * スライダー値 ((Current|TrimStart|TrimEnd)Position) が変更されたときに、ノブの位置/レールの表示に反映する
     */
    fun applyPosition(redraw:Boolean) {
        if(0L==totalLength) {
            return
        }
        mThumbRect.moveHorzCenterTo(mCurX)

        if(trimmingEnabled) {
            mTrimLeftRect.moveRightTo(mTrimStartX)
            mTrimRightRect.moveLeftTo(mTrimEndX)
        }
        if(redraw) {
            invalidate()
        }
    }

    /**
     * スライダーのドラッグ状態
     */
    enum class SliderDragState {
        NONE,       // ドラッグ中ではない
        BEGIN,      // ドラッグが開始される
        MOVING,     // まさにドラッグ中
        END,        // ドラッグが終了する
    }

    // Event Handler
    class SliderValueChangedListener : FuncyListener3<AmvSlider, Long, SliderDragState, Unit>() {
        interface IHandler {    // for Java
            /**
             *
             * @param caller イベントを発行したAmvSlider
             * @param position シーク位置
             * @param completed false:ドラッグ中 / true:ドラッグ終了
             */
            fun sliderValueChanged(caller:AmvSlider, position:Long, completed:SliderDragState)
        }
        fun set(listener:IHandler) {
            funcy = Funcy3(listener::sliderValueChanged)
        }
    }

//    /**
//     * スライダー上をタップされた
//     */
//    val tappedOnSlider = SliderValueChangedListener()

    //
    // Properties
    //

    private fun Long.limit(min:Long, max:Long) : Long {
        val (s,e) = if (min<max) Pair(min,max) else Pair(max,min)
        return when {
            this<s -> s
            this>e -> e
            else -> this
        }
    }

    // Viewの処理

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
            MeasureSpec.AT_MOST-> Math.min(naturalHeight, heightSize)
            MeasureSpec.UNSPECIFIED->naturalHeight
            else -> naturalHeight
        }
        setMeasuredDimension(width,height)
    }

    /**
     * レイアウト実行
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        updateLayout(right-left, bottom-top)
    }

    /**
     * サイズが変更されたときの処理
     */
//    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
//        updateLayout(w,h)
//    }

    /**
     * レールを描画する
     */
    private fun Float.drawRail(canvas:Canvas, end:Float, paint:Paint) : Float {
        if(this != end) {
            canvas.drawLine(this, mRailY, end, mRailY, paint)
        }
        return end
    }

    private fun RectF.toRect(w:Rect) : Rect {
        w.left = left.roundToInt()
        w.top = top.roundToInt()
        w.right = right.roundToInt()
        w.bottom = bottom.roundToInt()
        return w
    }

    // 描画の作業用
    private val mWorkRect = Rect()

    override fun onDraw(canvas: Canvas?) {
        if(null ==canvas) {
            return
        }
        // rail
        mStartX
            .drawRail(canvas, mTrimStartX, paintRailNoSel)
            .drawRail(canvas, mCurX, paintRailLeft)
            .drawRail(canvas, mTrimEndX, paintRail)
            .drawRail(canvas, mEndX, paintRailNoSel)

        // thumb
        drThumb.bounds = mThumbRect.toRect(mWorkRect)
        drThumb.draw(canvas)

        if(trimmingEnabled) {
            drLeft.bounds = mTrimLeftRect.toRect(mWorkRect)
            drLeft.draw(canvas)
            drRight.bounds = mTrimRightRect.toRect(mWorkRect)
            drRight.draw(canvas)
        }
    }

    /**
     * ノブの種別
     */
    enum class Knob {
        NONE, THUMB, LEFT, RIGHT
    }

    /**
     * ドラッグ操作を管理するクラス
     */
    inner class DraggingInfo {
        // ドラッグ中のノブ
        var knob: Knob = Knob.NONE
        // ドラッグ開始時のタップ位置とノブの間隔・・・ドラッグ開始時のノブとタッチ位置の位置関係を維持したままドラッグするため
        var offset: Float = 0f
        // ノブ毎のイベント通知
        val listener: SliderValueChangedListener?
            get() {
                return when(knob) {
                    Knob.THUMB -> currentPositionChanged
                    Knob.LEFT-> trimStartPositionChanged
                    Knob.RIGHT -> trimEndPositionChanged
                    else -> null
                }
            }
        // ノブ毎のスライダー値
        var value : Long
            get() {
                return when(knob) {
                    Knob.THUMB -> currentPosition
                    Knob.LEFT-> trimStartPosition
                    Knob.RIGHT -> trimEndPosition
                    else -> 0
                }
            }
            set(v) {
                when(knob) {
                    Knob.THUMB -> currentPosition = v
                    Knob.LEFT-> trimStartPosition = v
                    Knob.RIGHT -> trimEndPosition = v
                    else -> {}
                }
            }

        /**
         * 非ドラッグ状態に戻す
         */
        fun reset() {
            knob = Knob.NONE
            offset = 0f
        }

//        /**
//         * ドラッグ中か
//         */
//        val isDragging
//            get() = knob != Knob.NONE
//
//        /**
//         * ドラッグ状態を返す(MOVING or NONE)
//         */
//        val draggingState : SliderDragState
//            get() = if(isDragging) SliderDragState.MOVING else SliderDragState.NONE

        /**
         * knob はドラッグ中か？
         */
        fun draggingStateWithKnob(knob:Knob) : SliderDragState {
            return if(knob == this.knob) SliderDragState.MOVING else SliderDragState.NONE
        }

        /**
         * ドラッグ開始
         */
        fun initAt(x:Float, y:Float) {
            reset()

            if(trimmingEnabled) {
                when {
                    mThumbRect.contains(x,y) -> {
                        knob = Knob.THUMB
                        offset = mThumbRect.centerX() - x
                    }
                    mTrimLeftRect.contains(x,y)-> {
                        knob = Knob.LEFT
                        offset = mTrimLeftRect.right - x
                    }
                    mTrimRightRect.contains(x,y)-> {
                        knob = Knob.RIGHT
                        offset = mTrimRightRect.left - x
                    }
                }
            } else {
                knob = Knob.THUMB
                if(mThumbRect.contains(x,y)) {
                    offset = mThumbRect.centerX() - x
                } else {
                    currentPosition = position2value(x)    // この時の currentPositionChangedイベントは、SliderDragState.NONE で発行される
                    offset = 0f
                }
            }
            listener?.invoke(this@AmvSlider, value, SliderDragState.BEGIN)
        }

        /**
         * ドラッグ中
         */
        fun moveTo(x:Float) {
            if(Knob.NONE == knob) {
                return
            }
            value = position2value(x+offset)
        }

        /**
         * ドラッグ終了
         */
        fun finish() {
            listener?.invoke(this@AmvSlider, value, SliderDragState.END)
            reset()
        }

    }

    /**
     * タッチイベントのハンドラ
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mDraggingInfo.initAt(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                mDraggingInfo.moveTo(event.x)
            }

            MotionEvent.ACTION_UP -> {
                mDraggingInfo.finish()
            }
        }
        return true
    }

    override fun onSaveInstanceState(): Parcelable {
        val parent = super.onSaveInstanceState()
        return SavedState(parent).apply {
            totalLength = this@AmvSlider.totalLength
            currentPosition = this@AmvSlider.currentPosition
            trimStartPosition = this@AmvSlider.trimStartPosition
            trimEndPosition = this@AmvSlider.trimEndPosition
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if(state is SavedState) {
            state.apply {
                super.onRestoreInstanceState(superState)
                this@AmvSlider.totalLength = totalLength
                this@AmvSlider.currentPosition = currentPosition
                this@AmvSlider.trimStartPosition = trimStartPosition
                this@AmvSlider.trimEndPosition = trimEndPosition
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    internal class SavedState : View.BaseSavedState {
        var totalLength : Long = 0
        var currentPosition : Long = 0
        var trimStartPosition : Long = 0
        var trimEndPosition : Long = 0

        /**
         * Constructor called from [AmvSlider.onSaveInstanceState]
         */
        constructor(superState: Parcelable) : super(superState) {}

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(percel: Parcel) : super(percel) {
            totalLength = percel.readLong()
            trimStartPosition = percel.readLong()
            trimEndPosition = percel.readLong()
            currentPosition = percel.readLong()
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            super.writeToParcel(parcel, flags)
            parcel.writeLong(totalLength)
            parcel.writeLong(trimStartPosition)
            parcel.writeLong(trimEndPosition)
            parcel.writeLong(currentPosition)
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
}