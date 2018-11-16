@file:Suppress("unused")

package com.michael.amvplayer.exp

import com.michael.utils.UtLogger
import java.lang.IllegalStateException

open class Promistic constructor(
    private val action: ((Any?, Promistic)->Unit)? = null,
    private val errorHandler: ((Any?)->Unit)? = null,
    private val anywayHandler: ((Any?)->Unit)? = null
    ) {

    private var _next: Promistic? = null
    private var _head: Promistic? = null
    private var _burnt: Boolean = false
    private var _tag: Int = Promistic.nextGlobalIndex()
    private var _label: String? = null

    private fun log(msg:String) {
        if(_label.isNullOrEmpty()) {
            UtLogger.debug("$_tag: $msg")
        } else {
            UtLogger.debug("$_tag ($_label) : $msg")
        }
    }

    val first:Promistic
        get() = _head?:this


    /**
     * Promiseチェーンの末尾のノードを取得
     */
    val last:Promistic
        get() {
            var p = this
            while(null!=p._next) {
                p = p._next!!
            }
            return p
        }


    open fun execute(chainedResult:Any?) {
        if(_burnt) {
            throw IllegalAccessException("($_tag): the task to be executed is already burning or has been burnt.")
        }
        log("execute called")

        _burnt = true
        if(null!=action) {
            try {
                action.invoke(chainedResult, this)
            } catch(e:Throwable) {
                setError(e)
            }
        } else { // action == null && anywayHandler == null
            if(null!=anywayHandler){
                try {
                    anywayHandler.invoke(chainedResult)
                } catch(e:Throwable) {
                    // anywayのエラーは無視
                }
            }
            _next?.execute(chainedResult)
            this.dispose()
        }
    }

    private fun abort(error:Any?) {
        if(_burnt) {
            throw IllegalAccessException("($_tag): the task to be executed is already burning or has been burnt.")
        }

        _burnt = true
        if(null!=errorHandler) {
            try {
                errorHandler.invoke(error)
            } catch(e:Throwable) {
                // errorHandlerのエラーは無視
            }
        }
        if(null!=anywayHandler){
            try {
                anywayHandler.invoke(error)
            } catch(e:Throwable) {
                // anywayのエラーは無視
            }
        }

        val next = _next
        if( null!=next ){ // action == null
            next.abort(error)
        }
        this.dispose()
    }

    private fun setError(error:Any?) {
        log("rejected")

        assert(errorHandler==null)
//        try {
//            errorHandler?.invoke(error)
//        } catch (e:Throwable) {
//            log("errorHandler error : $e")
//        }

        val next = _next
        if(null!=next) {
            next.abort(error)
        } else {
            log("rejected and reached the last node of the promistic chain - ${first._tag}")
        }
    }

    private fun setResult(chainedResult:Any?) {
        log("resolved")

        val next = _next
        if(null!=next) {
            next.execute(chainedResult)
        } else {
            log("resolved and reached the last node of the promistic chain - ${first._tag}")
        }
        dispose()
    }

    private fun dispose() {
        log("disposed")
    }




    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private fun validateBeforeAddingNode() {
        if(_burnt) {
            throw IllegalStateException("cannot add promistic nodes after the node being ignited.")
        }
    }

    private fun chainTask(parentTask:Promistic, subTask:Promistic) {
        subTask
        .then{chainedResult:Any?->
            parentTask.resolve(chainedResult)
            null
        }
        .failed { error:Any?->
            parentTask.reject(error)
        }
        .ignite()
    }

    private fun chainIgnoringTask(parentTask:Promistic, subTask:Promistic) {
        subTask
        .then{chainedResult:Any?->
            parentTask.resolve(chainedResult)
            null
        }
        .failed {
            parentTask.resolve(null)
        }
        .ignite()
    }

    private fun addNextNode(promistic:Promistic) :Promistic{
        validateBeforeAddingNode()
        this.last._next = promistic.first
        promistic._head = this.first
        return promistic.last
    }



    private fun addNextChain(nextAction:(Any?)->Promistic?) : Promistic {
        val node = Promistic(action = { chainedResult: Any?, promix:Promistic ->
            val child = nextAction(chainedResult)
            if(null!=child) {
                chainTask(promix, child)
            } else {
                promix.resolve(chainedResult)
            }
        })
        return addNextNode(node)
    }


    // nextActionが返すPromisticが reject されても resolved として扱うノードとして追加する
    private  fun addNextIgnoringChain(nextAction:(Any?)->Promistic?):Promistic {
        val node = Promistic(action = { chainedResult: Any?, promix:Promistic ->
            val child = nextAction(chainedResult)
            if(null!=child) {
                chainIgnoringTask(promix, child)
            } else {
                promix.resolve(chainedResult)
            }
        })
        return addNextNode(node)
    }

    private fun addErrorHandler(errorHandler:(Any?)->Unit):Promistic {
        val node = Promistic(errorHandler = errorHandler)
        return addNextNode(node)
    }

    private fun addAnywayHandler(anywayHandler:(Any?)->Unit):Promistic {
        val node = Promistic(anywayHandler = anywayHandler)
        return addNextNode(node)
    }

    private fun addNextParalells(promistics:List<Promistic>, race:Boolean):Promistic {
        return addNextNode( ParallelPromistic(promistics, race) )
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    fun resolve(chainedResult:Any?=null) {
        setResult(chainedResult)
    }
    fun reject(error: Any?=null) {
        setError(error)
    }
    fun then(nextAction:(Any?)->Promistic?):Promistic {
        return addNextChain(nextAction)
    }
    fun ignore(nextAction:(Any?)->Promistic?):Promistic {
        return addNextIgnoringChain(nextAction)
    }
    // Promisticを返す fun SomePromisticFunc() があるとして、
    //
    // promise.then {
    //   return SomePromisticFunc()
    // }
    //
    // と書く代わりに
    //
    // promise.inject(SomePromisticFunc())
    //
    // と書ける。簡潔でPromiseノードが１つ少なくて済むように見えるが、
    // thenを使った場合は、thenブロックに入ったタイミングで、SomePromisticFunc()が実行され、同時に着火されるのに対して
    // injectを使う場合は、injectを呼び出したタイミングで、(着火はされないものの)SomePromisticFunc()が実行されてしまう点が異なる。
    // これを使う理由は、ほとんどない気がする。
    fun inject(node:Promistic) : Promistic {
        return addNextNode(node)
    }
    fun failed(errorHandler:(Any?)->Unit):Promistic {
        return addErrorHandler(errorHandler)
    }
    fun anyway(anywayHandler:(Any?)->Unit):Promistic {
        return addAnywayHandler(anywayHandler)
    }
    fun all(promistics:List<Promistic>):Promistic {
        return addNextParalells(promistics, race=false)
    }
    fun race(promistics:List<Promistic>):Promistic {
        return addNextParalells(promistics, race=true)
    }

    fun ignite(initialParam:Any?=null) {
        this.first.execute(initialParam)
    }

    companion object {
        private var globalIndex: Int = 0

        fun nextGlobalIndex(): Int {
            return ++globalIndex
        }

        // resolved(null)と、ほぼ等価
        fun promise(): Promistic {
            return Promistic()
        }

        fun resolved(chainedResult: Any? = null): Promistic {
            return Promistic(action = { _: Any?, promix: Promistic ->
                promix.resolve(chainedResult)
            })
        }

        fun rejected(error: Any? = null): Promistic {
            return Promistic(action = { _: Any?, promix: Promistic ->
                promix.reject(error)
            })
        }
    }
}

class ParallelPromistic
    constructor(
            promistics:List<Promistic>,
            private val race:Boolean = false ) : Promistic() {

    private val aryPromistic: ArrayList<Promistic>
    private val aryResults: ArrayList<Any?>
    private var failed = 0
    private var succeeded = 0

    private val finished
        get() = succeeded+failed
    private val isCompleted
        get() = finished == aryPromistic.size

    init {
        val count = promistics.size
        aryPromistic = ArrayList(count)
        aryResults = ArrayList(count)
        for(px in promistics) {
            aryResults.add(null)
            aryPromistic.add(px.first)
        }
    }

    private fun executeSingle(po:Promistic) {
        po.then { chainedResult:Any? ->
            onSingleTaskSucceeded(po, chainedResult)
            null
        }.failed { error:Any? ->
            onSingleTaskFailed(po, error)
        }.ignite()
    }

    override fun execute(chainedResult:Any?) {
        for(po in aryPromistic) {
            executeSingle(po)
        }
    }

    private fun onSingleTaskCompleted(promise:Promistic, result:Any?, resolved:Boolean) {
        val completed =
        synchronized (this) {
            if(resolved) {
                succeeded++
            } else {
                failed++
            }
            val index = aryPromistic.indexOf(promise.first)
            val count = aryPromistic.size
            if(index in 0..(count - 1)) {
                aryResults[index] = result
            }
            isCompleted
        }

        if(completed) {
            // raceの場合は１つでも成功すればresolve
            // all の場合はすべて成功すればresolve
            if((race && succeeded>0) || (!race && failed==0)) {
                this.resolve(aryResults)
            } else {
                this.reject(aryResults)
            }
        }

    }

    private fun onSingleTaskSucceeded(promise:Promistic, chainedResult:Any?) {
        onSingleTaskCompleted(promise, chainedResult, true)
    }

    private fun onSingleTaskFailed(promise:Promistic, error:Any?) {
        onSingleTaskCompleted(promise, error, false)
    }


}