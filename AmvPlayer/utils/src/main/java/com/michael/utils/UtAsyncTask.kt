/**
 * AsyncTaskを
 *
 * @author M.TOYOTA 2018.07.06 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */

package com.michael.utils

import io.github.toyota32k.utils.UtLogger
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class UtAsyncTaskA(val executorType:ExecutorType = ExecutorType.LOCAL_PARALLEL, val autoDispose:Boolean=true) : BCAsyncTask<Unit?,Any?,Unit?>() {

    enum class ExecutorType {
        SEQUENTIAL,
        GLOBAL_PARALLEL,
        LOCAL_PARALLEL,
    }
    /**
     * バックグラウンドタスクの実体（こいつをオーバーライドする）
     * doInBackground()の中の人
     *
     * isCancelledのチェックや、publishProgress()の呼び出しは、task()の実装者が頑張る
     */
    protected abstract fun task()


    protected open fun onFinished(result:Boolean) {
        onFinishedListener.invoke(this, result)
        if(autoDispose) {
            dispose()
        }
    }

    // イベントハンドラ

    /**
     * 処理が完了したときのイベント
     */
    val onFinishedListener = Funcies2<UtAsyncTaskA, Boolean, Unit>()

    /**
     * プログレス表示用
     */
    val onProgressListener = Funcies2<UtAsyncTaskA, Int, Unit>()

    /**
     * Java用リスナー型定義
     */
    interface IHandler {
        fun onFinished(caller:UtAsyncTaskA, result:Boolean)
        fun onProgress(caller:UtAsyncTaskA, percent:Int)
    }

    /**
     * Java版リスナー登録
     * ２つのリスナーが、まとめて登録される。Kotlinなら個々に登録できるけど。
     */
    fun setListener(listener:IHandler) {
        onFinishedListener.add("single", listener::onFinished)
        onProgressListener.add("single", listener::onProgress)
    }

    /**
     * task()中に発生した例外（なければnull）
     */
    var exception : Throwable? = null

    /**
     * エラーは発生しているか？
     */
    val hasError
        get() = exception != null

    /**
     * マイルドなキャンセル
     */
    open fun cancel() {
        cancel(false)
    }

    /**
     * 破棄・・・UIスレッドから呼び出すこと
     */
    open fun dispose() {
        onFinishedListener.clear()
        onProgressListener.clear()
        cancel(true)        // ハードなキャンセル
    }

    /**
     * 進捗通知（UI Threadから呼び出される）
     */
    fun updateProgress(percent:Int) {
        publishProgress(percent)
    }

    // UI Thread から実行するためのヘルパメソッド
    // (publishProgressの仕掛けを悪用）

    /**
     * 引数無し
     */
    fun runOnUiThread(f:IFuncy0<Unit>) {
        publishProgress(null, f)
    }
    /**
     * 引数１個
     */
    fun <T1> runOnUiThread(f:IFuncy1<T1,Unit>, a1:T1) {
        publishProgress(null, f, a1)
    }
    /**
     * 引数２個
     */
    fun <T1,T2> runOnUiThread(f:IFuncy2<T1,T2,Unit>, a1:T1, a2:T2) {
        publishProgress(null, f, a1, a2)
    }
    /**
     * 引数３個
     */
    fun <T1,T2,T3> runOnUiThread(f:IFuncy3<T1,T2,T3,Unit>, a1:T1, a2:T2, a3:T3) {
        publishProgress(null, f, a1, a2, a3)
    }

    // AsyncTaskのi/f実装

    override fun doInBackground(vararg params: Unit?) {
        try {
            exception = null
            task()
        } catch(e:Throwable) {
            UtLogger.error(e.toString())
            exception = e
            cancel(true)
        }
    }

    override fun onCancelled() {
        super.onCancelled()
        onFinished(false)
    }

    override fun onPostExecute(result: Unit?) {
        super.onPostExecute(result)
        onFinished(true)
    }

    override fun onProgressUpdate(vararg values: Any?) {
        super.onProgressUpdate(*values)
        val v = values[0]
        if(null!=v) {
            if(v is Int) {
                onProgressListener.invoke(this, v)
            }
        } else {
            val f = values[1]
            if(null!=f && f is IFuncy<*>) {
                val args= values.copyOfRange(2, values.size)
                f.invoke_(*args)
            }
        }
    }

    fun execute() {
        when(executorType) {
            ExecutorType.SEQUENTIAL->super.execute()
            ExecutorType.GLOBAL_PARALLEL->super.executeOnExecutor(THREAD_POOL_EXECUTOR)
            else-> super.executeOnExecutor(LocalParallelExecutor)
        }
//        if(sequential) {
//            super.execute()
//        } else {
//            super.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
//        }
    }

    companion object {
        private val CPU_COUNT by lazy {
            Runtime.getRuntime().availableProcessors()
        }
        private val CORE_POOL_SIZE = max(2, min(CPU_COUNT - 1, 4))
        private val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
        private const val KEEP_ALIVE_SECONDS = 30L
        private val sPoolWorkQueue by lazy {
            LinkedBlockingQueue<Runnable>(128)
        }

        private val sThreadFactory = object : ThreadFactory {
            private val mCount = AtomicInteger(1)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "UtAsyncTask #" + mCount.getAndIncrement())
            }
        }

        val LocalParallelExecutor: Executor by lazy {
            ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                    sPoolWorkQueue, sThreadFactory).apply {
                allowCoreThreadTimeOut(true)
            }
        }
    }
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
abstract class UtAsyncTask(val autoDispose:Boolean=true) : CRAsyncTask<Unit?,Any?,Unit?>() {
    /**
     * バックグラウンドタスクの実体（こいつをオーバーライドする）
     * doInBackground()の中の人
     *
     * isCancelledのチェックや、publishProgress()の呼び出しは、task()の実装者が頑張る
     */
    protected abstract fun task()

