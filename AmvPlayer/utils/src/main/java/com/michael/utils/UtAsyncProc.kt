package com.metamoji.lib.utils

import android.os.AsyncTask
import android.os.Handler
import java.security.InvalidParameterException
import java.util.concurrent.Executor

@Suppress("unused")
class UtAsyncProc<T>(val handler: Handler? = null, val executor: Executor? = null) {

    private var action: (() -> T)? = null
    private var completed: ((T) -> Unit)? = null
    private var failed: ((Throwable) -> Unit)? = null
    private var lock =  java.lang.Object()
    private var thread: Thread? = null
    private var cancelling: Boolean = false

    fun action(action: () -> T): UtAsyncProc<T> {
        this.action = action
        return this
    }

    fun completed(completed: (T) -> Unit): UtAsyncProc<T> {
        this.completed = completed
        return this
    }

    fun failed(failed: (Throwable) -> Unit): UtAsyncProc<T> {
        this.failed = failed
        return this
    }

    fun execute() : UtAsyncProc<T> {
        val executor = this.executor ?: AsyncTask.THREAD_POOL_EXECUTOR

        executor.execute {
            val action = this.action
            val handler = this.handler
            try {
                synchronized(lock) {
                    if(cancelling) {
                        throw InterruptedException("not started.")
                    }
                    thread = Thread.currentThread()
                }
                if (null == action) {
                    throw InvalidParameterException("no action")
                }
                val r = action()
                val completed = this.completed
                if (null != completed) {
                    if (null != handler) {
                        handler.post {
                            completed(r)
                        }
                    } else {
                        completed(r)
                    }
                }
            } catch (e: Throwable) {
                val failed = this.failed
                if (null != failed) {
                    if (null != handler) {
                        handler.post { failed(e) }
                    } else {
                        failed(e)
                    }
                }
            } finally {
                this.completed = null
                this.action = null
                this.failed = null
                this.thread = null
            }
        }
        return this
    }

    fun cancel() {
        synchronized(lock) {
            this.cancelling = true
            thread?.interrupt()
        }
    }
}