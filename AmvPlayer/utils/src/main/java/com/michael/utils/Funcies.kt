/**
 * 関数様の何か・・・Kotlinの関数リテラル、ラムダ、Javaのインスタンスメソッドなどをまとめて扱えるようにする、
 * とても funky monkey な funcy & methody クラス
 *
 * @author M.TOYOTA 2018.07.06 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
@file:Suppress("FunctionName")

package com.michael.utils

import java.lang.reflect.Method
import java.util.*

/**
 * 関数様の何か...を抽象化した基底インターフェース
 */
interface IFuncy<R> {
    fun compare(other: Any?) : Boolean
    fun invoke_(vararg args:Any?) : R
}

/**
 * Kotlinの関数リテラルを保持するクラス
 */
abstract class Funcy<R> : IFuncy<R> {

    abstract val func : Any
    override fun compare(other: Any?): Boolean {
        return when (other) {
            null -> false
            is Funcy<*> -> other.func == func
            else ->  other == func
        }
    }
}

/**
 * 引数のない関数リテラル・Javaメソッドを抽象化するインターフェース
 */
interface IFuncy0<R> : IFuncy<R> {
    fun invoke() : R
}

/**
 * 引数のないのKotlin関数リテラル
 */
class Funcy0<R: Any?>(override val func:()->R) : Funcy<R>(), IFuncy0<R> {
    override fun invoke() : R {
        return func()
    }
    override fun invoke_(vararg args: Any?): R {
        return invoke()
    }
}

/**
 * 引数１個の関数リテラル・Javaメソッドを抽象化するインターフェース
 */
interface IFuncy1<T:Any?,R:Any?> : IFuncy<R> {
    fun invoke(p:T) : R
}

/**
 * 引数１個のKotlin関数リテラル
 */
open class Funcy1<T:Any?,R:Any?>(override val func: (T)->R) : Funcy<R>(), IFuncy1<T,R> {
    override fun invoke(p:T) : R {
        return func(p)
    }
    override fun invoke_(vararg args: Any?): R {
        @Suppress("UNCHECKED_CAST")
        return func(args[0] as T)
    }

}

/**
 * 引数２個の関数リテラル・Javaメソッドを抽象化するインターフェース
 */
interface IFuncy2<T1:Any?,T2:Any?,R:Any?> : IFuncy<R> {
    fun invoke(p1:T1, p2:T2) : R
}

/**
 * 引数２個のKotlin関数リテラル
 */
open class Funcy2<T1:Any?, T2:Any?, R:Any?>(override val func:(T1, T2)->R) : Funcy<R>(), IFuncy2<T1,T2,R> {
    override fun invoke(p1:T1, p2:T2) : R {
        return func(p1,p2)
    }
    override fun invoke_(vararg args: Any?): R {
        @Suppress("UNCHECKED_CAST")
        return invoke(args[0] as T1, args[1] as T2)
    }

}

/**
 * 引数３個の関数リテラル・Javaメソッドを抽象化するインターフェース
 */
interface IFuncy3<T1:Any?,T2:Any?,T3:Any?,R:Any?> : IFuncy<R> {
    fun invoke(p1:T1, p2:T2, p3:T3) : R
}

/**
 * 引数３個のKotlin関数リテラル
 */
open class Funcy3<T1:Any?, T2:Any?, T3:Any?, R:Any?>(override val func: (T1, T2, T3)->R) : Funcy<R>(), IFuncy3<T1,T2,T3,R> {
    override fun invoke(p1:T1, p2:T2, p3:T3) : R {
        return func(p1,p2,p3)
    }
    override fun invoke_(vararg args: Any?): R {
        @Suppress("UNCHECKED_CAST")
        return invoke(args[0] as T1, args[1] as T2, args[2] as T3)
    }
}

/**
 * JavaのインスタンスメソッドをKotlinの関数リテラルと同様に扱うために、IFuncyでラップするクラス
 */
abstract class Methody<R> : IFuncy<R> {

    lateinit var obj:Any
    lateinit var method:Method

    override fun invoke_(vararg args:Any?) : R {
        @Suppress("UNCHECKED_CAST")
        return method.invoke(obj, *args) as R
    }

    override fun compare(other: Any?): Boolean {
        return when (other) {
            null -> false
            is Methody<*> -> other.method == method && other.obj == obj
            else -> false
        }
    }

    companion object {
        @JvmStatic
        fun methodOf(obj:Any, name:String) : Method? {
            for(m in obj.javaClass.methods ) {
                if(m.name == name) {
                    return m
                }
            }
            return null
        }
    }
}

/**
 * 引数のないJavaメソッドを保持するクラス
 */
@Suppress("unused")
class Methody0<R> : Methody<R>, IFuncy0<R> {

