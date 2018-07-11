package com.michael.video

import android.content.Context
import org.m4m.IProgressListener
import org.m4m.MediaComposer
import org.m4m.android.AndroidMediaObjectFactory
import java.lang.Exception

class AmvTranscoder {
    fun hoge(context: Context) : Any? {
        val factory = AndroidMediaObjectFactory(context)
        val listener = object : IProgressListener {
            override fun onMediaStart() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onMediaProgress(progress: Float) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onMediaDone() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onMediaPause() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onMediaStop() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onError(exception: Exception?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

        }
        val a = MediaComposer(factory, listener)
        return a
    }
}