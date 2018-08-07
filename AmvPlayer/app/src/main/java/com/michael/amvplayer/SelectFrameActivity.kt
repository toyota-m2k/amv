package com.michael.amvplayer

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.michael.video.AmvFrameSelectorView
import java.io.File

class SelectFrameActivity : AppCompatActivity() {
    private var mSource: File? = null

    private val frameSelector by lazy {
        findViewById<AmvFrameSelectorView>(R.id.frameSelector)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.select_frame_activity)

        val intent = intent
        val s = intent.getSerializableExtra("source")
        mSource = s as? File
        if (null != mSource && null == savedInstanceState) {
            frameSelector.setSource(mSource!!)
        }
    }

}