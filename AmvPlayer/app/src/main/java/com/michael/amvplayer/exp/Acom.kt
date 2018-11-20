package com.michael.amvplayer.exp

interface IPromiseResolver {
    fun resolve(result:Any?)
    fun reject(error:Any?)

    fun resolveOrReject(resolve:Boolean, param:Any?) {
        if(resolve) {
            this.resolve(param)
        } else {
            this.reject(param)
        }
    }
}

interface IPromise {
    fun execute(chainedResult:Any?, promix:IPromiseResolver)
    fun accept(resolve:Boolean) : Boolean
}

interface IPromiseNode : IPromise {
    fun then(proc:(result:Any?, promix:IPromiseResolver)->IPromiseNode?) : IPromiseNode
    fun failed(proc:(error:Any?)->Unit) : IPromiseNode
}

class PromiseChain : IPromise, IPromiseResolver {
    val chain = ArrayList<IPromiseNode>()
    var currentIndex = 0
    var resolve:Boolean = false
    var promix:IPromiseResolver?

    val first: IPromiseNode?
        get() {
            if(currentIndex>0||chain.count()==0) {
                return null
            }
            currentIndex = 1
            return chain[0]
        }
    val next: IPromiseNode?
        get() {
            if(currentIndex>chain.count())  {
                return null
            }
            return chain[currentIndex++]
        }

    override fun execute(chainedResult: Any?, promix: IPromiseResolver) {
        if(first==null) {
            promix.resolveOrReject(resolve, chainedResult)
        } else {
            first.execute(chainedResult, this)
        }
    }



    override fun accept(resolve: Boolean): Boolean {
        return true
    }

    override fun resolve(result: Any?) {
    }

    override fun reject(error: Any?) {
    }
}

abstract class PromiseNode : IPromiseNode {
    override fun then(proc: (result: Any?, promix: IPromiseResolver) -> IPromiseNode?): IPromiseNode {

    }

    override fun failed(proc: (error: Any?) -> Unit): IPromiseNode {

    }
}


class ThenNode : PromiseNode() {
    val handler:((chainedResult:Any?)->IPromiseNode?)? = null

    override fun accept(resolve: Boolean) : Boolean {
        return resolve
    }

    override fun execute(chainedResult:Any?, promix: IPromiseResolver) {
        if(null!=handler) {
            val child = handler.invoke(chainedResult)
            if(null!=child) {
                child.execute(null, promix)
                return
            }
        }
        promix.resolve(chainedResult)
    }
}

class FailedNode : PromiseNode() {
    val handler:((error:Any?)->Unit)? = null

    override fun accept(resolve: Boolean) : Boolean {
        return !resolve
    }

    override fun execute(chainedResult: Any?, promix: IPromiseResolver) {
        if(null!=handler) {
            handler.invoke(chainedResult)
        }
        promix.reject(chainedResult);
    }
}

class AnywayNode : PromiseNode() {
    val handler:((param:Any?)->Unit)? = null
    var resolve: Boolean = false

    override fun accept(resolve: Boolean) : Boolean {
        this.resolve = resolve
        return true;
    }

    override fun execute(chainedResult: Any?, promix: IPromiseResolver) {
        if(null!=handler) {
            handler.invoke(chainedResult)
        }
        promix.resolveOrReject(resolve, chainedResult)
    }
}

class ActionPromise : IPromise {

    var action: ((result: Any?, promix: IPromiseResolver) -> Unit) = null;

    override fun execute(promix: IPromiseResolver) {

    }
}

class PromiseList : IPromiseNode {
    val list = ArrayList<IPromise>()


    override fun execute(promix: IPromiseResolver) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun then(proc: (result: Any?, promix: IPromiseResolver) -> IPromiseNode?): IPromiseNode {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun failed(proc: (error: Any?) -> Unit): IPromiseNode {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