    private constructor()
    constructor(obj:Any, method:Method) {
        this.obj = obj
        this.method = method
    }

    constructor(obj:Any, methodName:String) {
        this.obj = obj
        try {
            this.method = obj.javaClass.getMethod(methodName)
        } catch (e:Exception) {
            UtLogger.error("Methody0:$methodName\n$e")
            throw e
        }
    }

    override fun invoke(): R {
        return invoke_()
    }
}

/**
 * 引数１個のJavaメソッドを保持するクラス
 */
@Suppress("unused")
class Methody1<T1,R>  : Methody<R>, IFuncy1<T1,R> {
    private constructor()
    constructor(obj:Any, method:Method) {
        this.obj = obj
        this.method = method
    }

    @JvmOverloads
    constructor(obj:Any, methodName:String, t1:Class<T1>?=null) {
        this.obj = obj
        try {
            if(null==t1) {
                this.method = methodOf(obj, methodName)!!
            } else {
                this.method = obj.javaClass.getMethod(methodName, t1)
            }
        } catch (e:Exception) {
            UtLogger.error("Methody1:$methodName, $t1\n$e")
            throw e
        }
    }

    override fun invoke(p:T1): R {
        return invoke_(p)
    }

    companion object {
        @JvmStatic
        fun <T1,R> create(obj:Any, name:String) : Methody1<T1,R>? {
            val m = Methody.methodOf(obj, name)
            if(null==m || m.parameterTypes.count() !=1) {
                return null
            }

            return Methody1(obj, m)
        }
    }

}

/**
 * 引数２個のJavaメソッドを保持するクラス
 */
@Suppress("unused")
class Methody2<T1,T2,R> : Methody<R>, IFuncy2<T1,T2,R> {
    private constructor()
    constructor(obj:Any, method:Method) {
        this.obj = obj
        this.method = method
    }
    constructor(obj:Any, methodName:String, t1:Class<T1>?=null, t2:Class<T2>?=null) {
        this.obj = obj
        try {
            if(null==t1||null==t2) {
                this.method = methodOf(obj, methodName)!!
            } else {
                this.method = obj.javaClass.getMethod(methodName, t1, t2)
            }
        } catch (e:Exception) {
            UtLogger.error("Methody2:$methodName, $t1, $t2\n$e")
            throw e
        }
    }

    override fun invoke(p1:T1, p2:T2): R {
        return invoke_(p1,p2)
    }

    companion object {
        @JvmStatic
        fun <T1,T2,R> create(obj:Any, name:String) : Methody2<T1,T2,R>? {
            val m = Methody.methodOf(obj, name)
            if(null==m || m.parameterTypes.count() !=2) {
                return null
            }
            return Methody2(obj, m)
        }
    }
}

/**
 * 引数３個のJavaメソッドを保持するクラス
 */
@Suppress("unused")
class Methody3<T1,T2,T3,R> : Methody<R>, IFuncy3<T1,T2,T3,R> {
    private constructor()
    constructor(obj:Any, method:Method) {
        this.obj = obj
        this.method = method
    }

    @JvmOverloads
    constructor(obj:Any, methodName:String, t1:Class<T1>?=null, t2:Class<T2>?=null, t3:Class<T3>?=null) {
        this.obj = obj
        try {
            if(null==t1||null==t2||null==t3) {
                this.method = methodOf(obj, methodName)!!
            } else {
                this.method = obj.javaClass.getMethod(methodName, t1, t2, t3)
            }
        } catch (e:Exception) {
            UtLogger.error("Methody3:$methodName, $t1, $t2, $t3\n$e")
            throw e
        }
    }
    override fun invoke(p1:T1, p2:T2, p3:T3): R {
        return invoke_(p1,p2,p3)
    }

    fun <T1,T2,T3,R> create(obj:Any, name:String) : Methody3<T1,T2,T3,R>? {
        val m = Methody.methodOf(obj, name)
        if(null==m || m.parameterTypes.count() !=3) {
            return null
        }
        return Methody3(obj, m)
    }
}

/**
 * Funcyたちを一束にして扱うためのコンテナの基底クラス
 * イベントリスナーなどとして使うことを想定
 */
abstract class Funcies<R> : IFuncy<Unit> {

    data class NamedFunc<R>(val name:String?, val funcy:IFuncy<R>)

    private val mArray = ArrayList<NamedFunc<R>>()

    fun add(name:String?, funcy:IFuncy<R>) {
        mArray.add(NamedFunc(name, funcy))
    }

    fun remove(f:Any) {
        if(f is String) {
            mArray.removeAll { nf->nf.name == f }
        } else {
            mArray.removeAll { nf -> nf.funcy.compare(f) }
        }
    }

    fun clear() {
        mArray.clear()
    }

