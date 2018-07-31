package com.michael.amvplayer

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.michael.video.AmvTrimmingPlayerView
import java.io.File

class TrimmingActivity : AppCompatActivity() {

    private var mSource:File? = null

    private inner class Controls {
        val trimmingPlayer:AmvTrimmingPlayerView by lazy {
            findViewById<AmvTrimmingPlayerView>(R.id.trimmingPlayer)
        }
    }
    private val controls = Controls()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.trimming_activity)

        val intent = intent
        val s = intent.getSerializableExtra("source")
        mSource = s as? File
        if (null != mSource) {
            controls.trimmingPlayer.setSource(mSource!!)
        }
    }
}
