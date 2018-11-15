package com.michael.amvplayer.exp

import java.lang.IllegalStateException

class Promistic constructor(
    private val action: ((Any?, Promistic)->Unit)? = null,
    private val errorHandler: ((Any?, Promistic)->Unit)? = null,
    private val anyway: ((Any?)->Unit)? = null,
    head:(Promistic?) = null
    ) {

    private var _next: Promistic? = null
    private var _head: Promistic? = head
    private var _burnt: Boolean = false
    private var _tag: Int = 0

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


    fun execute(chainResult:Any?):Unit {
        if(_burnt) {
            throw IllegalAccessException("the task to be executed is already burning or has been burnt.");
        }
        _burnt = true;
        if(null!=action) {
            try {
                action.invoke(chainResult, this)
            } catch(e:Throwable) {
                setError(e);
            }
        } else { // action == null
            _next?.execute(chainResult)
            this.dispose()
        }
    }

    private fun abort(error:Any?) {
        if(_burnt) {
            throw IllegalAccessException("the task to be executed is already burning or has been burnt.");
        }

        _burnt = true;
        if(null!=errorHandler) {
            try {
                errorHandler.invoke(error, this)
            } catch(e:Throwable) {
            }
        }

        val next = _next;
        if( null!=next ){ // action == null
            next.abort(error);
            this.dispose()
        }

    }

    private fun setError(error:Any?) {
        if(null!=errorHandler) {
            errorHandler.invoke(error, this)
        }
        val next = _next
        if(null!=next) {
            next.abort(error)
        }
    }

    private fun setResult(chainedResult:Any?) {
        val next = _next
        if(null!=next) {
            next.execute(chainedResult);
        }
        dispose()
    }

    fun dispose() {
        // disposed
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
        .failed { error:Any?->
            parentTask.resolve(null)
        }
        .ignite()
    }

    private fun addNextChain(nextAction:(Any?)->Promistic?) : Promistic {
        validateBeforeAddingNode();
        val node = Promistic(action = { chainedResult: Any?, promix:Promistic ->
            val child = nextAction(chainedResult)
            if(null!=child) {
                chainTask(promix, child)
            } else {
                promix.resolve(chainedResult)
            }
        }, head=this.first)
        this.last._next = node
        return node
    }


    // reject されても resolved として扱う
    private  fun addNextIgnoringChain(nextAction:(Any?)->Promistic):Promistic {
        validateBeforeAddingNode();
        val node = Promistic(action = { chainedResult: Any?, promix:Promistic ->
            val child = nextAction(chainedResult)
            if(null!=child) {
                chainIgnoringTask(promix, child)
            } else {
                promix.resolve(chainedResult)
            }
        }, head=this.first)
        this.last._next = node
        return node
    }

    private fun addErrorHandler(errorHandler:(Any?)->Unit):Promistic {
        validateBeforeAddingNode();
        val node = Promistic(errorHandler = {error:Any?, promiy:Promistic ->
            errorHandler(error)
        }, head=this.first)
        this.last._next = node
        return node
    }

    private fun addNextParalells(promistics:List<Promistic>, race:Boolean) :Promistic{
        validateBeforeAddingNode();

    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    fun resolve(chainedResult:Any?=null) {
        setResult(chainedResult)
    }
    fun reject(error: Any?=null) {
        setResult(error)
    }
    fun then(nextAction:(Any?)->Promistic?):Promistic {
        return addNextChain(nextAction)
    }
    fun ignore(nextAction:(Any?)->Promistic):Promistic {
        return addNextIgnoringChain(nextAction)
    }
    fun failed(errorHandler:(Any?)->Unit):Promistic {
        return addErrorHandler(errorHandler)
    }
    fun all(promistics:List<Promistic>):Promistic {
        return addNextParalells(promistics, race=false)
    }
    fun race(promistics:List<Promistic>):Promistic {
        return addNextParalells(promistics, race=true)
    }

    fun ignite(initialParam:Any?=null) {
        this.first.execute(initialParam);
    }
}

class MMJParallelPromistic constructor(private val aryPromistic:List<Promistic>, private val race:Boolean = false, head:Promistic?=null)
    : Promistic(head=head) {
    val aryResults: ArrayList<Any?>
    var finished = 0
    var ignored = 0

    init {
        val count = aryPromistic.size
        aryResults = ArrayList<Any?>(count)
        for(px in aryPromistic) {
            aryResults.add(null)
        }
    }

    fun executeSingle(po:Promistic) {
        po.last.then { chainedResult:Any? ->
            onSingleTaskCompleted(po, chainedResult)
        }.failed { error:Any? ->
            onSingleTaskFailed(po, error)
        }.ignite()
    }

    fun execute(chainedResult:Any?) {
        for(po in aryPromistic) {
            executeSingle(po);
        }
    }

    fun onSingleTaskCompleted(promise:Promistic, chainedResult:Any?) : Promistic {
        var succeeded = false
        var failed = false
        synchronized (this) {
            finished++;
            val index = aryPromistic.indexOf(promise.first)
            val count = aryPromistic.size;
            if(0<=index && index <count) {
                aryResults[index] = chainedResult
            }
            succeeded = finished == count
            failed = ignored>0 && (ignored+finished) == count

            if(race) {
                // rase条件のときは、１つでも成功したら、その結果をresolveで返す。
                if()
            }
        }
    }



}