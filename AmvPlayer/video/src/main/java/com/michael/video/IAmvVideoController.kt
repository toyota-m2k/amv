/**
 * Video Controller i/f
 *
 * @author M.TOYOTA 2018.07.04 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */


package com.michael.video

interface IAmvVideoController {

    fun setVideoPlayer(player:IAmvVideoPlayer)

    var isReadOnly : Boolean
}

