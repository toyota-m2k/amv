package com.metamoji.lib.utils

class UtSimpleAsyncTask<T>  : UtAsyncTask() {
    val onResult = FuncyListener1<T?, Unit>()
    val action = FuncyListener0<T?>()
    override fun task() {
        var r: T? = null
        try {
            r = action.invoke()
        } catch(e:Throwable) {
            UtLogger.stackTrace(e, "UtSimpleAsyncTask:error")
        }
        val funcy = onResult.funcy
        if(null!=funcy && !isCancelled) {
            runOnUiThread(funcy, r)
        }
    }

    override fun dispose() {
        super.dispose()
        onResult.reset()
        action.reset()
    }
}