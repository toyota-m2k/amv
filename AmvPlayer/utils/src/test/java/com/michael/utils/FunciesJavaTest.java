package com.michael.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FunciesJavaTest {

    // SAM
    public interface SingleMethod {
        int single(String s);
    }

    public static int callWithSAM(SingleMethod m, String s) {
        return m.single(s);
    }

    // インターフェースの継承
    public interface InheritedMethods extends SingleMethod {
        String second(int n);
    }

    // 複数メソッドi/f
    public interface MultiMethods {
        int atoi(String s);
        String itoa(int n);
    }

    // SAMを実装したクラス
    public class ImplSingleMethod implements SingleMethod {
        int num;
        int mul = 10;
        @Override
        public int single(String s) {
            UtLogger.debug("ImplSingleMethod#single");
            num = Integer.parseInt(s) * mul;
            return num;
        }
    }

    // 継承i/fを実装したクラス
    public class ImplInheritedMethods implements InheritedMethods {
        int mul = 10;
        int num;
        String str;

        @Override
        public int single(String s) {
            UtLogger.debug("ImplInheritedMethods#single");
            num = Integer.parseInt(s) * mul;
            return num;
        }

        @Override
        public String second(int n) {
            UtLogger.debug("ImplInheritedMethods#second");
            str = String.valueOf(n/mul);
            return str;
        }
    }

    // 複数メソッドi/fを実装したクラス
    public class ImplMultiMethods implements MultiMethods
    {
        int mul = 10;
        int num;
        String str;

        @Override
        public int atoi(String s) {
            UtLogger.debug("ImplMultiMethods#atoi");
            num = Integer.parseInt(s)*mul;
            return num;
        }

        @Override
        public String itoa(int n) {
            UtLogger.debug("ImplMultiMethods#itoa");
            str = String.valueOf(n/mul);
            return str;
        }
    }

    // 複数メソッドを実装したクラス
    public class MultiMethodsClass
    {
        int mul = 10;
        int num;
        String str;

        public int atoi(String s) {
            UtLogger.debug("ImplMultiMethods#atoi");
            num = Integer.parseInt(s)*mul;
            return num;
        }

        public String itoa(int n) {
            UtLogger.debug("ImplMultiMethods#itoa");
            str = String.valueOf(n/mul);
            return str;
        }
    }

    @Test
    public void samIfTest() {

        ImplSingleMethod obj = new ImplSingleMethod();
        Methody1<String,Integer> me = new Methody1<String,Integer>(obj, "single");
        int i = me.invoke("123");
        assertEquals(i, 1230);
        assertEquals(obj.num, 1230);
    }

    @Test
    public void inheritedMethodsTest() {
        ImplInheritedMethods obj = new ImplInheritedMethods();
        Methody1<String,Integer> me1 = new Methody1<String,Integer>(obj, "single");
        Methody1<Integer,String> me2 = new Methody1<Integer,String>(obj, "second");
        int i = me1.invoke("123");
        String s = me2.invoke(1230);
        assertEquals(i, 1230);
        assertEquals(s, "123");
        assertEquals(obj.num, 1230);
        assertEquals(obj.str, "123");
    }

    @Test
    public void multiMethodsTest() {
        ImplMultiMethods obj = new ImplMultiMethods();
        Methody1<String,Integer> me1 = new Methody1<String,Integer>(obj, "atoi");
        Methody1<Integer,String> me2 = new Methody1<Integer,String>(obj, "itoa");
        int i = me1.invoke("123");
        String s = me2.invoke(1230);
        assertEquals(i, 1230);
        assertEquals(s, "123");
        assertEquals(obj.num, 1230);
        assertEquals(obj.str, "123");
    }
    @Test
    public void funciesTest() {
        ImplInheritedMethods obj1 = new ImplInheritedMethods();
        ImplMultiMethods obj2 = new ImplMultiMethods();
        MultiMethodsClass obj3 = new MultiMethodsClass();

        Funcies1<String,Integer> fs1 = new Funcies1<String,Integer>();
        Funcies1<Integer,String> fs2 = new Funcies1<Integer,String>();

        fs1.add(null, new Methody1<String,Integer>(obj1,"single", String.class ));
        fs1.add(null, new Methody1<String,Integer>(obj2,"atoi", String.class ));
        fs1.add(null, new Methody1<String,Integer>(obj3,"atoi", String.class ));

        fs2.add(null, new Methody1<Integer,String>(obj1,"second", int.class ));
        fs2.add(null, new Methody1<Integer,String>(obj2,"itoa", int.class ));
        fs2.add(null, new Methody1<Integer,String>(obj3,"itoa", int.class ));

        fs1.invoke("123");
        fs2.invoke(2340);

        assertEquals(obj1.num, 1230);
        assertEquals(obj1.str, "234");
        assertEquals(obj2.num, 1230);
        assertEquals(obj2.str, "234");
        assertEquals(obj3.num, 1230);
        assertEquals(obj3.str, "234");
    }
}

