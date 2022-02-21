/**
 * Backward Compatible AsyncTask Class
 *
 * Android 11 で、AsyncTaskクラスがdeprecatedになったので、それを代用するクラスを作ってみた。
 * Extractorsのthread poolを使って、ちょちょいと作っただけなので、厳密にはAsyncTaskと同じ動作ではないかもしれない。
 * 尚、get(), get(timeout: Long, unit: TimeUnit?) は、使う機会はないと断言できる（とおもう）ので実装していない。
 * また、SERIAL_EXECUTOR は、本家AsyncTaskの実装では、自前のキューを実装していたようなのだが、こちらの実装では、
 * Executors.newFixedThreadPool(1) とすれば、１つのタスクが実行中なら次は待たされるはずだから、それでいいんじゃね的な実装になっている。
 * ちなみに、本当にそう動くかどうかは知らないし、今まで、SERIALを使ったこともない。
 * SERIALが不要なら、Coroutine を使って実装した CRAsyncTaskの方が効率がよいんじゃないだろうか。しらんけど。
 */
@file:Suppress("unused")

package com.michael.utils

import android.os.Handler
import android.os.Looper
import io.github.toyota32k.utils.UtLogger
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

abstract class BCAsyncTask<Params, Progress, Result> {

    private var mStatus = AtomicReference(Status.PENDING)
    var status: Status
        get() = mStatus.get()
        private set(v) = mStatus.set(v)

    private val mCancelled = AtomicBoolean(false)
    var cancelled: Boolean
        @JvmName("isCancelled")
        get() = mCancelled.get()
        private set(v) = mCancelled.set(v)

    private val mThread = AtomicReference<Thread?>(null)
    private var thread : Thread?
        get() = mThread.get()
        set(v) = mThread.set(v)

    // Unitテストでは
    private val handler = Handler(Looper.getMainLooper())

    protected abstract fun doInBackground(vararg params: Params): Result

    protected open fun onPreExecute() {}

    protected open fun onPostExecute(result: Result) {}

    protected open fun onProgressUpdate(vararg values: Progress) {}

    protected open fun onCancelled() {}

    fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        cancelled = true
        if(mayInterruptIfRunning) {
            thread?.interrupt()
        }
        return true
    }

    // get() のようなブロッキングメソッドはたぶん不要だろう。

//    @Throws(ExecutionException::class, InterruptedException::class)
//    fun get(): Result {
//        throw RuntimeException("Stub!")
//    }
//
//    @Deprecated("")
//    @Throws(ExecutionException::class, InterruptedException::class, TimeoutException::class)
//    operator fun get(timeout: Long, unit: TimeUnit?): Result {
//        throw RuntimeException("Stub!")
//    }

    fun execute(vararg params: Params): BCAsyncTask<Params, Progress, Result> {
        return executeOnExecutor(THREAD_POOL_EXECUTOR, *params)
    }

    private fun call(fn:()->Unit) {
        handler.post(fn)
//        if(handler!=null) {
//            handler.post(fn)
//        } else {
//            fn()
//        }
    }

    fun executeOnExecutor(exec: Executor, vararg params: Params): BCAsyncTask<Params, Progress, Result> {
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

        exec.execute {
            thread = Thread.currentThread()
            try {
                val result = doInBackground(*params)
                call {
                    if (!cancelled) {
                        onPostExecute(result)
                    } else {
                        onCancelled()
                    }
                }
            } catch (e: Throwable) {
                cancelled = true
                when (e) {
                    is InterruptedException, is CancellationException -> {}
                    else -> UtLogger.stackTrace(e)
                }
                call {
                    onCancelled()
                }
            } finally {
                status = Status.FINISHED
            }
        }
        return this
    }

    protected fun publishProgress(vararg values: Progress) {
        call {
            onProgressUpdate(*values)
        }
    }

    enum class Status {
        PENDING,
        RUNNING,
        FINISHED
    }

    companion object {
        private val threadIndex = AtomicInteger(0)

        val SERIAL_EXECUTOR: Executor by lazy {
            Executors.newFixedThreadPool(1) { Thread("BCAsyncTask-S") }
        }

        val THREAD_POOL_EXECUTOR: Executor by lazy {
            Executors.newCachedThreadPool {Thread("BCAsyncTask #${threadIndex.getAndIncrement()}")}
        }
//
//        fun execute(runnable: Runnable?) {
//            throw RuntimeException("Stub!")
//        }
    }
}
