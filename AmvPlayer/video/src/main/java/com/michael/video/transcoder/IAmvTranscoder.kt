package com.mihcael.video.transcoder

import com.michael.video.v2.util.AmvClipping
import com.michael.video.v2.util.AmvFile
import com.michael.video.transcoder.AmvResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface IAmvTranscoder {
    /**
     * プログレス通知 (%)
     * 外部（=プログレスを監視したい人）からセットして使うタイプ
     */
    var progress: MutableStateFlow<Int>?

    /**
     * 残り時間(ms) -1: not available
     */
    val remainingTime: Long

    suspend fun transcode(distFile:AmvFile, clipping: AmvClipping= AmvClipping.empty): AmvResult

    fun cancel()

    fun close()
}