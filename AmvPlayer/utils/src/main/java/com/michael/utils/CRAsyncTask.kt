/**
 * Coroutine版 AsyncTask クラス
 *
 * Android 11 で deprecatedになった AsyncTaskクラスの代用品。
 * 本家 AsyncTaskと違って、Executorの指定はできない。特に、SERIAL_EXECUTORの動作は実装していない。
 * これまでSERIALで動作させる必要に迫られたことがないので。。。
 * また、本家AsyncTaskで、cancel(true)すると、スレッドがkillされて即座に処理が止まるが、こちらは、CoroutineScopeに対して、cancel()を呼ぶので、
 * あらたなCoroutineメソッドの呼び出しがあるまでは、勝手に終了しない。
 * doInBackground()の実装で、適宜 cancelled (javaなら isCancelled()）をチェックしてループを抜けるように頑張れ。
 */

@file:Suppress("unused")

package com.michael.utils

import io.github.toyota32k.utils.UtLogger
import kotlinx.coroutines.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

abstract class CRAsyncTask<Params, Progress, Result> {
    private var mStatus = AtomicReference(Status.PENDING)
    var status: Status
        get() = mStatus.get()
        private set(v) = mStatus.set(v)

    private val mCancelled = AtomicBoolean(false)
    var cancelled: Boolean
        @JvmName("isCancelled")
        get() = mCancelled.get()
        private set(v) = mCancelled.set(v)

    private val mScope = AtomicReference<CoroutineScope?>(null)
    private var scope: CoroutineScope?
        get() = mScope.get()
        set(v) = mScope.set(v)

    private val mainScope by lazy {
        CoroutineScope(Dispatchers.Main)
    }

    protected abstract fun doInBackground(vararg params: Params): Result

    protected open fun onPreExecute() {}

    protected open fun onPostExecute(result: Result) {}

    protected open fun onProgressUpdate(vararg values: Progress) {}

    protected open fun onCancelled() {}

    fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        cancelled = true
        if(mayInterruptIfRunning) {
            scope?.cancel()
        }
        return true
    }

    fun execute(vararg params: Params): CRAsyncTask<Params, Progress, Result> {
        when (status) {
            Status.RUNNING -> throw IllegalStateException("Cannot execute task:"
                    + " the task is already running.")
            Status.FINISHED -> throw IllegalStateException("Cannot execute task:"
                    + " the task has already been executed "
                    + "(a task can be executed only once)")
            Status.PENDING -> {}
        }

        status = Status.RUNNING

        onPreExecute()

        scope = CoroutineScope(Dispatchers.IO).apply {
            launch {
                try {
                    val result = doInBackground(*params)
                    withContext(Dispatchers.Main) {
                        try {
                            onPostExecute(result)
                        } catch (e: Throwable) {
                            UtLogger.stackTrace(e)
                        }
                    }
                } catch (e: Throwable) {
                    when (e) {
                        is InterruptedException, is CancellationException -> {}
                        else -> UtLogger.stackTrace(e)
                    }
                    // cancel(true)されたとき、scope がキャンセルされるので、withContext()が実行されない。
                    // 別のスコープで実行する必要がある。
                    // withContext(Dispatchers.Main) {
                    mainScope.launch {
                        onCancelled()
                    }
                } finally {
                    status = Status.FINISHED
                }
            }
        }
        return this
    }

    protected fun publishProgress(vararg values: Progress) {
        mainScope.launch {
            onProgressUpdate(*values)
        }
    }

    enum class Status {
        PENDING,
        RUNNING,
        FINISHED
    }
}