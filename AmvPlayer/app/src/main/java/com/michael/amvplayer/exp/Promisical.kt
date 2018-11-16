package com.michael.amvplayer.exp

interface IPromiseNode {
}

interface IPromiseResolvable {
    fun resolve(chainedResult:Any?=null)
    fun reject(error: Any?=null)
}

interface IPromise : IPromiseResolvable {
    fun then(nextAction:(Any?)->IPromise?):IPromise
    fun ignore(nextAction:(Any?)->IPromise?):IPromise
    fun failed(errorHandler:(Any?)->Unit):IPromise
    fun anyway(anywayHandler: (Any?)->Unit ):IPromise
    fun all(IPromises:List<IPromise>):IPromise
    fun race(IPromises:List<IPromise>):IPromise

    fun ignite()
}

class Promisy : IPromise {
    val aryPromise = ArrayList<IPromise>()
    var current: Int = 0


    override fun ignite() {
        if(current==0) {
            resolve(null)
        }
    }


    override fun resolve(chainedResult: Any?) {
        if(current<aryPromise.size) {
            val next = aryPromise[current]
            current++
            next.resolve(chainedResult)
        }
    }
    
    override fun reject(error: Any?) {
        if(current<aryPromise.size) {
            val next = aryPromise[current]
            current++
            next.reject(error)
        }
    }

    override fun then(nextAction: (Any?) -> IPromise?): IPromise {
        val node = PromiseNode(action = { chainedResult: Any?, promix: IPromiseResolvable ->
            val nextPromise = nextAction(chainedResult)
            if(null!=nextPromise) {
                nextPromise
                .then { chainedResult2: Any? ->
                    promix.resolve(chainedResult2)
                    null
                }
                .failed { error: Any? ->
                    promix.reject(error);
                }
                .ignite()
            } else {
                promix.resolve()
            }
        })
        aryPromise.add(node)
        return this
    }

    override fun ignore(nextAction: (Any?) -> IPromise?): IPromise {
        val node = PromiseNode(action = { chainedResult: Any?, promix: IPromiseResolvable ->
            val nextPromise = nextAction(chainedResult)
            if(null!=nextPromise) {
                nextPromise
                .anyway {param:Any?->
                    promix.resolve(param);
                }
                .ignite()
            } else {
                promix.resolve()
            }
        })
        aryPromise.add(node)
        return this
    }

    override fun failed(errorHandler: (Any?) -> Unit): IPromise {
        val node = PromiseNode(errorHandler = errorHandler)
        aryPromise.add(node)
        return this;
    }


    override fun anyway(anywayHandler: (Any?)->Unit ):IPromise {
        val node = PromiseNode(anywayHandler = anywayHandler)
        aryPromise.add(node)
        return this
    }

    override fun all(IPromises: List<IPromise>): IPromise {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun race(IPromises: List<IPromise>): IPromise {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun safe_call(proc: ((Any?)->Unit)?, param:Any?) {
        if(proc!=null) {
            try {
                proc.invoke(param)
            } catch(e:Throwable) {
                // ignore error
            }
        }
    }

    inner class PromiseNode (
            val action:((Any?, promix:IPromiseResolvable) -> Unit)? = null, 
            val errorHandler:((Any?)->Unit)? = null,
            val anywayHandler:((Any?)->Unit)? = null
            )  : IPromise {

        override fun resolve(chainedResult: Any?) {
            if(null!=action) {
                try {
                    action.invoke(chainedResult, this)
                } catch(e:Throwable) {
                    reject(e);
                    return;
                }
            }
            safe_call(anywayHandler, chainedResult)
            this@Promisy.resolve(chainedResult)
        }

        override fun ignite() {
            this@Promisy.ignite()
        }

        override fun reject(error: Any?) {
            safe_call(errorHandler, error)
            safe_call(anywayHandler, error)
            this@Promisy.reject(error);
        }

        override fun then(nextAction: (Any?) -> IPromise?): IPromise {
            return this@Promisy.then(nextAction)
        }

        override fun ignore(nextAction: (Any?) -> IPromise?): IPromise {
            return this@Promisy.ignore(nextAction)
        }

        override fun anyway(anywayHandler: (Any?)->Unit ):IPromise {
            return this@Promisy.anyway(anywayHandler)
        }

        override fun failed(errorHandler: (Any?) -> Unit): IPromise {
            return this@Promisy.failed(errorHandler)
        }

        override fun all(promises: List<IPromise>): IPromise {
            return this@Promisy.all(promises)
        }

        override fun race(promises: List<IPromise>): IPromise {
            return this@Promisy.all(promises)
        }
    }

}