    protected open fun onFinished(result:Boolean) {
        onFinishedListener.invoke(this, result)
        if(autoDispose) {
            dispose()
        }
    }

    // イベントハンドラ

    /**
     * 処理が完了したときのイベント
     */
    val onFinishedListener = Funcies2<UtAsyncTask, Boolean, Unit>()

    /**
     * プログレス表示用
     */
    val onProgressListener = Funcies2<UtAsyncTask, Int, Unit>()

    /**
     * Java用リスナー型定義
     */
    interface IHandler {
        fun onFinished(caller:UtAsyncTask, result:Boolean)
        fun onProgress(caller:UtAsyncTask, percent:Int)
    }

    /**
     * Java版リスナー登録
     * ２つのリスナーが、まとめて登録される。Kotlinなら個々に登録できるけど。
     */
    fun setListener(listener:IHandler) {
        onFinishedListener.add("single", listener::onFinished)
        onProgressListener.add("single", listener::onProgress)
    }

    /**
     * task()中に発生した例外（なければnull）
     */
    var exception : Throwable? = null

    /**
     * エラーは発生しているか？
     */
    val hasError
        get() = exception != null

    /**
     * マイルドなキャンセル
     */
    open fun cancel() {
        cancel(false)
    }

    /**
     * 破棄・・・UIスレッドから呼び出すこと
     */
    open fun dispose() {
        onFinishedListener.clear()
        onProgressListener.clear()
        cancel(true)        // ハードなキャンセル
    }

    /**
     * 進捗通知（UI Threadから呼び出される）
     */
    fun updateProgress(percent:Int) {
        publishProgress(percent)
    }

    // UI Thread から実行するためのヘルパメソッド
    // (publishProgressの仕掛けを悪用）

    /**
     * 引数無し
     */
    fun runOnUiThread(f:IFuncy0<Unit>) {
        publishProgress(null, f)
    }
    /**
     * 引数１個
     */
    fun <T1> runOnUiThread(f:IFuncy1<T1,Unit>, a1:T1) {
        publishProgress(null, f, a1)
    }
    /**
     * 引数２個
     */
    fun <T1,T2> runOnUiThread(f:IFuncy2<T1,T2,Unit>, a1:T1, a2:T2) {
        publishProgress(null, f, a1, a2)
    }
    /**
     * 引数３個
     */
    fun <T1,T2,T3> runOnUiThread(f:IFuncy3<T1,T2,T3,Unit>, a1:T1, a2:T2, a3:T3) {
        publishProgress(null, f, a1, a2, a3)
    }

    // AsyncTaskのi/f実装

    override fun doInBackground(vararg params: Unit?) {
        try {
            exception = null
            task()
        } catch(e:Throwable) {
            UtLogger.error(e.toString())
            exception = e
            cancel(true)
        }
    }

    override fun onCancelled() {
        super.onCancelled()
        onFinished(false)
    }

    override fun onPostExecute(result: Unit?) {
        super.onPostExecute(result)
        onFinished(true)
    }

    override fun onProgressUpdate(vararg values: Any?) {
        super.onProgressUpdate(*values)
        val v = values[0]
        if(null!=v) {
            if(v is Int) {
                onProgressListener.invoke(this, v)
            }
        } else {
            val f = values[1]
            if(null!=f && f is IFuncy<*>) {
                val args= values.copyOfRange(2, values.size)
                f.invoke_(*args)
            }
        }
    }
}