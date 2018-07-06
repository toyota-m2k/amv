package com.michael.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class FunciesTest {
    // SAM
    interface SingleMethod {
        fun single(s: String): Int
    }

    // インターフェースの継承
    interface InheritedMethods : SingleMethod {
        fun second(n: Int): String
    }

    // 複数メソッドi/f
    interface MultiMethods {
        fun atoi(s: String): Int
        fun itoa(n: Int): String
    }

    // SAMを実装したクラス
    inner class ImplSingleMethod : SingleMethod {
        var number:Int = 0
        override fun single(s: String): Int {
            UtLogger.debug("ImplSingleMethod#single")
            number = Integer.parseInt(s)
            return number;
        }
    }

    // 継承i/fを実装したクラス
    inner class ImplInheritedMethods(val mul:Int) : InheritedMethods {

        var number:Int = 0
        var str:String = ""
        override fun single(s: String): Int {
            UtLogger.debug("ImplInheritedMethods#single")
            number = Integer.parseInt(s) * mul
            return number
        }

        override fun second(n: Int): String {
            UtLogger.debug("ImplInheritedMethods#second")
            str = (n/mul).toString()
            return str
        }
    }

    // 複数メソッドi/fを実装したクラス
    inner class ImplMultiMethods(val mul:Int) : MultiMethods {
        var number:Int = 0
        var str:String = ""
        override fun atoi(s: String): Int {
            UtLogger.debug("ImplMultiMethods#atoi")
            number = Integer.parseInt(s) *mul
            return number
        }

        override fun itoa(n: Int): String {
            UtLogger.debug("ImplMultiMethods#itoa")
            str = (n/mul).toString()
            return str
        }
    }

    // 複数メソッドを実装したクラス
    inner class MultiMethodsClass(val mul:Int) {
        var number:Int = 0
        var str:String = ""
        fun atoi(s: String): Int {
            UtLogger.debug("ImplMultiMethods#atoi")
            number = Integer.parseInt(s) *mul
            return number
        }

        fun itoa(n: Int): String {
            UtLogger.debug("ImplMultiMethods#itoa")
            str = (n/mul).toString()
            return str
        }
    }

    @Test
    fun kotlinSAMFuncyTest() {
        val s = object : SingleMethod {
            override fun single(s: String): Int = Integer.parseInt(s)
        }
        val funcy = Funcy1<String,Int>(s::single)
        assertEquals(funcy.invoke("123"), 123)
    }
//    @Test
//    fun JavaSAMFuncyTest() {
//        var r = FunciesJavaTest.callWithSAM({
//            s->Integer.parseInt(s)
//        }, "123")
//        assertEquals(r, 123)
//
//        val s = FunciesJavaTest::SingleMethod{ s->Integer.parseInt(s)}
//        r = FunciesJavaTest.callWithSAM(s, "234")
//        assertEquals(r, 234)
//
//        val funcy = Funcy1<String,Int>(s::single)
//        assertEquals(funcy.invoke("123"), 123)
//    }

    @Test
    fun inheritedFuncyTest() {
        val obj = ImplInheritedMethods(10)
        val f1 = Funcy1<String,Int>(obj::single)
        val f2 = Funcy1<Int,String>(obj::second)
        assertEquals(f1.invoke("123"), 1230)
        assertEquals(f2.invoke(2340), "234")
        assertEquals(obj.number, 1230)
        assertEquals(obj.str, "234")
    }

    @Test
    fun multiFuncyTest() {
        val obj = ImplMultiMethods(10)
        val f1 = Funcy1<String,Int>(obj::atoi)
        val f2 = Funcy1<Int,String>(obj::itoa)
        assertEquals(f1.invoke("123"), 1230)
        assertEquals(f2.invoke(2340), "234")
        assertEquals(obj.number, 1230)
        assertEquals(obj.str, "234")
    }

    @Test
    fun multiClassFuncyTest() {
        val obj = MultiMethodsClass(10)
        val f1 = Funcy1<String,Int>(obj::atoi)
        val f2 = Funcy1<Int,String>(obj::itoa)
        assertEquals(f1.invoke("123"), 1230)
        assertEquals(f2.invoke(2340), "234")
        assertEquals(obj.number, 1230)
        assertEquals(obj.str, "234")
    }

    @Test
    fun funciesTest() {
        val obj1 = ImplInheritedMethods(10)
        val obj2 = ImplMultiMethods(10)
        val obj3 = MultiMethodsClass(10)

        val fs1 = Funcies1<String,Int>()
        val fs2 = Funcies1<Int,String>()

        fs1.add(null, obj1::single)
        fs1.add(null, obj2::atoi)
        fs1.add(null, obj3::atoi)

        fs2.add(null, obj1::second)
        fs2.add(null, obj2::itoa)
        fs2.add(null, obj3::itoa)

        fs1.invoke("123")
        fs2.invoke(2340)

        assertEquals(obj1.number, 1230)
        assertEquals(obj1.str, "234")
        assertEquals(obj2.number, 1230)
        assertEquals(obj2.str, "234")
        assertEquals(obj3.number, 1230)
        assertEquals(obj3.str, "234")
    }

}