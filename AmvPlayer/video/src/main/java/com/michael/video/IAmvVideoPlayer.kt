package com.michael.video

import com.michael.utils.Funcies2
import com.michael.utils.IFuncy
import com.michael.utils.IFuncy2
import java.io.File

interface IAmvVideoPlayer {

    enum class LayoutMode {
        Width,       // 指定された幅になるように高さを調整
        Height,      // 指定された高さになるよう幅を調整
        Inside,      // 指定された矩形に収まるよう幅、または、高さを調整
        Fit          // 指定された矩形にリサイズする
    }

    enum class PlayerState {
        None,       // 初期状態
        Loading,
        Error,
        Playing,
        Paused
    }

    // Event listeners

    class PlayerStateChangedListener : Funcies2<IAmvVideoPlayer, IAmvVideoPlayer.PlayerState, Unit>() {
        interface IHandler {    // for Java
            fun playerStateChanged(vp:IAmvVideoPlayer, state:IAmvVideoPlayer.PlayerState)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(null, listener::playerStateChanged)
        fun remove(listener:IHandler) = this.remove(listener::playerStateChanged)
    }
    val playerStateChangedListener:PlayerStateChangedListener

    class SeekCompletedListener : Funcies2<IAmvVideoPlayer, Int, Unit>() {
        interface IHandler {    // for Java
            fun seekCompleted(vp:IAmvVideoPlayer, position:Int)
        }
        @JvmOverloads
        fun add(listener:IHandler, name:String?=null) = super.add(null, listener::seekCompleted)
        fun remove(listener:IHandler) = this.remove(listener::seekCompleted)
    }
    val seekCompletedListener:SeekCompletedListener

    val playerState: PlayerState

    val naturalDuration : Int

    val seekPosition : Int

    // Operations

    fun setLayoutHint(mode: IAmvVideoPlayer.LayoutMode, width:Float, height:Float)

    fun reset(state: IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None)

    fun setSource(source: File, autoPlay:Boolean=false)

    fun play()

//    fun stop()

    fun pause()

    fun seekTo(pos:Int)

}