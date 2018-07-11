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

    // Event listener class
    class PlayerStateChangedListener : Funcies2<IAmvVideoPlayer, IAmvVideoPlayer.PlayerState, Unit>() {
        interface IHandler {    // for Java
            fun playerStateChanged(vp:IAmvVideoPlayer, state:IAmvVideoPlayer.PlayerState)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(name, listener::playerStateChanged)
        fun remove(listener:IHandler) = this.remove(listener::playerStateChanged)
    }

    class SourceChangedListener : Funcies2<IAmvVideoPlayer, File, Unit>() {
        interface IHandler {    // for Java
            fun sourceChanged(vp:IAmvVideoPlayer, source:File)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(name, listener::sourceChanged)
        fun remove(listener:IHandler) = this.remove(listener::sourceChanged)
    }

    class SeekCompletedListener : Funcies2<IAmvVideoPlayer, Int, Unit>() {
        interface IHandler {    // for Java
            fun seekCompleted(vp:IAmvVideoPlayer, position:Int)
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

    // Event Listener
    val playerStateChangedListener: PlayerStateChangedListener
    val sourceChangedListener : SourceChangedListener
    val seekCompletedListener:SeekCompletedListener
    val sizeChangedListener: SizeChangedListener

    val playerState: PlayerState

    val naturalDuration : Int

    val seekPosition : Int

    // Operations

    fun setLayoutHint(mode: FitMode, width:Float, height:Float)

    fun reset(state: IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None)

    fun setSource(source: File, autoPlay:Boolean=false)

    fun play()

//    fun stop()

    fun pause()

    fun seekTo(pos:Int)

}