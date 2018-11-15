package com.michael.amvplayer.exp

import java.lang.IllegalStateException

class Promistic constructor(
    private val action: ((Any?, Promistic)->Unit)? = null,
    private val errorHandler: ((Any?, Promistic)->Unit)? = null,
    private val anyway: ((Any?)->Unit)? = null ) {

    private var _next: Promistic? = null
    private var _head: Promistic? = null
    private var _burnt: Boolean = false
    private var _tag: Int = 0

    private val first:Promistic
        get() = _head?:this

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
        subTask.
    }

    private fun addNextChain(nextAction:(Any?)->Promistic) {
        val node = Promistic(action={ chainedResult: Any?, promix:Promistic ->
            val child = nextAction(chainedResult)
            if(null!=child) {
                chainTask(promix, child);
            } else {
                promix.setResult(chainedResult);
            }
        })
    }

    // reject されても resolved として扱う
    private  fun addNextIgnoringChain(nextAction:(Any?)->Promistic):Promistic {

    }

    private fun addErrorHandler(errorHandler:(Any?)->Unit):Promistic {

    }

    private fun addNextParalells(promistics:List<Promistic>, race:Boolean) :Promistic{

    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    fun resolve(chainedResult:Any?) {
        setResult(chainedResult)
    }
    fun reject(error: Any?) {
        setResult(error)
    }
    fun then(nextAction:(Any?)->Promistic):Promistic {
        addNextChain(nextAction)
    }
    fun ignore(nextAction:(Any?)->Promistic):Promistic {
        addNextIgnoringChain(nextAction)
    }
    fun failed(errorHandler:(Any?)->Unit):Promistic {
        addErrorHandler(errorHandler)
    }
    fun all(promistics:List<Promistic>):Promistic {
        addNextParalells(promistics, race=false)
    }
    fun race(promistics:List<Promistic>):Promistic {
        addNextParalells(promistics, race=true)
    }

    fun ignite(initialParam:Any?) {
        this.first.execute(initialParam);
    }
}