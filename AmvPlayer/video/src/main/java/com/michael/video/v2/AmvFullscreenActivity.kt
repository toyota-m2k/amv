package com.michael.video.v2

//import android.support.v7.app.AppCompatActivity
import android.annotation.TargetApi
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
import android.util.Rational
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.michael.video.AmvSettings
import com.michael.video.R
import com.michael.video.v2.elements.AmvExoVideoPlayer
import com.michael.video.v2.models.FullControlPanelModel
import com.michael.video.v2.viewmodel.AmvPlayerUnitViewModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.flow.onEach

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class AmvFullscreenActivity : AppCompatActivity() {
    companion object {
        val logger = AmvSettings.logger
        private const val INTENT_NAME = "PlayVideo"
        private const val ACTION_TYPE_KEY = "ActionType"

        private var primaryModelTemp:FullControlPanelModel? = null
        fun open(primaryModel: FullControlPanelModel, caller:FragmentActivity) {
            primaryModelTemp = primaryModel
            caller.apply {
                startActivity(Intent(caller, AmvFullscreenActivity::class.java))
            }
        }
    }
    // region Private Fields & Constants

    /**
     * PinP中のアクション
     */
    private enum class Action(val code:Int) {
        PLAY(1),
        PAUSE(2),
        SEEK_TOP(3),
    }

    private lateinit var receiver: BroadcastReceiver        // PinP中のコマンド（ブロードキャスト）を受け取るレシーバー

    lateinit var viewModel:AmvPlayerUnitViewModel
    lateinit var player: AmvExoVideoPlayer
    val binder = Binder()

    /**
     * 構築
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        logger.debug()
        try {
            viewModel = AmvPlayerUnitViewModel.secondaryInstance(this, primaryModelTemp!!)
            primaryModelTemp = null
        } catch(e:Throwable) {
            logger.stackTrace(e)
            finishAndRemoveTask()
            return
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.v2_fullscreen_activity)

        player = findViewById(R.id.fsa_player)
        player.bindViewModel(viewModel.controlPanelModel, binder)
        viewModel.attachView(player)

        binder.register(
            viewModel.controlPanelModel.commandCloseFullscreen.bind(lifecycleOwner()!!) {
                finishAndRemoveTask()
            }
        )

        if (AmvSettings.isPinPAvailable(this)) {
            if(viewModel.controlPanelModel.isPinP) {
                viewModel.playerModel.isPlaying.onEach { playing->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        playAction.isEnabled = !playing
                        pauseAction.isEnabled = playing
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        playAction.setShouldShowIcon(!playing)
                        pauseAction.setShouldShowIcon(playing)
                    }
                }
            }
        } else {
            viewModel.controlPanelModel.isPinP = false
        }

        if(viewModel.controlPanelModel.isPinP) {
            enterPinP()
        }

        /**
         * 閉じるボタン
         */
        findViewById<ImageButton>(R.id.amv_ctr_close_button)?.setOnClickListener {
            // finish()
            // ここは、本来、finish()でもよい（onStop が呼ばれて、その中で finishAndRemoveTask()を呼んでいるから）はずだし、Android 10 (Pixel) では、そのように動作したが、
            // Android 9 （HUAWEI）では、アクティビティが残ってしまったので、ここでも、finishAndRemoveTask()を呼ぶようにする。
            // finishAndRemoveTaskを２回呼んで、本当に大丈夫なのか。
            finishAndRemoveTask()
        }

        /**
         * PinPボタン
         */
        findViewById<ImageButton>(R.id.amv_ctr_pinp_button)?.let {
            if (AmvSettings.isPinPAvailable(this)) {     // PinPで起動後、全画面表示になるケースだけ、PinPボタンを表示する
                it.visibility = View.VISIBLE
                it.setOnClickListener {
                    viewModel.controlPanelModel.isPinP = true
                    enterPinP()
                }
            } else {
                it.visibility = View.GONE
            }
        }
        logger.debug("-- exit")
    }

    /**
     * 後処理
     */
    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * このActivityは singleTask モードで実行されるので、すでにActivityが存在する状態で、startActivity されると、
     * onCreate()ではなく、onNewIntent()が呼ばれる。
     * 渡されたインテントに基づいて、適切に状態を再構成する
     */
//    override fun onNewIntent(intent: Intent?) {
//        logger.debug()
//        super.onNewIntent(intent)
//    }

    /**
     * インテントで渡された情報をもとにビューを初期化する
     * onCreate / onNewIntent 共通の処理
     */
