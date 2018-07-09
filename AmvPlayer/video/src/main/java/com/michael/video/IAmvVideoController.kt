package com.michael.video

interface IAmvVideoController {

    fun setVideoPlayer(player:IAmvVideoPlayer)

    var isReadOnly : Boolean
}