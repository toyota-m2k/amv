package com.michael.amvplayer

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageButton
import com.michael.utils.UtLogger
import com.michael.video.AmvTranscoder
import com.michael.video.AmvTrimmingPlayerView
import java.io.File
import kotlin.math.roundToInt

class TrimmingActivity : AppCompatActivity() {

    private var mSource:File? = null
    private val mTranscoder : AmvTranscoder by lazy {
        AmvTranscoder(mSource!!, applicationContext).apply {
            completionListener.set {_, result->
                UtLogger.debug("Trimming done $result")
            }
            progressListener.set {_, progress->
                UtLogger.debug("Trimming progress ${(progress*100).roundToInt()}")
            }
        }
    }

    private inner class Controls {
        val trimmingPlayer:AmvTrimmingPlayerView by lazy {
            findViewById<AmvTrimmingPlayerView>(R.id.trimmingPlayer)
        }
        val closeButton:Button by lazy {
            findViewById<Button>(R.id.closeButton)
        }
        val trimmingButton:Button by lazy {
            findViewById<Button>(R.id.trimming_button)
        }

        fun initialize() {
            closeButton.setOnClickListener {
                finish()
            }
            trimmingButton.setOnClickListener {
                if(trimmingPlayer.isTrimmed) {
                    val range = trimmingPlayer.trimmingRange
                    val source = mSource
                    if(null!=source) {
                        try {
                            val path = File.createTempFile("TRIM_", ".mp4", MainActivity.getWorkFolder())
                            mTranscoder.truncate(path, range.start, range.end)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
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
        controls.initialize()
    }
}
