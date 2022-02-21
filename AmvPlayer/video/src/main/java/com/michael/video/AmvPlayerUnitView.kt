/**
 * 基本動画プレーヤー画面
 * （AmvExoVideoPlayer + AmvVideoController)
 *
 * @author M.TOYOTA 2018.07.20 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class AmvPlayerUnitView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    companion object {
        const val LISTENER_NAME = "EVP"
    }

    inner class Bindings {
        val player: AmvExoVideoPlayer by lazy {
            findViewById(R.id.evp_videoPlayer)
        }
        val controller: AmvVideoController by lazy {
            findViewById(R.id.evp_videoController)
        }
        val altLayer: LinearLayout by lazy {
            findViewById(R.id.evp_altLayer)
        }
        val message: TextView by lazy {
            findViewById(R.id.evp_message)
        }
        val progress: ProgressBar by lazy {
            findViewById(R.id.evp_progress)
        }
        fun init() {
            controller.setVideoPlayer(player)
            player.sizeChangedListener.add(LISTENER_NAME) {_,width,height ->
                bindings.altLayer.setLayoutWidth(width)
                bindings.altLayer.setLayoutHeight(height)
            }
        }
    }
    private val bindings =  Bindings()
    private var preparing:Boolean = false
        set(v) {
            if(v != field) {
                field = v
                if(v) {
                    bindings.progress.isActivated = true
                    bindings.message.visibility = View.GONE
                    bindings.progress.visibility = View.VISIBLE

                    bindings.altLayer.visibility = View.VISIBLE
                    bindings.player.visibility = View.INVISIBLE
                    bindings.controller.visibility = View.INVISIBLE
                } else {
                    bindings.progress.isActivated = false
                    bindings.message.visibility = View.GONE
                    bindings.progress.visibility = View.GONE

                    bindings.altLayer.visibility = View.GONE
                    bindings.player.visibility = View.VISIBLE
                    bindings.controller.visibility = View.VISIBLE
                }
            }
        }


    init {
        LayoutInflater.from(context).inflate(R.layout.player_unit_view, this)
        bindings.init()
        preparing = true
        isSaveFromParentEnabled = false
    }

    val videoPlayer : IAmvVideoPlayer
        get() = bindings.player

    val videoController : IAmvMarkerEditableController
        get() = bindings.controller

    fun setPlayerSize(width:Float, height:Float) {
        bindings.player.setLayoutHint(FitMode.Fit, width, height)

    }

    fun pause() {
        bindings.player.pause()
    }

    var isMuted: Boolean
        get() = bindings.player.isMuted
        set(v) { bindings.player.isMuted = v }

    val playerState: IAmvVideoPlayer.PlayerState
        get() = bindings.player.playerState

    @Suppress("unused")
    private fun setClipAndSource(clip:IAmvVideoPlayer.Clipping?, source: IAmvSource, autoPlay:Boolean, playFrom:Long) {
        preparing = false
        bindings.player.setClip(clip)
        bindings.player.setSource(source, autoPlay,playFrom)
    }

    fun setSource(source:IAmvSource, autoPlay:Boolean, playFrom:Long, showFrames:Boolean) {
        preparing = false
        bindings.controller.showingFrames = showFrames
        bindings.player.setClip(null)
        bindings.player.setSource(source, autoPlay,playFrom)
    }
//    fun setSource(source: File, autoPlay:Boolean, playFrom:Long) {
//        preparing = false
//        bindings.player.setClip(null)
//        bindings.player.setSource(source, autoPlay,playFrom)
//    }

    @Suppress("unused")
    fun setError(message:String) {
        preparing = true
        bindings.message.text = message
        bindings.message.visibility = View.VISIBLE
        bindings.progress.isActivated = false
        bindings.progress.visibility = View.GONE
    }

    fun startAt(pos: Long) {
        bindings.controller.startAt(pos)
    }

    fun stopAt(pos: Long) {
        bindings.controller.stopAt(pos)
    }

    fun seekTo(pos:Long) {
        bindings.player.seekTo(pos)
    }

    val frameVisibilityChanged
        get() = bindings.controller.frameVisibilityChanged

}
