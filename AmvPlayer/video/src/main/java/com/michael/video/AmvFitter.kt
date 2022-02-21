/**
 * 矩形サイズをルールに従って配置するためのサイズ決定ロジックの実装
 *
 * @author M.TOYOTA 2018.07.11 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */

package com.michael.video

/**
 * 矩形領域のリサイズ方法
 */
enum class FitMode {
    Width,       // 指定された幅になるように高さを調整
    Height,      // 指定された高さになるよう幅を調整
    Inside,      // 指定された矩形に収まるよう幅、または、高さを調整
    Fit          // 指定された矩形にリサイズする
}

/**
 * ビデオや画像のサイズ(original)を、指定されたmode:FitModeに従って、配置先のサイズ(layout)に合わせるように変換して resultに返す。
 *
 * @param layout    レイアウト先の指定サイズ
 * @param result    結果を返すバッファ
 * @param original  元のサイズ（ビデオ/画像のサイズ）
 */
fun fitSizeTo(original:MuSize, layout:MuSize, mode:FitMode, result:MuSize) {
    try {
        when (mode) {
            FitMode.Fit -> result.copyFrom(layout)
            FitMode.Width -> result.set(layout.width, original.height * layout.width / original.width)
            FitMode.Height -> result.set(original.width * layout.height / original.height, layout.height)
            FitMode.Inside -> {
                val rw = layout.width / original.width
                val rh = layout.height / original.height
                if (rw < rh) {
                    result.set(layout.width, original.height * rw)
                } else {
                    result.set(original.width * rh, layout.height)
                }
            }
        }
    } catch(e:Exception) {
        AmvSettings.logger.stackTrace(e)
        result.set(0f,0f)
    }
}

interface IAmvLayoutHint {
    val fitMode: FitMode
    val layoutWidth: Float
    val layoutHeight: Float
}

open class AmvFitter(override var fitMode: FitMode = FitMode.Inside, private var layoutSize: MuSize = MuSize(1000f, 1000f)) : IAmvLayoutHint {
    override val layoutWidth: Float
        get() = layoutSize.width
    override val layoutHeight: Float
        get() = layoutSize.height


    fun setHint(fitMode:FitMode, width:Float, height:Float) {
        this.fitMode = fitMode
        layoutSize.width = width
        layoutSize.height = height
    }

    fun fit(original:MuSize, result:MuSize) {
        fitSizeTo(original, layoutSize, fitMode, result)
    }

    fun fit(w:Float, h:Float):ImSize {
        val result = MuSize()
        fit(MuSize(w,h), result)
        return result
    }
}