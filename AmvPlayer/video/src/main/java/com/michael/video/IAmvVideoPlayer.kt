package com.michael.video

import com.michael.utils.Funcies2
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

    class PlayerStateChangedListener : Funcies2<AmvVideoPlayer, IAmvVideoPlayer.PlayerState, Unit>() {
        interface IHandler {
            fun playerStateChanged(vp:AmvVideoPlayer, state:IAmvVideoPlayer.PlayerState)
        }
        fun add(listener:IHandler) = this.add(listener::playerStateChanged)
        fun remove(listener:IHandler) = this.remove(listener::playerStateChanged)
    }
    val playerStateChangedListener:PlayerStateChangedListener

    // Operations

    fun setLayoutHint(mode: IAmvVideoPlayer.LayoutMode, width:Float, height:Float)


    fun reset(state: IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None)

    fun setSource(source: File)

    fun play()

//    fun stop()

    fun pause()

    fun seekTo(pos:Int)
}