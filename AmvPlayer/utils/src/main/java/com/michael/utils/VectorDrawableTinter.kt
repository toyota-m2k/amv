package com.michael.utils

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * VectorタイプのDrawableのtintを設定してFillColorを変えるヘルパークラス
 * xmlで変更するときは、
 *   Android:tint = "#ff0000"
 * でいけるけど、プログラムから変えようとすると、ちょっと細工が必要なので、
 * やり方を忘れないようライブラリにしておこう。
 *
 * 使い方
 * ■リソースIDを使って複数のターゲットを連続して変更するとき
 *  val tinter = VectorDrawableTinter(getContext())
 *  val r1 = tinter.tintDrawable(R.drawable.item1, R.color.red)
 *  val r2 = tinter.tintDrawable(R.drawable.item2, R.color.blue)
 *  ...
 *
 * ■すでに持っているDrawableインスタンスから色を変更したDrawableを得る
 *
 *  VectorDrawableTinter.tintDrawable(drawable, Color.rgb(r,g,b))
 *
 * @author M.TOYOTA 2018.07.06 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class VectorDrawableTinter(val context: Context) {

    var tintMode = PorterDuff.Mode.SRC_IN

    fun tintDrawable(@DrawableRes dr:Int, @ColorRes cr:Int) : Drawable? {
        val color = ContextCompat.getColor(context, cr)
        Color.BLACK
        return tintDrawableByRGB(dr, color)
    }

    fun tintDrawableByRGB(@DrawableRes dr:Int, @ColorInt color:Int) : Drawable? {
        val drawable = ContextCompat.getDrawable(context, dr)?.mutate() ?: return null
        return tintDrawable(drawable, color, tintMode)
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun tintDrawable(drawable:Drawable, @ColorInt color:Int, tintMode:PorterDuff.Mode=PorterDuff.Mode.SRC_IN) : Drawable {
            return DrawableCompat.wrap(drawable.mutate()).apply {
                setTint(color)
                setTintMode(tintMode)
            }
        }
    }
}