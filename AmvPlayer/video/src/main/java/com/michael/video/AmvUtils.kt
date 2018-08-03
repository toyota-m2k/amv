package com.michael.video

import android.content.Context
import android.util.Size
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import com.michael.utils.UtLogger
import kotlin.math.roundToInt
import android.content.ContextWrapper
import android.app.Activity



interface ImSize {
    val height:Float
    val width:Float
    val asSizeF:SizeF
    val asSize: Size
    val isEmpty:Boolean
}

/**
 * MutableなSize型
 */
@Suppress("unused")
data class MuSize(override var height: Float, override var width: Float) : ImSize {

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

    override val asSizeF:SizeF
        get() = SizeF(width,height)

    override val asSize: Size
        get() = Size(width.toInt(), height.toInt())

    override val isEmpty:Boolean
        get() = width==0f && height==0f
}



fun View.setLayoutWidth(width:Int) {
    val params = layoutParams
    if(null!=params) {
        params.width = width
        layoutParams = params
    }
}

fun View.getLayoutWidth() : Int {
    return if(layoutParams.width>=0) {
        layoutParams.width
    } else {
        width
    }
}

fun View.setLayoutHeight(height:Int) {
    val params = layoutParams
    if(null!=params) {
        params.height = height
        layoutParams = params
    }
}

@Suppress("unused")
fun View.getLayoutHeight() : Int {
    return if(layoutParams.height>=0) {
        layoutParams.height
    } else {
        width
    }
}

fun View.setLayoutSize(width:Int, height:Int) {
    val params = layoutParams
    if(null!=params) {
        params.width = width
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

fun View.getActivity(): Activity? {
    var ctx = this.context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) {
            return ctx
        }
        ctx = ctx.baseContext
    }
    return null
}

@Suppress("unused")
fun Context.dp2px(dp:Float) : Float {
    return resources.displayMetrics.density * dp
}

fun Context.dp2px(dp:Int) : Int {
    return (resources.displayMetrics.density * dp).roundToInt()
}

class AmvTimeSpan(private val ms : Long) {
    val milliseconds: Long
        get() = ms % 1000

    val seconds: Long
        get() = (ms / 1000) % 60

    val minutes: Long
        get() = (ms / 1000 / 60)

    val hours: Long
        get() = (ms / 1000 / 60 / 60)

    fun formatH() : String {
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    fun formatM() : String {
        return String.format("%02d:%02d", minutes, seconds)
    }
    fun formatS() : String {
        return String.format("%02d.%02d", seconds, milliseconds/10)
    }
}

fun <T> ignoreErrorCall(def:T, f:()->T): T {
    return try {
        f()
    } catch(e:Exception) {
        UtLogger.debug("SafeCall: ${e.message}")
        def
    }
}