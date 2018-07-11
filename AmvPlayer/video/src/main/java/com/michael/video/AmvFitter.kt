package com.michael.video

import com.michael.utils.UtLogger

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
        UtLogger.error(e.toString())
        result.set(0f,0f)
    }
}

class AmvFitter(private var fitMode: FitMode = FitMode.Fit, private var layoutSize: MuSize = MuSize(100f, 100f)) {

    fun setHint(fitMode:FitMode, width:Float, height:Float) {
        this.fitMode = fitMode
        layoutSize.width = width
        layoutSize.height = height
    }

    fun fit(original:MuSize, result:MuSize) {
        fitSizeTo(original, layoutSize, fitMode, result)
    }
}