//    private fun initWithIntent(intent:Intent) {
//        val source = intent.getParcelableExtra<IAmvSource>(KEY_SOURCE)
//        requestPinP = intent.getBooleanExtra(KEY_PINP, false)
//        if (null != source) {
//            val playing = intent.getBooleanExtra(KEY_PLAYING, false)
//            val position = intent.getLongExtra(KEY_POSITION, 0)
//            val start = intent.getLongExtra(KEY_CLIP_START, -1)
//            val end = intent.getLongExtra(KEY_CLIP_END, -1)
//            if (start >= 0) {
//                fsa_player.setClip(IAmvVideoPlayer.Clipping(start, end))
//            }
//            mSource?.release()
//            mSource = source
//            fsa_player.setSource(source, playing, position)
//        }
//    }

    /**
     * PinPに遷移する
     * （PinPから通常モードへの遷移はシステムに任せる。というより、そのようなAPIは存在しない。）
     */
    @TargetApi(Build.VERSION_CODES.O)
    private fun enterPinP() {
        if (AmvSettings.isPinPAvailable(this)) {
            player.useExoController = false
            val w = viewModel.playerModel.videoSize.value?.width ?: 100
            val h = viewModel.playerModel.videoSize.value?.height ?: 100
            val ro = Rational(w, h)
            val rational = when {
                ro.isNaN || ro.isInfinite || ro.isZero -> Rational(1, 1)
                ro.toFloat() > 2.39 -> Rational(239, 100)
                ro.toFloat() < 1 / 2.39 -> Rational(100, 239)
                else -> ro
            }
            val param = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                    .setActions(listOf(playAction, pauseAction, seekTopAction))
                    .build()
            enterPictureInPictureMode(param)
        }
    }

    /**
     * PinP内のPlayボタン
     */
    private val playAction: RemoteAction by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = this@AmvFullscreenActivity
            val icon = Icon.createWithResource(context, R.drawable.ic_play)
            val title = context.getText(R.string.play)
            val pendingIntent = PendingIntent.getBroadcast(context, Action.PLAY.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.PLAY.code),PendingIntent.FLAG_IMMUTABLE)
            RemoteAction(icon, title, title, pendingIntent)
        } else {
            throw IllegalStateException("needs Android O or later.")
        }
    }

    /**
     * PinP内のPauseボタン
     */
    private val pauseAction:RemoteAction by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = this@AmvFullscreenActivity
            val icon = Icon.createWithResource(context, R.drawable.ic_pause)
            val title = context.getText(R.string.pause)
            val pendingIntent = PendingIntent.getBroadcast(context, Action.PAUSE.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.PAUSE.code),PendingIntent.FLAG_IMMUTABLE)
            RemoteAction(icon, title, title, pendingIntent)
        } else {
            throw IllegalStateException("needs Android O or later.")
        }
    }

    /**
     * 先頭へシーク
     */
    private val seekTopAction:RemoteAction by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = this@AmvFullscreenActivity
            val icon = Icon.createWithResource(context, R.drawable.ic_back)
            val title = context.getText(R.string.seekTop)
            val pendingIntent = PendingIntent.getBroadcast(context, Action.SEEK_TOP.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.SEEK_TOP.code),PendingIntent.FLAG_IMMUTABLE)
            RemoteAction(icon, title, title, pendingIntent)
        } else {
            throw IllegalStateException("needs Android O or later.")
        }
    }

    override fun onRestart() {
        logger.debug()
        super.onRestart()
    }

    override fun onPause() {
        logger.debug()
        super.onPause()
    }

    override fun onStart() {
        logger.debug()
        super.onStart()
        pinpTerminator.onStart()
    }

    override fun onStop() {
        logger.debug()
        super.onStop()
        pinpTerminator.onStop()
    }

    /**
     * PinP画面内の×ボタンでPinPを閉じるときの動作が難しい
     * - ×でPinP画面を閉じても、onDestroy()に来ない（Activityがスタックに残っているっぽい） --> 閉じるときは明示的に finishAndRemoveTask()を呼ぶ必要がありそう。
     * - ×でPinPが閉じるタイミングをどうやって検知するか？
     *    - Android 8/9 では、×ボタンが押されると、onStop() --> onPictureInPictureModeChanged(false) の順に呼ばれる
     *    - Android 10+ では、×ボタンが押されると、onPictureInPictureModeChanged(false) --> onStop() の順に呼ばれる。
     * したがって、PinPの状態から、onStop/onPictureInPictureModeChanged()が（順不同で）呼ばれた場合に終了、と判断することにする。
     */
    inner class PinPTerminator {
        private var activityStopped:Boolean = false
        private var exitPinP:Boolean = false
        private var isPinP:Boolean = false

        fun onStop() {
            activityStopped = true
            check()
        }
        fun onStart() {
            activityStopped = false
        }

        fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
            if(isInPictureInPictureMode) {
                isPinP = true
                exitPinP = false
            } else {
                if(isPinP) {
                    exitPinP = true
                    check()
                }
                isPinP = false
            }
        }

        private fun check() {
            if(activityStopped && exitPinP) {
                finishAndRemoveTask()
            }
        }
    }
    val pinpTerminator = PinPTerminator()


    /**
     * PinPモードが開始される
     */
    private fun onEnterPinP() {
        receiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null||intent.action!=INTENT_NAME) {
                    return
                }

                when(intent.getIntExtra(ACTION_TYPE_KEY, -1)) {
                    Action.PAUSE.code -> viewModel.playerModel.pause()
                    Action.PLAY.code -> viewModel.playerModel.play()
                    Action.SEEK_TOP.code -> viewModel.playerModel.seekTo(0L)
                    else -> {}
                }
            }
        }
        registerReceiver(receiver, IntentFilter(INTENT_NAME))
        player.useExoController = false
    }

    /**
     * PinPモードが終了する
     */
    private fun onExitPinP() {
        unregisterReceiver(receiver)
        player.useExoController = true
    }

    /**
     * PinPモードが変更されるときの通知
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        logger.debug("pinp=$isInPictureInPictureMode")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.controlPanelModel.isPinP = isInPictureInPictureMode
        if(!isInPictureInPictureMode) {
            onExitPinP()
        } else {
            onEnterPinP()
        }
        pinpTerminator.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }
}
