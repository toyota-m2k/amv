package com.michael.utils

import android.util.Log

@Suppress("unused")
class UtLogger(val tag:String) {

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
    }

    private fun printToSystemOut(tag: String, s: String): Int {
        System.out.println("$tag:$s")
        Log.DEBUG
        return 0
    }

    private val isAndroid: Boolean by lazy {
        val runtime = System.getProperty("java.runtime.name")
        0 <= runtime.indexOf("Android")
    }

    fun target(level: Int): (String, String) -> Int {
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