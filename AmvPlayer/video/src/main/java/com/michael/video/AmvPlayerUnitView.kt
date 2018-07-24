package com.michael.video

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import java.io.File

class AmvPlayerUnitView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    inner class Bindings {
        val player: AmvExoVideoPlayer by lazy {
            findViewById<AmvExoVideoPlayer>(R.id.videoPlayer)
        }
        val controller: AmvVideoController by lazy {
            findViewById<AmvVideoController>(R.id.videoController)
        }
        fun init() {
            controller.setVideoPlayer(player)
        }
    }
    val bindings =  Bindings()

    init {
        LayoutInflater.from(context).inflate(R.layout.player_unit_view, this)
        bindings.init()
    }

    val videoPlayer : IAmvVideoPlayer
        get() = bindings.player

    val videoController : IAmvVideoController
        get() = bindings.controller

    fun setSource(source: File, autoPlay:Boolean, playFrom:Long) {
        bindings.player.setSource(source, autoPlay,playFrom)
    }
}
