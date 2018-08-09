/**
 * トラッキング、トリミング兼用 スライダービュー
 *
 * @author M.TOYOTA 2018.07.12 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
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
    private var mValueRange = 1000L

    val valueRange : Long    // = Natural Duration
        get() = mValueRange

    fun resetWithValueRange(v:Long, redraw:Boolean) {
        mValueRange = v
        trimEndPosition = v
        trimStartPosition = 0
        currentPosition = 0
        applyPosition(redraw)
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
            val value = v.limit(trimStartPosition, valueRange)
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
    val isTrimmed : Boolean
        get() = 0<trimStartPosition || trimEndPosition<valueRange

    /**
     * スライダーのドラッグ操作中か？
     */
    @Suppress("unused")
    val isDragging : Boolean
        get() = mDraggingInfo.isDragging

    var showThumbBg : Boolean = true
        set(v) {
            if(field != v) {
                field = v
                invalidate()
            }
        }

    /**
     * スライダーレールの外側にはみ出す幅（左）
     */
    val leftExtentWidth:Float
        get() {
            return if(trimmingEnabled) {
                mTrimLeftRect.width()
            } else if(!endToEndRail) {
                mThumbRect.width()/2
            } else {
                0f
            }
        }
    /**
     * スライダーレールの外側にはみ出す幅（右）
     */
    val rightExtentWidth:Float
        get() {
            return if(trimmingEnabled) {
                mTrimRightRect.width()
            } else if(!endToEndRail) {
                mThumbRect.width()/2
            } else {
                0f
            }
        }

    /**
     * スライダーレールの外側にはみ出す幅（左右合計）
     */
    val extentWidth:Float
        get() {
            return if(trimmingEnabled) {
                mTrimRightRect.width() + mTrimLeftRect.width()
            } else if(!endToEndRail) {
                mThumbRect.width()
            } else {
                0f
            }
        }

    /**
     * true: トリミングモード有効
     * false: 通常のスライダー
     */
    private var trimmingEnabled : Boolean = false

    /**
     * trimmingEnabled == true の場合に、レールをビューの端から端まで引っ張るかどうか
     */
    private var endToEndRail : Boolean = false

    /**
     * スライダー操作の管理用
     */
    private val mDraggingInfo = DraggingInfo()

    private val drThumb : Drawable
    private val drLeft : Drawable
    private val drRight : Drawable

    private val drThumbBg : Drawable

    private val paintRail: Paint
    private val paintRailLeft: Paint
    private val paintRailNoSel: Paint

    // dimensions
    private val naturalHeight: Int
    private val railHeight : Int
    private val railLeftHeight : Int
    private val railNoSelHeight : Int
    private val thumbOffset: Int
    private val railOffset : Int
    private val trimmerOffset: Int

    private val maxRailHeight:Int
        get() = if(trimmingEnabled) maxOf(railHeight,railLeftHeight,railNoSelHeight) else max(railHeight,railLeftHeight)

    // updateLayout()で決定するプロパティ
    private val mSliderRange = Range()
    private var mRailY: Float = 0f
