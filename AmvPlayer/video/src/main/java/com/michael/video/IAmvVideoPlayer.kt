package com.michael.video

import com.michael.utils.Funcies2
import com.michael.utils.Funcies3
import java.io.File

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
                Math.min(Math.max(start, pos), end)
            } else {
                Math.max(start, pos)
            }
        }
    }

    // Event listener class
    class SourceChangedListener : Funcies2<IAmvVideoPlayer, File, Unit>() {
        interface IHandler {    // for Java
            fun sourceChanged(vp:IAmvVideoPlayer, source:File)
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

    class PlayerStateChangedListener : Funcies2<IAmvVideoPlayer, IAmvVideoPlayer.PlayerState, Unit>() {
        interface IHandler {    // for Java
            fun playerStateChanged(vp:IAmvVideoPlayer, state:IAmvVideoPlayer.PlayerState)
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

    val naturalDuration : Long

    val seekPosition : Long

    // Operations

    fun setLayoutHint(mode: FitMode, width:Float, height:Float)

    fun reset()

    fun setSource(source: File, autoPlay:Boolean=false, playFrom:Long=0)

    fun clip(clipping:Clipping?)

    val isClipping : Boolean

    fun play()

//    fun stop()

    fun pause()

    fun seekTo(pos:Long)

    fun setFastSeekMode(fast:Boolean)
}