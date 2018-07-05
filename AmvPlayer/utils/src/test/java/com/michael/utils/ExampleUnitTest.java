package com.michael.utils;

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    interface ITheThing {
        String doIt(int n);
    }

    @Test
    public void funcy_test() {
        ITheThing t = new ITheThing() {
            @Override
            public String doIt(int n) {
                return "hoge:" + n;
            }
        };

//        Class co = t.getClass();
//        Method m = ITheThing.class.getMethod("doIt", int.class);
//        String r = (String)m.invoke(t, 1);
//
//        assertEquals(r,"hoge:1");
//
//        m = co.getMethod("doIt", int.class);
//        r = (String)m.invoke(t, 2);
//        assertEquals(r,"hoge:2");

//        for(m in c::class.members) {
//            if(m.name == "doIt") {
//                val f = Funcy1<Int,String>(m as KFunction<String>)
//                assertEquals("Test:10", f.invoke(10))
//            }
//        }

    }
}