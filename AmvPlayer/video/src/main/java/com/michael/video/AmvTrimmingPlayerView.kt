package com.michael.video

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import java.io.File

class AmvTrimmingPlayerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private inner class Controls {
        val player: AmvExoVideoPlayer by lazy {
            findViewById<AmvExoVideoPlayer>(R.id.videoPlayer)
        }
        val controller: AmvTrimmingController by lazy {
            findViewById<AmvTrimmingController>(R.id.trimmingController)
        }
        fun initialize() {
            controller.setVideoPlayer(player)
        }
    }
    private val mControls =  Controls()


    init {
        LayoutInflater.from(context).inflate(R.layout.player_unit_view, this)
        mControls.initialize()
    }

    val videoPlayer : IAmvVideoPlayer
        get() = mControls.player

    val videoController : IAmvVideoController
        get() = mControls.controller

    fun setSource(source: File) {
        mControls.player.setSource(source, false, 0)
    }

    val range:TrimmingRange
        get() = mControls.controller.trimmingRange
}