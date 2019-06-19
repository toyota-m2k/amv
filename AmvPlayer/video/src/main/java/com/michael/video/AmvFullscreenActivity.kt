package com.michael.video

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.ImageButton
import com.michael.video.IAmvSource
import com.michael.utils.Funcies1
import kotlinx.android.synthetic.main.activity_amv_fullscreen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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

        // Fixed: classroom#3217
        // このActivityの呼び出し元（AmvVideoController)に対して、再生位置を返すための簡単な仕掛け。
        // 本来はstartActivityForResult + onActivityResult を使って値を返すべきなのだが、
        // 動作をvideoライブラリ内に閉じ込めたかったので、少し強引だが、グローバルな領域の変数を介して、
        // 情報を受け渡しすることにした。
        val onResultListener = Funcies1<Intent, Unit>()
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
            val source = intent.getParcelableExtra<IAmvSource>(KEY_SOURCE)
            if(null!=source) {
                val playing = intent.getBooleanExtra(KEY_PLAYING, false)
                val position = intent.getLongExtra(KEY_POSITION, 0)
                val start = intent.getLongExtra(KEY_CLIP_START, -1)
                val end = intent.getLongExtra(KEY_CLIP_END, -1)

                if(start>=0) {
                    fsa_player.setClip(IAmvVideoPlayer.Clipping(start, end))
                }
                GlobalScope.launch(Dispatchers.Main) {
                    fsa_player.setSource(source, playing, position)
                }
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
        intent.putExtra(KEY_PLAYING, fsa_player.isPlayingOrReservedToPlay)
        intent.putExtra(KEY_POSITION, fsa_player.seekPosition)
        fsa_player.pause()
        super.onStop()
        onResultListener.invoke(intent)
    }
}
