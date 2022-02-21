package com.mihcael.video.transcoder

import com.michael.utils.FuncyListener2
import com.michael.video.AmvError
import java.io.File

interface IAmvTranscoder {

    /**
     * 処理が終了したときのイベント
     */
    val completionListener : FuncyListener2<IAmvTranscoder, Boolean, Unit>
    /**
     * プログレス通知
     */
    val progressListener :FuncyListener2<IAmvTranscoder, Float, Unit>

    val error: AmvError

    fun transcode(distFile: File)
    fun truncate(distFile:File, start:Long, end:Long)
    fun cancel()
    fun dispose()

}