//    private var mScale: Float = 1f

    // updateLayout()でY座標とサイズを決定し、applyPositionで、X座標を決定するプロパティ
    private val mThumbRect = RectF()
    private val mTrimLeftRect = RectF()
    private val mTrimRightRect = RectF()

    // updateLayout()の結果から計算されるプロパティ（レールの描画用）
    private val mDrawingRailStart   // レールの描画先頭位置・・・endToEndRailを考慮するのでmRailRangeとは異なる
        get() = if(endToEndRail) 0f else mSliderRange.min
    private val mDrawingRailEnd     // レールの描画終端位置・・・endToEndRailを考慮するのでmRailRangeとは異なる
        get() = if(endToEndRail) viewWidth.toFloat() else mSliderRange.max

    // 操作量（表示用）
    private val mCurX               // シークノブの位置
        get() = value2position(currentPosition)
    private val mTrimStartX         // 左ノブの位置
        get() = if(trimmingEnabled) value2position(trimStartPosition) else mDrawingRailStart
    private val mTrimEndX           // 右ノブの位置
        get() = if(trimmingEnabled) value2position(trimEndPosition) else mDrawingRailEnd

    init {
        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvSlider,defStyleAttr,0)

        try {
            trimmingEnabled = sa.getBoolean(R.styleable.AmvSlider_trimmingMode, trimmingEnabled)
            if(!trimmingEnabled) {
                endToEndRail = sa.getBoolean(R.styleable.AmvSlider_endToEndRail, true)
            }
            resetWithValueRange(sa.getInteger(R.styleable.AmvSlider_valueRange, 1000).toLong(), false)

            // drawables
            drThumb = sa.getDrawable(R.styleable.AmvSlider_thumb) ?: context.getDrawable(R.drawable.ic_slider_knob)
            drLeft = sa.getDrawable(R.styleable.AmvSlider_startThumb) ?: context.getDrawable(R.drawable.ic_trim_left)
            drRight = sa.getDrawable(R.styleable.AmvSlider_endThumb) ?: context.getDrawable(R.drawable.ic_trim_right)

            drThumbBg = sa.getDrawable(R.styleable.AmvSlider_thumbBg) ?: context.getDrawable(R.drawable.ic_slider_knob_bg)
            showThumbBg = sa.getBoolean(R.styleable.AmvSlider_showThumbBg, false)

            // colors
            val railColor = sa.getColor(R.styleable.AmvSlider_railColor, Color.WHITE)
            val railLeftColor = sa.getColor(R.styleable.AmvSlider_railLeftColor, Color.GRAY)
            val railNoSelColor = sa.getColor(R.styleable.AmvSlider_railNoSelColor, Color.GRAY)

            // dimensions
            railHeight = sa.getDimensionPixelSize(R.styleable.AmvSlider_railHeight, context.dp2px(8))
            railLeftHeight = sa.getDimensionPixelSize(R.styleable.AmvSlider_railLeftHeight, railHeight)
            railNoSelHeight= sa.getDimensionPixelSize(R.styleable.AmvSlider_railNoSelHeight, context.dp2px(2))

            thumbOffset = sa.getDimensionPixelSize(R.styleable.AmvSlider_thumbOffset, 0)
            var defRailOffset = thumbOffset+ drThumb.intrinsicHeight
            if(endToEndRail) {
                defRailOffset/=2    // EndToEndモードのときは、スライダー左右端の無効領域を隠すため、ノブをめり込ませる
            }

            railOffset = sa.getDimensionPixelSize(R.styleable.AmvSlider_railOffset, defRailOffset )
            trimmerOffset = sa.getDimensionPixelSize(R.styleable.AmvSlider_trimmerOffset, railOffset)

            isSaveFromParentEnabled = sa.getBoolean(R.styleable.AmvSlider_saveFromParent, true)

            fun coloredPaint(@ColorInt c:Int) : Paint {
                return Paint().apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.BUTT
                    color = c
                }
            }

            // paints
            paintRail = coloredPaint(railColor)
            paintRailLeft = coloredPaint(railLeftColor)
            paintRailNoSel = coloredPaint(railNoSelColor)

            naturalHeight = calcNaturalHeight()

            initLayoutConstants()
        } finally {
            sa.recycle()
        }

    }

    /**
     * 与えられたDrawableやoffset値から、スライダーの中身を表示できる適切な高さを計算する
     *
     */
    private fun calcNaturalHeight() : Int {
        return maxOf(
                thumbOffset + drThumb.intrinsicHeight,
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
    private fun value2position(value:Long) : Float {
        return mSliderRange.min + mSliderRange.range * value.toFloat() / valueRange.toFloat()
    }

    /**
     * 座標値から、スライダー値を求める
     */
    fun position2value(position:Float) : Long {
        return (valueRange * (position - mSliderRange.min) / mSliderRange.range).roundToLong()
    }

    /**
     * RectFのX方向の中央を指定座標に移動する
     */
    private fun RectF.moveHorizontalCenterTo(x:Float) {
        offsetTo(x-width() /2f, top)
    }
    /**
     * RectFのX方向の左端を指定座標に移動する
     */
    private fun RectF.moveLeftTo(x:Float) {
        offsetTo(x, top)
    }
    /**
     * RectFのX方向の右端を指定座標に移動する
     */
    private fun RectF.moveRightTo(x:Float) {
        offsetTo(x-width(), top)
    }

    /**
     * 左上隅とサイズを与えて、RectFの値を初期化する
     */
    private fun RectF.setOffsetSize(left:Int, top:Int, width:Int, height:Int) {
        this.left = left.toFloat()
        this.top = top.toFloat()
        this.right = this.left + width
        this.bottom = this.top + height
    }

    // updateLayout()の（同じサイズでの）重複実行を回避するために、前回値を覚えておく
    // もともと↑の用途だったが、endToEndRailモードのときに、widthが必要になるので、現在のビューサイズを覚えておくプロパティに格上げ
    private var viewWidth:Int = 0

    /**
     * レイアウト情報のうち、スライダー値やビューの幅に依存しない定数となるものを初期化
     */
    private fun initLayoutConstants() {
        mThumbRect.setOffsetSize(0,thumbOffset,drThumb.intrinsicWidth, drThumb.intrinsicHeight)
        mRailY = railOffset+maxRailHeight/2f

        paintRail.strokeWidth = railHeight.toFloat()
        paintRailLeft.strokeWidth = railLeftHeight.toFloat()

        if(trimmingEnabled) {
            mTrimLeftRect.setOffsetSize(0, trimmerOffset, drLeft.intrinsicWidth, drLeft.intrinsicHeight)
            mTrimRightRect.set(mTrimLeftRect)
            paintRailNoSel.strokeWidth = railNoSelHeight.toFloat()
        }
    }

    /**
     * ビューのサイズが変化したときに、ビューの幅に依存するレイアウト情報を更新する
     * スライダー値((Current|TrimStart|TrimEnd)Position )に依存するプロパティは、applyPosition()で変更する
     */
    private fun updateLayout(width:Int) {
        if(viewWidth == width) {
            // no changed ... return
        }
        viewWidth = width

        if(trimmingEnabled) {
            mSliderRange.set(mTrimLeftRect.width(), width-mTrimRightRect.width())
        } else {
            mSliderRange.set(mThumbRect.width()/2,width.toFloat()-mThumbRect.width()/2)
        }
        applyPosition(false)    // そのうち再描画されるはず
    }

    /**
     * スライダー値 ((Current|TrimStart|TrimEnd)Position) が変更されたときに、ノブの位置/レールの表示に反映する
     */
    private fun applyPosition(redraw:Boolean) {
        if(0L==valueRange) {
            return
        }
        mThumbRect.moveHorizontalCenterTo(mCurX)

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
             * @param dragState false:ドラッグ中 / true:ドラッグ終了
             */
            fun sliderValueChanged(caller:AmvSlider, position:Long, dragState:SliderDragState)
        }
        fun set(listener:IHandler?) {
            if(null!=listener) {
                funcy = Funcy3(listener::sliderValueChanged)
            } else {
                reset()
            }
        }
    }

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

        // 当初、高さがNaturalHeightと異なる場合は、そのサイズになるよう拡大/縮小するために、mScale( = height / naturalHeight) を保持して位置調整していたが、
        // 初期化時にパーツのサイズ（特にextentWidth）が確定しないため、他の連動するビュー（フレームリストやプレーヤー）の位置調整ができなくなるので、
        // 高さは naturalHeight 固定とする。
        //
        // 変更前：3f4b7058dba6bd98a1f86d9e5c3d32b9820851c3
        // 変更後：da56b5b32b1ac2d5ec55fdf2d3920146f2e48c31


        // val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        // val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)
        // val height = when(heightMode) {
        //     MeasureSpec.EXACTLY -> heightSize
        //     MeasureSpec.AT_MOST-> Math.min(naturalHeight, heightSize)
        //     MeasureSpec.UNSPECIFIED->naturalHeight
        //     else -> naturalHeight
        // }

        val height = naturalHeight
        setMeasuredDimension(width,height)
    }

    /**
     * レイアウト実行
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        updateLayout(right-left)
    }

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
        mDrawingRailStart
            .drawRail(canvas, mTrimStartX, paintRailNoSel)
            .drawRail(canvas, mCurX, paintRailLeft)
            .drawRail(canvas, mTrimEndX, paintRail)
            .drawRail(canvas, mDrawingRailEnd, paintRailNoSel)

        // thumb
        mThumbRect.toRect(mWorkRect)
        if(showThumbBg) {
            drThumbBg.bounds = mWorkRect
            drThumbBg.draw(canvas)
        }
        drThumb.bounds = mWorkRect
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

    fun isKnobDragging(knob:Knob) : Boolean {
        return mDraggingInfo.draggingStateWithKnob(knob) == SliderDragState.MOVING
    }

    /**
     * ドラッグ操作を管理するクラス
     */
    inner class DraggingInfo {
        // ドラッグ中のノブ
        private var knob: Knob = Knob.NONE
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
        private fun reset() {
            knob = Knob.NONE
            offset = 0f
        }

        /**
         * ドラッグ中か
         */
        val isDragging
            get() = knob != Knob.NONE
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

        private fun RectF.containsX(x:Float) : Boolean {
            return x in left .. right
        }

        /**
         * ドラッグ開始
         */
        fun initAt(x:Float, y:Float, fromFriend:Boolean) :Boolean {
            reset()

            var result = false
            if(trimmingEnabled) {
                if(!fromFriend && mThumbRect.contains(x,y)){
                    knob = Knob.THUMB
                    offset = mThumbRect.centerX() - x
                    result = true
                } else if(fromFriend || y>=this@AmvSlider.mRailY) {
                    if (mTrimLeftRect.containsX(x)) {
                        knob = Knob.LEFT
                        offset = mTrimLeftRect.right - x
                        result = true
                    } else if(mTrimRightRect.containsX(x)) {
                        knob = Knob.RIGHT
                        offset = mTrimRightRect.left - x
                        result = true
                    }
                }
            } else {
                knob = Knob.THUMB
                if(mThumbRect.contains(x,y)) {
                    offset = mThumbRect.centerX() - x
                    result = true
                } else {
                    currentPosition = position2value(x)    // この時の currentPositionChangedイベントは、SliderDragState.NONE で発行される
                    offset = 0f
                    result = true
                }
            }
            listener?.invoke(this@AmvSlider, value, SliderDragState.BEGIN)
            return result
        }

        fun initByFriend(type:Knob) : Boolean {
            return when(type) {
                Knob.LEFT, Knob.RIGHT -> {
                    knob = type
                    offset = 0f
                    true
                }
                else -> false
            }
        }

        /**
         * ドラッグ中
         */
        fun moveTo(x:Float) : Boolean {
            if(Knob.NONE == knob) {
                return false
            }
            value = position2value(x+offset)
            return true
        }

        /**
         * ドラッグ終了
         */
        fun finish() : Boolean {
            if(Knob.NONE == knob) {
                return false
            }
            listener?.invoke(this@AmvSlider, value, SliderDragState.END)
            reset()
            return true
        }

    }

    private fun handleTouchEvent(action:Int, x:Float, y:Float, friend:Boolean) : Boolean {
        return when (action) {
            MotionEvent.ACTION_DOWN -> {
                mDraggingInfo.initAt(x, y, friend)
            }

            MotionEvent.ACTION_MOVE -> {
                mDraggingInfo.moveTo(x)
            }

            MotionEvent.ACTION_UP -> {
                mDraggingInfo.finish()
            }

            else -> { false }
        }
    }

    /**
     * タッチイベントのハンドラ
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleTouchEvent(event.action, event.x, event.y, false)
    }

    /**
     * お友達（AmvFrameListView）のタッチイベントを我がことのように扱うためのi/f
     */
    fun onTouchAtFriend(event: MotionEvent) : Boolean {
        return handleTouchEvent(event.action, event.x, event.y, true)
    }

    /**
     * トリミング用にフレームリストを使う場合のおともだち追加処理
     */
    fun onTrimmingAtFriend(@Suppress("UNUSED_PARAMETER") event: MotionEvent, knob:Knob) : Boolean {
        return mDraggingInfo.initByFriend(knob)
    }

    /**
     * 状態保存・・・AmvVideoPlayerが状態の保存・復元を行うことにより、AmvVideoControllerの状態が自動的に復元されるので、
     * 今回の実装では、これが呼ばれることはない。
     */
    override fun onSaveInstanceState(): Parcelable {
        val parent = super.onSaveInstanceState()
        return SavedState(parent).apply {
            totalLength = this@AmvSlider.valueRange
            currentPosition = this@AmvSlider.currentPosition
            trimStartPosition = this@AmvSlider.trimStartPosition
            trimEndPosition = this@AmvSlider.trimEndPosition
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if(state is SavedState) {
            state.apply {
                super.onRestoreInstanceState(superState)
                this@AmvSlider.mValueRange = totalLength
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
        constructor(superState: Parcelable) : super(superState)

        /**
         * Constructor called from [.CREATOR]
         */
        private constructor(parcel: Parcel) : super(parcel) {
            totalLength = parcel.readLong()
            trimStartPosition = parcel.readLong()
            trimEndPosition = parcel.readLong()
            currentPosition = parcel.readLong()
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