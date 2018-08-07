/**
 * m4m でフレーム描画を行うための最小限のSurfaceView実装
 *
 * @author M.TOYOTA 2018.07.26 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.View

class AmvWorkingSurfaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : SurfaceView(context, attrs, defStyleAttr) {

    private var widthMeasureSpec: Int = 0
    private var heightMeasureSpec: Int = 0
    private var mVideoWidth: Int = 0
    private var mVideoHeight: Int = 0

    fun setVideoSize(width: Int, height: Int) {
        mVideoWidth = width
        mVideoHeight = height
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        this.widthMeasureSpec = widthMeasureSpec
        this.heightMeasureSpec = heightMeasureSpec
        var width = View.getDefaultSize(mVideoWidth, widthMeasureSpec)
        var height = View.getDefaultSize(mVideoHeight, heightMeasureSpec)
        if (mVideoWidth > 0 && mVideoHeight > 0) {
            when {
                mVideoWidth * height > width * mVideoHeight -> height = width * mVideoHeight / mVideoWidth
                mVideoWidth * height < width * mVideoHeight -> width = height * mVideoWidth / mVideoHeight
                else -> {
                }
            }
        }
        setMeasuredDimension(width, height)
    }
}
