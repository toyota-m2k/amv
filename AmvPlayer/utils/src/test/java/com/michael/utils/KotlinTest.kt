package com.michael.utils

import org.junit.Test
import org.junit.Assert.*

class KotlinTest {
    open class Base(val a:Int, val b:String) {
        var c:Float
        init {
            c = 0f
        }
        constructor(b:String, c:Float) : this(1111, b) {
            this.c = c
        }

        open val dumpString:String
            get() = "Base: a=$a, b=$b, c=$c"

        fun dump() {
            UtLogger.debug(dumpString)
        }
    }

    class XDerived(a:Int, b:String) : Base(a,b) {
        var x:Double
        init {
            x=0.0
        }
        constructor(x:Double) : this(2222, "xdrived") {
            this.x=x
        }

        override val dumpString: String
            get() = "XDerived: x=$x ... ${super.dumpString}"
    }

    class YDerived(a:Int, b:String) : Base(a,b) {
        data class Some(val s:Int,val e:Int)
        var y:Some = Some(0,0)
        constructor(y:Some) : this(3333,"yderived") {
            this.y = y
        }
    }

    @Test
    fun reflectConstructorTest() {
        val xclass = XDerived::class.java
        val ctor = xclass.getConstructor(Int::class.java, String::class.java)
        assertNotNull(ctor)
        val x = ctor.newInstance(123, "created")
        assertNotNull(x)
        if(null!=x) {
            assertEquals(x.a, 123)
            assertEquals(x.b, "created")
            assertEquals(x.x, 0.0, 0.0001)
            assertEquals(x.c, 0f, 0.0001f)
        }

        val kclass = XDerived::class
        for(kf in kclass.constructors) {
            UtLogger.debug("$kf")
            for(p in kf.parameters) {
                UtLogger.debug("${p.name} : ${p.type}")
                val type = p.type
                val type2 = Int::class.qualifiedName
            }
        }
    }
}