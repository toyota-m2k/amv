/**
 * Video Player i/f
 *
 * @author M.TOYOTA 2018.07.04 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */

package com.michael.video

import android.util.Size
import com.michael.utils.Funcies2
import com.michael.utils.Funcies3
import kotlin.math.max
import kotlin.math.min

interface IAmvVideoPlayer {

    enum class PlayerState {
        None,       // 初期状態
        Loading,
        Error,
        Playing,
        Paused
    }

    data class Clipping (val start:Long, val end:Long=-1) {
        val isValid
            get() = end>start

        fun clipPos(pos:Long) : Long {
            return if(end>start) {
                min(max(start, pos), end)
            } else {
                max(start, pos)
            }
        }
    }

    // Event listener class
    class SourceChangedListener : Funcies2<IAmvVideoPlayer, IAmvSource, Unit>() {
        interface IHandler {    // for Java
            fun sourceChanged(vp:IAmvVideoPlayer, source:IAmvSource)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(name, listener::sourceChanged)
        fun remove(listener:IHandler) = this.remove(listener::sourceChanged)
    }

    class VideoPreparedListener : Funcies2<IAmvVideoPlayer, Long, Unit>() {
        interface IHandler {    // for Java
            fun videoPrepared(vp:IAmvVideoPlayer, duration:Long)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(name, listener::videoPrepared)
        fun remove(listener:IHandler) = this.remove(listener::videoPrepared)
    }

    class PlayerStateChangedListener : Funcies2<IAmvVideoPlayer, PlayerState, Unit>() {
        interface IHandler {    // for Java
            fun playerStateChanged(vp:IAmvVideoPlayer, state: PlayerState)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(name, listener::playerStateChanged)
        fun remove(listener:IHandler) = this.remove(listener::playerStateChanged)
    }

    class SeekCompletedListener : Funcies2<IAmvVideoPlayer, Long, Unit>() {
        interface IHandler {    // for Java
            fun seekCompleted(vp:IAmvVideoPlayer, position:Long)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(name, listener::seekCompleted)
        fun remove(listener:IHandler) = this.remove(listener::seekCompleted)
    }

    class SizeChangedListener : Funcies3<IAmvVideoPlayer, Int, Int, Unit>() {
        interface IHandler {    // for Java
            fun sizeChanged(vp:IAmvVideoPlayer, width:Int, height:Int)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(name, listener::sizeChanged)
        fun remove(listener:IHandler) = this.remove(listener::sizeChanged)
    }

    class ClipChangedListener : Funcies2<IAmvVideoPlayer, Clipping?, Unit>() {
        interface IHandler {    // for Java
            fun clipChanged(vp:IAmvVideoPlayer, clipping:Clipping?)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(name, listener::clipChanged)
        fun remove(listener:IHandler) = this.remove(listener::clipChanged)
    }

    // Event Listener
    val sourceChangedListener : SourceChangedListener
    val videoPreparedListener : VideoPreparedListener
    val playerStateChangedListener: PlayerStateChangedListener
    val seekCompletedListener:SeekCompletedListener
    val sizeChangedListener: SizeChangedListener
    val clipChangedListener: ClipChangedListener

    val playerState: PlayerState
    val isPlayingOrReservedToPlay : Boolean

    val naturalDuration : Long

    val seekPosition : Long

    var isMuted: Boolean

    // Operations

    fun setLayoutHint(mode: FitMode, width:Float, height:Float)

    fun getLayoutHint() : IAmvLayoutHint

    val videoSize: Size

    fun reset()

    fun setSource(source: IAmvSource, autoPlay:Boolean=false, playFrom:Long=0)

    val source:IAmvSource?

    fun setClip(clipping:Clipping?)

    val clip:Clipping?

    fun play()

//    fun stop()

    fun pause()

    fun seekTo(pos:Long)

    fun setFastSeekMode(fast:Boolean)
}