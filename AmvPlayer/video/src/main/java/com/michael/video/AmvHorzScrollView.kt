package com.michael.video

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * タッチ操作を受け付けない横スクローラー
 * もともと、↑だけの目的だったが、トリミング時のマスクの操作は、このビューで行う方が合理的なので、その機能をAmvFrameListViewからこちらに移管した。
 * マスクを操作するために、スクロールやTrimming関係の管理情報もこちらに持たせてしまうことにしたので、AmvFrameListViewは、主ノブの位置だけを管理することになった。
 */
class AmvHorzScrollView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
        ) : FrameLayout(context, attrs, defStyleAttr) {

    // Region Internals

    private inner class Controls {
        val container : LinearLayout by lazy { findViewById<LinearLayout>(R.id.imageList) }
        val leftTruncated: View by lazy { findViewById<View>(R.id.leftTruncated) }
        val rightTruncated: View by lazy { findViewById<View>(R.id.rightTruncated) }
        val leftTruncatedBar: View by lazy { findViewById<View>(R.id.leftTruncatedBar)}
        val rightTruncatedBar: View by lazy { findViewById<View>(R.id.rightTruncatedBar) }

    }

    private inner class Models {

        var trimmingEnabled = false
        var position:Long = 0           // スライダーのスクロール位置
        var totalRange:Long = 1000

        var leftMask:Long = 0
        var rightMask:Long = 1000

        val scrollPixel: Int
            get() = scrollValueInPixel(position)
        val leftMaskPixel:Int
            get() = valueToPixel(leftMask)
        val rightMaskPixel:Int
            get() = valueToPixel(rightMask)

        fun resetWithTotalRange(range:Long) {
            totalRange = if(range!=0L) range else 1000
            rightMask = totalRange
            leftMask = 0
            position = 0
        }
    }

    private val controls = Controls()
    private val models = Models()

    // endregion

    // region Publics (refered from AmvFrameListView)

    /**
     * トリミングは有効か？
     */
    var trimmingEnabled:Boolean
        get() = models.trimmingEnabled
        set(enabled) {
            models.trimmingEnabled = enabled

            val visibility = if(enabled) View.VISIBLE else View.GONE
            controls.leftTruncated.visibility = visibility
            controls.rightTruncated.visibility = visibility
            controls.leftTruncatedBar.visibility = visibility
            controls.rightTruncatedBar.visibility = visibility
        }

    /**
     * スクロールレンジ（＝natural duration)(ms)
     */
    var totalRange : Long
        get() = models.totalRange
        set(v) {
            models.resetWithTotalRange(v)
            updateScroll()
        }

    /**
     * スクロール位置(ms)
     */
    var position: Long
        get() = models.position
        set(v) {
            models.position = v
            updateScroll()
        }

    /**
     * トリミング開始位置(ms)
     */
    var trimStart:Long
        get() = models.leftMask
        set(v) {
            models.leftMask = v
            controls.leftTruncated.setLayoutWidth(models.leftMaskPixel)
            //updateLeftMaskMargin()
            models.position = v
            updateScroll()
        }

    /**
     * トリミング終了位置(ms)
     */
    var trimEnd:Long
        get() = models.rightMask
        set(v) {
            models.rightMask = v
            controls.rightTruncated.setLayoutWidth(models.rightMaskPixel)
            //updateRightMaskMargin()
            models.position = v
            updateScroll()
        }


    /**
     * フレーム数、サイズを指定する
     */
    fun prepare(frameCount: Int, frameWidth: Int, frameHeight: Int) {
        controls.container.removeAllViews()
        controls.container.setLayoutWidth(frameCount * frameWidth)
        controls.container.setLayoutHeight(frameHeight)
    }

    /**
     * フレームのビットマップ（を持ったImageView）を追加する
     */
    fun addImage(img: ImageView) {
        controls.container.addView(img)
    }

    val imageCount:Int
        get() = controls.container.childCount

    /**
     * 値（動画のシーク位置：Long）から、Viewの位置(Pixel)を取得
     */
    private fun valueToPixel(v:Long) : Int {
        return ((v * controls.container.getLayoutWidth())/models.totalRange.toFloat()).toInt()
    }

    /**
     * スクロール可動範囲（ピクセル）
     */
    private val scrollablePixel: Int
        get() = controls.container.getLayoutWidth() - this.getLayoutWidth()

    /**
     * スクロール位置(ms)に対するスクロール量（ピクセル）を取得
     */
    private fun scrollValueInPixel(position:Long) : Int {
        return ((position * scrollablePixel) / models.totalRange.toFloat()).toInt()
    }

    /**
     * スクロールを実行
     */
    private fun updateScroll() {
        val scr = models.scrollPixel
        controls.container.setMargin(-scr, 0, 0, 0)
        updateLeftMaskMargin(scr)
        updateRightMaskMargin(scr)
    }

    private fun updateLeftMaskMargin(scr:Int = models.scrollPixel) {
        controls.leftTruncated.setMargin(-scr,0,0,0)
        controls.leftTruncatedBar.setMargin(-scr+models.leftMaskPixel-controls.leftTruncatedBar.getLayoutWidth(), 0,0,0)

    }
    private fun updateRightMaskMargin(scr:Int = models.scrollPixel) {
        controls.rightTruncated.setMargin(-scr+models.rightMaskPixel, 0, 0,0)
        controls.rightTruncatedBar.setMargin(-scr+models.rightMaskPixel,0,0,0)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        return false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateScroll()
    }

}