    override fun invoke_(vararg args:Any?) {
        for(f in mArray) {
            f.funcy.invoke_(*args)
        }
    }

    override fun compare(other: Any?): Boolean {
        return other is Funcies<*> && other == this
    }

    fun invokeWithPredicate_(predicate:(R)->Boolean, vararg args:Any?) {
        for(f in mArray) {
            if(!predicate(f.funcy.invoke_(*args))) {
                break
            }
        }
    }
}

/**
 * 引数のないFuncyたちのコンテナ
 */
@Suppress("unused")
open class Funcies0<R:Any?> : Funcies<R>(), IFuncy0<Unit> {
    fun add(name:String?, f:()->R) : IFuncy0<R> {
        return Funcy0(f).apply {
            super.add(name, this)
        }
    }
    override fun invoke() {
        invoke_()
    }

    fun invokeWithPredicate(predicate:(R)->Boolean) {
        invokeWithPredicate_(predicate)
    }
}

/**
 * 引数１個のFuncyたちのコンテナ
 */
@Suppress("unused")
open class Funcies1<T1:Any?, R:Any?> : Funcies<R>(), IFuncy1<T1,Unit> {
    fun add(name:String?, f:(T1)->R) : IFuncy1<T1,R> {
        return Funcy1(f).apply {
            add(name, this)
        }
    }
    override fun invoke(p:T1) {
        invoke_(p)
    }

    fun invokeWithPredicate(p1:T1, predicate:(R)->Boolean) {
        invokeWithPredicate_(predicate, p1)
    }
}

/**
 * 引数２個のFuncyたちのコンテナ
 */
@Suppress("unused")
open class Funcies2<T1:Any?, T2:Any?, R:Any?> : Funcies<R>(), IFuncy2<T1,T2,Unit> {
    fun add(name:String?, f:(T1, T2)->R) : IFuncy2<T1,T2,R> {
        return Funcy2(f).apply {
            add(name, this)
        }
    }
    override fun invoke(p1:T1, p2:T2) {
        invoke_(p1, p2)
    }

    fun invokeWithPredicate(p1:T1, p2:T2, predicate:(R)->Boolean) {
        invokeWithPredicate_(predicate, p1, p2)
    }
}

/**
 * 引数３個のFuncyたちのコンテナ
 */
@Suppress("unused")
open class Funcies3<T1:Any?, T2:Any?, T3:Any?, R:Any?> : Funcies<R>(), IFuncy3<T1,T2,T3,Unit> {
    fun add(name:String?, f:(T1, T2, T3)->R) : IFuncy3<T1,T2,T3,R> {
        return Funcy3(f).apply {
            add(name, this)
        }
    }
    override fun invoke(p1:T1, p2:T2, p3:T3) {
        invoke_(p1, p2, p3)
    }

    fun invokeWithPredicate(p1:T1, p2:T2, p3:T3, predicate:(R)->Boolean) {
        invokeWithPredicate_(predicate, p1, p2, p3)
    }
}


/**
 * 高々１つのリスナーを登録するだけの場合、Funcies を使う必要はないので、簡素化したクラスも用意しておく
 */
@Suppress("unused")
open class FuncyListener0<R> {
    var funcy : IFuncy0<R>? = null
    fun set(f:IFuncy0<R>) {funcy = f}
    fun set(listener:()->R) { funcy = Funcy0(listener) }
    fun reset() {funcy = null }
    fun invoke() : R?  = funcy?.invoke()
}

@Suppress("unused")
open class FuncyListener1<T1,R> {
    var funcy : IFuncy1<T1,R>? = null
    fun set(f:IFuncy1<T1,R>) {funcy = f}
    fun set(listener:(T1)->R) { funcy = Funcy1(listener) }
    fun reset() {funcy = null }
    fun invoke(p1:T1) : R? = funcy?.invoke(p1)
}

@Suppress("unused")
open class FuncyListener2<T1,T2,R> {
    var funcy : IFuncy2<T1,T2,R>? = null
    fun set(f:IFuncy2<T1,T2,R>) {funcy = f}
    fun set(listener:(T1,T2)->R) { funcy = Funcy2(listener) }
    fun reset() {funcy = null }
    fun invoke(p1:T1, p2:T2) : R? = funcy?.invoke(p1, p2)
}

@Suppress("unused")
open class FuncyListener3<T1,T2,T3,R> {
    var funcy : IFuncy3<T1,T2,T3,R>? = null
    fun set(f:IFuncy3<T1,T2,T3,R>) {funcy = f}
    fun set(listener:(T1,T2,T3)->R) { funcy = Funcy3(listener) }
    fun reset() {funcy = null }
    fun invoke(p1:T1, p2:T2, p3:T3) : R? = funcy?.invoke(p1, p2, p3)
}

