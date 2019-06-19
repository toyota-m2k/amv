package com.michael.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Rational
import android.view.View
import android.widget.ImageButton
import com.michael.utils.UtLogger
import kotlinx.android.synthetic.main.activity_amv_fullscreen.*
import java.io.File
import java.lang.IllegalStateException

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
        const val KEY_PINP:String = "pinp"
        const val KEY_VIDEO_WIDTH = "videoWidth"
        const val KEY_VIDEO_HEIGHT = "videoHeight"
    }

    private val handlerName="fsa"

    override fun onCreate(savedInstanceState: Bundle?) {
        UtLogger.debug("##AmvFullScreenActivity.onCreate -- enter")
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

                if (intent.getBooleanExtra(KEY_PINP, false)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        fsa_player.playerStateChangedListener.add(handlerName) {_,state->
                            val playing = when(state) {
                                IAmvVideoPlayer.PlayerState.Playing -> true
                                else -> false
                            }
                            playAction.isEnabled = !playing
                            pauseAction.isEnabled = playing
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                playAction.setShouldShowIcon(!playing)
                                pauseAction.setShouldShowIcon(playing)
                            }
                        }
                        val w = intent.getIntExtra(KEY_VIDEO_WIDTH, 0)
                        val h = intent.getIntExtra(KEY_VIDEO_HEIGHT, 0)
                        val ro = Rational(w, h)
                        val rational = when {
                            ro.isNaN || ro.isInfinite || ro.isZero -> Rational(1, 1)
                            ro.toFloat() > 2.39 -> Rational(239, 100)
                            ro.toFloat() < 1 / 2.39 -> Rational(100, 239)
                            else -> ro
                        }
                        val param = PictureInPictureParams.Builder()
                                .setAspectRatio(rational)
                                .setActions(listOf(playAction, pauseAction))
                                .build()
                        enterPictureInPictureMode(param)
                    }
                }


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
        UtLogger.debug("##AmvFullScreenActivity.onCreate -- exit")
    }


    enum class Action(val code:Int) {
        PLAY(1),
        PAUSE(2),
    }

    private val INTENT_NAME = "PlayVideo"
    private val ACTION_TYPE_KEY = "ActionType"

    val playAction: RemoteAction by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = this@AmvFullscreenActivity
            val icon = Icon.createWithResource(context, R.drawable.ic_play)
            val title = context.getText(R.string.play)
            val pendingIntent = PendingIntent.getBroadcast(context, Action.PLAY.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.PLAY.code),0)
            RemoteAction(icon, title, title, pendingIntent)
        } else {
            throw IllegalStateException("needs Android O or later.")
        }
    }
    val pauseAction:RemoteAction by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = this@AmvFullscreenActivity
            val icon = Icon.createWithResource(context, R.drawable.ic_pause)
            val title = context.getText(R.string.pause)
            val pendingIntent = PendingIntent.getBroadcast(context, Action.PAUSE.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.PAUSE.code),0)
            RemoteAction(icon, title, title, pendingIntent)
        } else {
            throw IllegalStateException("needs Android O or later.")
        }
    }

    override fun onRestart() {
        UtLogger.debug("##AmvFullScreenActivity.onRestart")
        super.onRestart()
    }

    override fun onStart() {
        UtLogger.debug("##AmvFullScreenActivity.onStart")
        super.onStart()
    }

    override fun onResume() {
        UtLogger.debug("##AmvFullScreenActivity.onResume")
        super.onResume()
    }

    /**
     * PinP画面で、×ボタンを押したとき（閉じる）と、□ボタンを押したとき（全画面に戻る）で、
     * onPictureInPictureModeChanged()のパラメータに区別がないのだが、
     * ×を押したときは、onPictureInPictureModeChangedが呼ばれる前に onStop()が呼ばれ、□ボタンの場合は呼ばれないことが分かったので、
     * これによって、×と□を区別する。
     */
    private var closing:Boolean = false

    override fun onStop() {
        UtLogger.debug("##AmvFullScreenActivity.onStop")
        closing = true
        fsa_player.pause()
        super.onStop()
    }

    override fun onPause() {
        UtLogger.debug("##AmvFullScreenActivity.onPause")
        super.onPause()
        if(!intent.getBooleanExtra(KEY_PINP, false)) {
            fsa_player.pause()
        }
    }

    private lateinit var receiver: BroadcastReceiver

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        UtLogger.debug("##AmvFullScreenActivity.onPictureInPictureModeChanged")
        UtLogger.info("-------")
        UtLogger.info(newConfig?.toString()?:"null")
        UtLogger.info("-------")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if(!isInPictureInPictureMode) {
            // PinPモードで×ボタンが押されたときに、ここに入ってくる
//            fsa_player.showDefaultController = true
            unregisterReceiver(receiver)
            if(closing) {
                finish()
            } else {
                fsa_player.showDefaultController = true
            }
        } else {
            fsa_player.showDefaultController = false
            receiver = object: BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null||intent.action!=INTENT_NAME) {
                        return
                    }

                    when(intent.getIntExtra(ACTION_TYPE_KEY, -1)) {
                        Action.PAUSE.code -> fsa_player.pause()
                        Action.PLAY.code -> fsa_player.play()
                        else -> {}
                    }
                }
            }
            registerReceiver(receiver, IntentFilter(INTENT_NAME))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fsa_player.playerStateChangedListener.remove(handlerName)
        UtLogger.debug("##AmvFullScreenActivity.onDestroy")
    }
}
