package com.michael.video.transcoder

import com.michael.video.v2.common.AmvError

data class AmvResult(val succeeded:Boolean, val error: AmvError?, val cancelled:Boolean) {
    companion object {
        val succeeded: AmvResult get() = AmvResult(true,null,false)
        val cancelled: AmvResult get() = AmvResult(false, null, true)
        fun failed(error: AmvError): AmvResult = AmvResult(false, error,false)
        fun failed(error: Throwable): AmvResult = AmvResult(false, AmvError(error),false)
        fun failed(message:String): AmvResult = AmvResult(false, AmvError(message), false)
    }
    val hasError:Boolean get() = error!=null
}
