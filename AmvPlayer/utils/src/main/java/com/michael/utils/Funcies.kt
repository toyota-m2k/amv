/**
 * funky monkey な funcy methody クラス
 */
@file:Suppress("FunctionName")

package com.michael.utils

import java.lang.reflect.Method
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.reflect

/**
 * 関数様の何か...を抽象化した基底インターフェース
 */
interface IFuncy<R> {
    fun compare(other: Any?) : Boolean
    fun invoke_(vararg arg:Any?) : R
}

/**
 * Kotlinの関数リテラルを保持するクラス
 */
abstract class Funcy<R: Any?>(private val func: KFunction<*>) : IFuncy<R> {

    override fun invoke_(vararg arg:Any?) : R {
        @Suppress("UNCHECKED_CAST")
        return func.call(*arg) as R
    }

    override fun compare(other: Any?): Boolean {
        return when (other) {
            null -> false
            is Funcy<*> -> other.func == func
            is KFunction<*> -> other == func
            else -> false
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
class Funcy0<R: Any?>(f:()->R) : Funcy<R>(f as KFunction<*>), IFuncy0<R> {
    override fun invoke() : R {
        return invoke_()
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
class Funcy1<T:Any?,R:Any?>(func: (T)->R) : Funcy<R>(func as KFunction<*>), IFuncy1<T,R> {
    override fun invoke(p:T) : R {
        return invoke_(p)
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
class Funcy2<T1:Any?, T2:Any?, R:Any?>(func:(T1, T2)->R) : Funcy<R>(func as KFunction<*>), IFuncy2<T1,T2,R> {

    override fun invoke(p1:T1, p2:T2) : R {
        return invoke_(p1, p2)
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
class Funcy3<T1:Any?, T2:Any?, T3:Any?, R:Any?>(func: (T1, T2, T3)->R) : Funcy<R>(func as KFunction<*>), IFuncy3<T1,T2,T3,R> {

    override fun invoke(p1:T1, p2:T2, p3:T3) : R {
        return invoke_(p1, p2, p3)
    }
}

/**
 * JavaのインスタンスメソッドをKotlinの関数リテラルと同様に扱うために、IFuncyでラップするクラス
 */
abstract class Methody<R> : IFuncy<R> {

    lateinit var obj:Any
    lateinit var method:Method

    override fun invoke_(vararg arg:Any?) : R {
        @Suppress("UNCHECKED_CAST")
        return method.invoke(obj, *arg) as R
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
    constructor(obj:Any, methodName:String, t1:Class<T1>) {
        this.obj = obj
        try {
            this.method = obj.javaClass.getMethod(methodName, t1)
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
    constructor(obj:Any, methodName:String, t1:Class<T1>, t2:Class<T2>) {
        this.obj = obj
        try {
            this.method = obj.javaClass.getMethod(methodName, t1, t2)
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
    constructor(obj:Any, methodName:String, t1:Class<T1>, t2:Class<T2>, t3:Class<T3>) {
        this.obj = obj
        try {
            this.method = obj.javaClass.getMethod(methodName, t1, t2, t3)
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
abstract class Funcies<R> {

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

    fun invoke_(vararg args:Any?) {
        for(f in mArray) {
            f.funcy.invoke_(*args)
        }
    }

    fun invoke_(predicate:(R)->Boolean, vararg args:Any?) {
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
open class Funcies0<R:Any?> : Funcies<R>() {
    fun add(name:String?, f:()->R) : IFuncy0<R> {
        return Funcy0(f).apply {
            super.add(name, this)
        }
    }

    fun invoke() {
        super.invoke_()
    }

    fun invoke(predicate:(R)->Boolean) {
        super.invoke_(predicate)
    }
}

/**
 * 引数１個のFuncyたちのコンテナ
 */
@Suppress("unused")
open class Funcies1<T1:Any?, R:Any?> : Funcies<R>() {
    fun add(name:String?, f:(T1)->R) : IFuncy1<T1,R> {
        return Funcy1(f).apply {
            add(name, this)
        }
    }

    fun invoke(p1:T1) {
        super.invoke_(p1)
    }

    fun invoke(p1:T1, predicate:(R)->Boolean) {
        super.invoke_(predicate, p1)
    }
}

/**
 * 引数２個のFuncyたちのコンテナ
 */
@Suppress("unused")
open class Funcies2<T1:Any?, T2:Any?, R:Any?> : Funcies<R>() {
    fun add(name:String?, f:(T1, T2)->R) : IFuncy2<T1,T2,R> {
        return Funcy2(f).apply {
            add(name, this)
        }
    }

    fun invoke(p1:T1, p2:T2) {
        super.invoke_(p1,p2)
    }

    fun invoke(p1:T1, p2:T2, predicate:(R)->Boolean) {
        super.invoke_(predicate, p1, p2)
    }
}

/**
 * 引数３個のFuncyたちのコンテナ
 */
@Suppress("unused")
open class Funcies3<T1:Any?, T2:Any?, T3:Any?, R:Any?> : Funcies<R>() {
    fun add(name:String?, f:(T1, T2, T3)->R) : IFuncy3<T1,T2,T3,R> {
        return Funcy3(f).apply {
            add(name, this)
        }
    }

    fun invoke(p1:T1, p2:T2, p3:T3) {
        super.invoke_(p1,p2,p3)
    }

    fun invoke(p1:T1, p2:T2, p3:T3, predicate:(R)->Boolean) {
        super.invoke_(predicate, p1, p2, p3)
    }
}