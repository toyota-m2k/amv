package com.michael.video

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.ImageButton
import kotlinx.android.synthetic.main.activity_amv_fullscreen.*
import java.io.File

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class AmvFullscreenActivity : AppCompatActivity() {

    companion object {
        const val KEY_SOURCE:String = "source"
        const val KEY_POSITION:String = "position"
        const val KEY_PLAYING:String = "playing"
        const val KEY_CLIP_START:String = "start"
        const val KEY_CLIP_END:String = "end"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_amv_fullscreen)

        fsa_root.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        if(null!=intent) {
            val source = intent.getSerializableExtra(KEY_SOURCE) as? File
            if(null!=source) {
                val playing = intent.getBooleanExtra(KEY_PLAYING, false)
                val position = intent.getLongExtra(KEY_POSITION, 0)
                val start = intent.getLongExtra(KEY_CLIP_START, -1)
                val end = intent.getLongExtra(KEY_CLIP_END, -1)

                if(start>=0) {
                    fsa_player.setClip(IAmvVideoPlayer.Clipping(start, end))
                }
                fsa_player.setSource(source, playing, position)
            }
        }

        /**
         * 閉じるボタン
         */
        findViewById<ImageButton>(R.id.amv_ctr_close_button)?.setOnClickListener {
            finish()
        }
    }

    override fun onStop() {
        fsa_player.pause()
        super.onStop()
    }
}
