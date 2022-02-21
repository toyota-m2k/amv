/**
 * フレームサムネイルリストビュー
 *
 * @author M.TOYOTA 2018.07.11 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.KeyEvent.ACTION_DOWN
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.michael.utils.FuncyListener1
import com.michael.utils.FuncyListener2
import kotlin.math.roundToInt

class AmvFrameListView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
    ) : FrameLayout(context, attrs, defStyleAttr) {

    // region Internals

    private var mKnobPosition = 0L          // 主ノブの位置（TrimStart/Endの管理は、scrollerに任せる
    private val controls = Controls()
    private val trimmingEnabled:Boolean
        get() = controls.scroller.trimmingEnabled

    private inner class Controls {
        val scroller: AmvHorzScrollView by lazy {
            findViewById(R.id.flv_scroller)
        }
        val knob: View by lazy {
            findViewById(R.id.flv_knob)
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.frame_list_view, this) as FrameLayout
        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvFrameListView,defStyleAttr,0)
        try {
            controls.scroller.trimmingEnabled = sa.getBoolean(R.styleable.AmvFrameListView_trimmingMode, false)
            val left = sa.getDimensionPixelSize(R.styleable.AmvFrameListView_extentLeft, 0)
            val right = sa.getDimensionPixelSize(R.styleable.AmvFrameListView_extentRight, 0)
            if(left>0||right>0) {
                setExtentWidth(left, right)
            }
        } finally {
            sa.recycle()
        }

        if(controls.scroller.trimmingEnabled) {
            controls.knob.background = ContextCompat.getDrawable(context, R.drawable.ic_slider_trim_guide)
            controls.knob.visibility = View.GONE
        }
    }

    // endregion

    // region Public APIs

    /**
     * お友達（AmvSlider）にタッチイベントを伝えるためのリスナー
     * これにより、このビュー上でのドラッグ操作を、AmvSlider上でのタッチ操作と同じように扱うことができる。
     */
    val touchFriendListener = FuncyListener1<MotionEvent, Boolean>()

    /**
     * トリミング用にフレームリストを使う場合のおともだち追加処理
     */
    val trimmingFriendListener = FuncyListener2<MotionEvent, AmvSlider.Knob, Boolean>()

    private var frameWidth:Int = 0      // 1フレームの幅(px)
    private var frameCount:Int = 0      // フレーム数
    private var currentImageCount = 0   // 現在リストに登録済みのフレーム数

    /**
     * 動画情報が取得されたときの初期化処理
     */
    fun prepare(frameCount: Int, frameWidth: Int, frameHeight: Int) {
        this.frameCount = frameCount
        this.frameWidth = frameWidth
        currentImageCount = 0
        controls.scroller.prepare(frameCount,frameWidth, frameHeight)
        controls.scroller.setLayoutHeight(frameHeight)
        this.setLayoutHeight(frameHeight)
        updateScroll()

        for(i in 0 until frameCount) {
            controls.scroller.addImage(ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                layoutParams = ViewGroup.LayoutParams(frameWidth, frameHeight)
            })
        }

    }

    /**
     * スクローラー内部のスクロールコンテンツの幅(px)
     */
    val contentWidth:Int
        get() = controls.scroller.contentWidth

    /**
     * 左側の余白(px)・・・タッチは有効
     * トリミングモードのときに、スライダーのレールからはみ出したトリミングノブの幅に合わせる
     */
    val rightExtentWidth
        get() = paddingLeft

    /**
     * 右側の余白(px)・・・以下同文
     */
    val leftExtentWidth:Int
        get() = paddingRight

    /**
     * 余白を設定（通常はxmlで指定することを想定しているが、AmvSliderの設定を反映させるには、プログラムから設定するほうがよいのかも）
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun setExtentWidth(left:Int, right:Int) {
        setPadding(left, 0,right,0)
    }

    /**
     * フレーム画像を追加
     */
    fun add(bmp: Bitmap) {
        val view = controls.scroller.getImageViewAt(currentImageCount) ?: return
        view.setImageBitmap(bmp)
        currentImageCount++
    }

    /**
     * フレーム画像を一括設定
     */
    fun setFrames(frameList: ArrayList<Bitmap>) {
        for(i in currentImageCount until frameList.size) {
            add(frameList[i])
        }
        // Android版はフレームの抽出が遅く、再生開始後しばらくフレームリストの空白が目立ってブサイクなので、
        // 最後のフレーム画像で残りのフレームを埋めることにより、少し見栄えを改善する
        // #3153対応のおまけ
        for(i in currentImageCount until frameCount) {
            val view = controls.scroller.getImageViewAt(i) ?: return
            view.setImageBitmap(frameList[frameList.size-1])
        }
    }

    /**
     * レンジ = natural duration
     */
    var totalRange: Long
        get() = controls.scroller.totalRange
        set(v) {
            mKnobPosition = 0
            controls.scroller.totalRange = v
            updateScroll()
        }

    /**
     * カレント位置
     */
    var position: Long
        get() = mKnobPosition
        set(v) {
            mKnobPosition = v
            controls.scroller.position = v
            updateScroll()
        }

    /**
     * トリミング開始位置
     */
    var trimStart: Long
        get() = controls.scroller.trimStart
        set(v) {
            controls.scroller.trimStart = v
            updateScroll()
        }

    /**
     * トリミング終了位置
     */
    var trimEnd: Long
        get() = controls.scroller.trimEnd
        set(v) {
            controls.scroller.trimEnd = v
            updateScroll()
        }

    /**
     * カレント位置を表示するか？
     */
    var showKnob:Boolean
        get() = controls.knob.visibility == View.VISIBLE
        set(v) {
            controls.knob.visibility = if(v) View.VISIBLE else View.GONE
        }

    // endregion

    // region Scroll / Rendering

    private val sliderWidthPixel: Int
        get() = getLayoutWidth() - paddingLeft - paddingRight

    private fun updateScroll() {
        val range = sliderWidthPixel
        val knobPos = if(trimmingEnabled) {     // 本当は、trimmingが有効かどうかではなく、AmvSlider.endToEndRail と一致させるべき
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

    // endregion

    // region Touch event handling

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (null != event) {
            if(touchFriendListener.invoke(event) == true) {
               return true
            }

            if(trimmingEnabled && event.action == ACTION_DOWN) {
                return trimmingFriendListener.invoke(event, controls.scroller.hitTest(event.x-paddingLeft))?:false
            }
        }
        return false
    }

    // endregion
}

