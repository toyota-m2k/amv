package com.michael.utils

import org.junit.Test
import org.junit.Assert.*

interface ISomething {
    fun do1void(s:String)
    fun do2int(v:Int,s:String) : Int
}

open class Thing (var prop:Int) : ISomething {

    var str:String = ""

    override fun do2int(v: Int, s: String): Int {
        UtLogger.debug("doSomething:${s}=${v} (prop=${prop})")
        prop += v;
        str = s;
        return prop
    }

    override fun do1void(s: String) {
        UtLogger.debug("doAnother:${s}=${prop}")
        str = s
    }
}



class FuncyTest {
    var xxx = object : Thing(1000) {
        fun hoge():String { return "abc"}
    }

    @Test
    fun funcy_test() {
        val thing1 = Thing(1)
        val thing2 = Thing(2)

        assertEquals("", thing1.str)
        assertEquals("", thing2.str)
        assertEquals(1, thing1.prop)
        assertEquals(2, thing2.prop)


        val x = object : Thing(1000) {
            fun hoge():String { return "abc"}
        }
        x.hoge()
        xxx = x
//        xxx.hoge()

//        var f1 = thing1::do2int
//        val f2 = f1.reflect()!!
//        val f3:(Int,String)->Int = thing1::do2int
//        val f4 = f3.reflect()!!
//        val f5 = f3 as KFunction<Int>
//
//        f1.call(1,"a")
//        f5.call(2,"b")
//        f3.invoke(3,"c")
//        f2.call(4,"d")




        val cb1 = Funcies1<String, Unit>().apply {
            add(thing1::do1void)
            add(thing2::do1void)
            invoke("hoge")
        }

        assertEquals("hoge", thing1.str)
        assertEquals("hoge", thing2.str)
        assertEquals(1, thing1.prop)
        assertEquals(2, thing2.prop)

        var count = 0
        val cb2 = Funcies2<Int,String,Int>().apply {
            add(thing1::do2int)
            add(thing2::do2int)
            invoke(2,"fuga")  {
                count++
                true
            }
        }

        assertEquals(2, count)
        assertEquals("fuga", thing1.str)
        assertEquals("fuga", thing2.str)
        assertEquals(1+2, thing1.prop)
        assertEquals(2+2, thing2.prop)

        var tmp:Int = 0
        val c = ExampleUnitTest.ITheThing {
            tmp = it*2;
            "Test:${tmp}"
        }

//        for(m in c::class.members) {
//            if(m.name == "doIt") {
//                val f = Funcy1<Int,String>(m as KFunction<String>)
//                assertEquals("Test:10", f.invoke(10))
//            }
//        }

        for(m in c.javaClass.methods ) {
            if(m.name == "doIt") {
                val my =  Methody1<Int,String>(c, m)
                var res = my.invoke(100)
                assertEquals(tmp, 200)
                assertEquals(res, "Test:200")
            }
        }

        try {
            // Int.javaClass だと、KotlinのIntクラスのjavaClass(!=int.class)が返ってきてしまう
            // Int::class.java としなければならない
            val m = c.javaClass.getMethod("doIt", Int::class.java)
            val that = Methody1<Int,String>(c, m)
            val res = that.invoke(150)

            assertEquals(tmp, 300)
            assertEquals(res, "Test:300")
        } catch(e:Exception) {
            UtLogger.error(e.toString())
        }

        val m = Methody1.create<Int,String>(c,"doIt")
        assertTrue(m!=null)

        val res = m!!.invoke(300)

        assertEquals(tmp, 600)
        assertEquals(res, "Test:600")


    }
}