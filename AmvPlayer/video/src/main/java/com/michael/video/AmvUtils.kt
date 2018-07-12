package com.michael.video

import android.content.Context
import android.graphics.RectF
import android.util.Size
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/**
 * MutableなSize型
 */
@Suppress("unused")
data class MuSize(var height: Float, var width: Float) {

    constructor() : this(0f,0f)
    constructor(v:Float) : this(v,v)
    constructor(s: SizeF) : this(s.width, s.height)
    constructor(s: Size) : this(s.width.toFloat(), s.height.toFloat())

    fun copyFrom(s:MuSize) {
        width = s.width
        height = s.height
    }
    fun copyFrom(s:SizeF) {
        width = s.width
        height = s.height
    }
    fun set(width:Float, height:Float) {
        this.width = width
        this.height = height
    }

    val asSizeF:SizeF
        get() = SizeF(width,height)

    val asSize: Size
        get() = Size(width.toInt(), height.toInt())

    val isEmpty:Boolean
        get() = width==0f && height==0f
}



fun View.setLayoutWidth(width:Int) {
    val params = layoutParams
    if(null!=params) {
        params.width = width
        layoutParams = params
    }
}

fun View.setLayoutHeight(height:Int) {
    val params = layoutParams
    if(null!=params) {
        params.height = height
        layoutParams = params
    }
}

fun View.setMargin(left:Int, top:Int, right:Int, bottom:Int) {
    val p = layoutParams as? ViewGroup.MarginLayoutParams
    if(null!=p) {
        p.setMargins(left, top, right, bottom)
        layoutParams = p
    }

}

fun Context.dp2px(dp:Float) : Float {
    return resources.displayMetrics.density * dp
}

fun Context.dp2px(dp:Int) : Int {
    return (resources.displayMetrics.density * dp).roundToInt()
}
