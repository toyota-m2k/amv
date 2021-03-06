package com.michael.utils

import android.util.Log

@Suppress("unused")
interface ILogger {
    fun debug(msg:String)
    fun warn(msg:String)
    fun error(msg:String)
    fun info(msg:String)
    fun verbose(msg:String)
}

@Suppress("unused")
class UtLogger(private val tag:String) {

    companion object {
        @JvmStatic
        val instance: UtLogger by lazy {
            UtLogger("Amv")
        }

        @JvmStatic
        fun debug(s: String, vararg args: Any?) {
            instance.print(Log.DEBUG, s, *args)
        }

        @JvmStatic
        fun warn(s: String, vararg args: Any?) {
            instance.print(Log.WARN, s, *args)
        }

        @JvmStatic
        fun error(s: String, vararg args: Any?) {
            instance.print(Log.ERROR, s, *args)
        }

        @JvmStatic
        fun info(s: String, vararg args: Any?) {
            instance.print(Log.INFO, s, *args)
        }

        @JvmStatic
        fun verbose(s: String, vararg args: Any?) {
            instance.print(Log.VERBOSE, s, *args)
        }

        @JvmStatic
        fun assert(chk:Boolean, msg:String?) {
            if(!chk) {
                if(null!=msg) {
                    error(msg)
                }
            }
        }

        @JvmStatic
        fun stackTrace(e:Throwable, message:String?) {
            error("${message?:""}\n${e.message}\n${e.stackTrace}")
        }
    }

    var externalLogger:ILogger? = null

    private fun printToSystemOut(tag: String, s: String): Int {
        println("$tag:$s")
        Log.DEBUG
        return 0
    }

    private val isAndroid: Boolean by lazy {
        val runtime = System.getProperty("java.runtime.name")
        0 <= runtime?.indexOf("Android") ?: -1
    }

    private fun target(level: Int): (String, String) -> Int {
        if (!isAndroid) {
            return this::printToSystemOut
        }

        return when (level) {
            Log.DEBUG -> Log::d
            Log.ERROR -> Log::e
            Log.INFO -> Log::i
            Log.WARN -> Log::w
            else -> Log::v
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun print(level: Int, s: String, vararg args: Any?) {
        var ss = s
        if (args.count() > 0) {
            ss = String.format(s, *args)
        }
        target(level)(tag, ss)
    }

}