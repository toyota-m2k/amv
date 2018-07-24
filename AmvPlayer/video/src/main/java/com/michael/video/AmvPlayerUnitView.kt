package com.michael.video

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.google.android.exoplayer2.ui.PlayerView
import java.util.zip.Inflater

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
            controller.setVideoPlayer(player);
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
}