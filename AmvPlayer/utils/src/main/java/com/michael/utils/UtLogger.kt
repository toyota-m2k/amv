package com.michael.utils

import android.util.Log

class UtLogger(val tag:String) {

    companion object {
        val instance : UtLogger by lazy {
            UtLogger("Amv")
        }

        fun debug(s:String, vararg args:Any?) {
            instance.debug(s, *args)
        }
        fun warn(s:String, vararg args:Any?) {
            instance.warn(s, *args)
        }
        fun error(s:String, vararg args:Any?) {
            instance.error(s, *args)
        }
        fun info(s:String, vararg args:Any?) {
            instance.info(s, *args)
        }
        fun verbose(s:String, vararg args:Any?) {
            instance.verbose(s, *args)
        }
    }

    fun print_sys(tag:String, s:String) : Int {
        System.out.println(tag + ":" + s)
        Log.DEBUG
        return 0;
    }

    val isAndroid : Boolean by lazy {
        var runtime = System.getProperty("java.runtime.name")
        0 <= runtime.indexOf("Android")
    }

    fun target(level:Int) :(String,String)->Int {
        if(isAndroid) {
            return when(level) {
                Log.DEBUG -> Log::d
                Log.ERROR -> Log::e
                Log.INFO -> Log::i
                Log.WARN -> Log::w
                else -> Log::v
            }
        } else {
            return this::print_sys
        }
    }

    fun print(level:Int, s:String, vararg args:Any?) {
        var ss = s;
        if(args.count()>0) {
            ss = String.format(s,*args)
        }
        target(level)(tag,ss);
    }

    fun debug(s:String, vararg args:Any?) {
        instance.print(Log.DEBUG, s, *args)
    }
    fun warn(s:String, vararg args:Any?) {
        instance.print(Log.WARN, s, *args)
    }
    fun error(s:String, vararg args:Any?) {
        instance.print(Log.ERROR, s, *args)
    }
    fun info(s:String, vararg args:Any?) {
        instance.print(Log.INFO, s, *args)
    }
    fun verbose(s:String, vararg args:Any?) {
        instance.print(Log.VERBOSE, s, *args)
    }
}