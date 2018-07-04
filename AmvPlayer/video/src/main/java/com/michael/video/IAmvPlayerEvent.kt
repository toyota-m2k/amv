package com.michael.video

interface IAmvPlayerEvent {
    fun playerStateChanged(vp:AmvVideoPlayer, state:AmvVideoPlayer.PlayerState)
}