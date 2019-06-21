package com.michael.video

//import android.support.v7.app.AppCompatActivity
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
import android.os.Handler
import android.util.Rational
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.michael.utils.Funcies1
import com.michael.utils.Funcies2
import com.michael.utils.UtLogger
import kotlinx.android.synthetic.main.activity_amv_fullscreen.*
import java.lang.ref.WeakReference

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class AmvFullscreenActivity : AppCompatActivity() {
    /**
     * FullscreenActivity の表示状態
     */
    enum class State {
        NONE,
        FULL,
        PINP
    }

    companion object {
        const val KEY_SOURCE:String = "source"
        const val KEY_POSITION:String = "position"
        const val KEY_PLAYING:String = "playing"
        const val KEY_CLIP_START:String = "start"
        const val KEY_CLIP_END:String = "end"
        const val KEY_PINP:String = "pinp"
        const val KEY_VIDEO_WIDTH = "videoWidth"
        const val KEY_VIDEO_HEIGHT = "videoHeight"

        private const val INTENT_NAME = "PlayVideo"
        private const val ACTION_TYPE_KEY = "ActionType"
        private const val handlerName="fsa"

        // Fixed: classroom#3217
        // このActivityの呼び出し元（AmvVideoController)に対して、再生位置を返すための簡単な仕掛け。
        // 本来はstartActivityForResult + onActivityResult を使って値を返すべきなのだが、
        // 動作をvideoライブラリ内に閉じ込めたかったので、少し強引だが、グローバルな領域の変数を介して、
        // 情報を受け渡しすることにした。
        val onResultListener = Funcies1<Intent, Unit>()

        // Fullscreen / PinP 表示の状態監視
        //  PinP実行ボタンのグレーアウトに使用。
        //  PinP表示中に、別のPinPの表示が要求されると
        //  ■ launchMode = standard (デフォルト) の場合
        //      PinPはシステムで１つしか実行できないらしく、新しいアクティビティがPinPとなり、
        //      もとのPinPはFullScreenに戻って背面に回る。このとき、onStopは呼ばれないので、finish()できない。
        //      結果的に、管理できないアクティビティが残ってしまうので、standardは却下。
        //
        //  ■ launchMode = singleTaskの場合
        //      onNewIntent()がよばれるが、このとき、必ずFullScreenに戻される。
        //      しかも、onNewIntent() で　enterPictureInPictureMode() を呼んでも、PinPに遷移しない。
        //      onNewIntent()が呼ばれた後、onPictureInPictureModeChanged(isInPictureInPictureMode:false) が呼ばれるが、
        //      このタイミングで、普通に、enterPictureInPictureMode() を呼んでも、やはりPinPに遷移しない。
        //      Handler().postDelayed()を使って、500ms 遅延させてから、enterPictureInPictureMode()を呼ぶと、PinPに遷移したが、
        //      100msの遅延ではダメだった。必要となる遅延時間は端末によって異なるだろうし、これを製品としてサポートするのは無理と判断。
        //
        //  そこで、AmvFullscreenActivity実行中は、新たなPinPの実行を禁止し、AmvFullscreenActivityを閉じてから、
        //  次のPinPを開いてもらうこととする。
        val stateListener = Funcies2<State,IAmvSource?,Unit>()
        val currentActivityState:State
            get() = activityState.state

        /**
         * AmvFullscreenActivity の状態を保持するクラス
         */
        private class ActivityState {
            var state:State = State.NONE
                private set(v) { field = v }
            private var activity : WeakReference<AmvFullscreenActivity>? = null
            private val source:IAmvSource?
                get() = activity?.get()?.mSource

            fun onCreated(activity:AmvFullscreenActivity) {
                this.activity = WeakReference(activity)
                this.state = State.FULL
                stateListener.invoke(state, source)
            }
            fun onDestroy() {
                if(activity!=null || state!=State.NONE) {
                    activity = null
                    state = State.NONE
                    stateListener.invoke(state, null)
                }
            }
            fun changeState(state:State) {
                stateListener.invoke(state, source)
            }
        }
        private val activityState = ActivityState()
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

    private var mSource:IAmvSource? = null
    private var closing:Boolean = false                     // ×ボタンでPinPが閉じられるときに true にセットされる
    private var requestPinP:Boolean = false                 // PinPへの遷移が要求されているか(intentで渡され、ユーザ操作によって変更される）
    private var reloadingPinP:Boolean = false               // onNewIntent()から、PinPへの移行が必要な場合にセットされる
    private var isPinP:Boolean = false                      // 現在PinP中か？
    private lateinit var receiver: BroadcastReceiver        // PinP中のコマンド（ブロードキャスト）を受け取るレシーバー

    // endregion

    /**
     * 構築
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        UtLogger.debug("##AmvFullScreenActivity.onCreate -- enter")
        super.onCreate(savedInstanceState)

        activityState.onCreated(this)
        setContentView(R.layout.activity_amv_fullscreen)

        if (AmvSettings.allowPictureInPicture) {
            fsa_player.playerStateChangedListener.add(handlerName) { _, state ->
                if(isPinP) {
                    val playing = when (state) {
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
            }
        }

        if(null!=intent) {
            initWithIntent(intent)
            if(requestPinP) {
                enterPinP()
            }
        }

        /**
         * 閉じるボタン
         */
        findViewById<ImageButton>(R.id.amv_ctr_close_button)?.setOnClickListener {
            finish()
        }

        /**
         * PinPボタン
         */
        findViewById<ImageButton>(R.id.amv_ctr_pinp_button)?.let {
            if (AmvSettings.allowPictureInPicture) {
                it.setOnClickListener {
                    requestPinP = true
                    enterPinP()
                }
            } else {
                it.visibility = View.GONE
            }
        }
        UtLogger.debug("##AmvFullScreenActivity.onCreate -- exit")
    }

    /**
     * 後処理
     */
    override fun onDestroy() {
        super.onDestroy()
        activityState.onDestroy()
        mSource?.release()
        mSource = null
        fsa_player.playerStateChangedListener.remove(handlerName)
        UtLogger.debug("##AmvFullScreenActivity.onDestroy")
    }

    /**
     * このActivityは singleTask モードで実行されるので、すでにActivityが存在する状態で、startActivity されると、
     * onCreate()ではなく、onNewIntent()が呼ばれる。
     * 渡されたインテントに基づいて、適切に状態を再構成する
     */
    override fun onNewIntent(intent: Intent?) {
        UtLogger.debug("##AmvFullScreenActivity.onNewIntent")
        super.onNewIntent(intent)
        if(intent!=null) {
            this.intent = intent
            initWithIntent(intent)
            if(requestPinP && isPinP) {
                reloadingPinP = true
            }
        }
    }

    /**
     * インテントで渡された情報をもとにビューを初期化する
     * onCreate / onNewIntent 共通の処理
     */
    private fun initWithIntent(intent:Intent) {
        val source = intent.getParcelableExtra<IAmvSource>(KEY_SOURCE)
        requestPinP = intent.getBooleanExtra(KEY_PINP, false)
        if (null != source) {
            val playing = intent.getBooleanExtra(KEY_PLAYING, false)
            val position = intent.getLongExtra(KEY_POSITION, 0)
            val start = intent.getLongExtra(KEY_CLIP_START, -1)
            val end = intent.getLongExtra(KEY_CLIP_END, -1)


            if (start >= 0) {
                fsa_player.setClip(IAmvVideoPlayer.Clipping(start, end))
            }
            mSource?.release()
            mSource = source
            fsa_player.setSource(source, playing, position)
        }
    }

    /**
     * PinPに遷移する
     * （PinPから通常モードへの遷移はシステムに任せる。というより、そのようなAPIは存在しない。）
     */
    private fun enterPinP() {
        if (AmvSettings.allowPictureInPicture) {
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
            val pendingIntent = PendingIntent.getBroadcast(context, Action.PLAY.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.PLAY.code),0)
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
            val pendingIntent = PendingIntent.getBroadcast(context, Action.PAUSE.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.PAUSE.code),0)
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
            val pendingIntent = PendingIntent.getBroadcast(context, Action.SEEK_TOP.code, Intent(INTENT_NAME).putExtra(ACTION_TYPE_KEY, Action.SEEK_TOP.code),0)
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
        // もともと、次のフラグは、onCreate()で設定していたが、
        // Full<->PinPを行き来していると、いつの間にか、一部フラグが落ちて NavBarが表示されてしまうので、resume時にセットすることにした。
        fsa_root.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    override fun onPause() {
        UtLogger.debug("##AmvFullScreenActivity.onPause")
        super.onPause()
    }

    /**
     * PinP画面で、×ボタンを押したとき（閉じる）と、□ボタンを押したとき（全画面に戻る）で、
     * onPictureInPictureModeChanged()のパラメータに区別がないのだが、
     * ×を押したときは、onPictureInPictureModeChangedが呼ばれる前に onStop()が呼ばれ、□ボタンの場合は呼ばれないことが分かったので、
     * これによって、×と□を区別する。
     */
    override fun onStop() {
        UtLogger.debug("##AmvFullScreenActivity.onStop")
        closing = true
        intent.putExtra(KEY_PLAYING, fsa_player.isPlayingOrReservedToPlay)
        intent.putExtra(KEY_POSITION, fsa_player.seekPosition)
        fsa_player.pause()
        super.onStop()
        onResultListener.invoke(intent)
    }

//    override fun onPostResume() {
//        UtLogger.debug("##AmvFullScreenActivity.onPostResume")
//        super.onPostResume()
//    }

    /**
     * PinPモードが開始される
     */
    private fun onEnterPinP() {
        activityState.changeState(State.PINP)
        fsa_player.showDefaultController = false
        receiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null||intent.action!=INTENT_NAME) {
                    return
                }

                when(intent.getIntExtra(ACTION_TYPE_KEY, -1)) {
                    Action.PAUSE.code -> fsa_player.pause()
                    Action.PLAY.code -> fsa_player.play()
                    Action.SEEK_TOP.code -> fsa_player.seekTo(0L)
                    else -> {}
                }
            }
        }
        registerReceiver(receiver, IntentFilter(INTENT_NAME))
    }

    /**
     * PinPモードが終了する
     */
    private fun onExitPinP() {
        activityState.changeState(State.FULL)
        unregisterReceiver(receiver)
        if(closing) {
            // ×ボタンがタップされた(＝ここに来る前にonStopが呼ばれている）
            // --> Activityを閉じる
            finish()
        } else {
            fsa_player.showDefaultController = true
            if(reloadingPinP) {
                // PinP中に、onNewIntent()で、pinpでの実行が要求された
                // onNewIntent()が呼ばれるケースでは、システムが、PinPモードを強制的に解除してくるので、ここに入ってくる。
                // このときは（他にうまい方法があればよいのだが）少し時間をあけて、PinPへの移行を要求する。
                // ちなみに、手持ちの Huawei の場合、100ms ではダメで、500ms ならokだった。
                reloadingPinP = false
                Handler().postDelayed({
                    enterPinP()
                }, 500)
            } else {
                // □ボタンがおされてPinPからFullScreenに移行しようとしている
                requestPinP = false
            }
        }
    }

    /**
     * PinPモードが変更されるときの通知
     */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        UtLogger.debug("##AmvFullScreenActivity.onPictureInPictureModeChanged($isInPictureInPictureMode)")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPinP = isInPictureInPictureMode
        if(!isInPictureInPictureMode) {
            onExitPinP()
        } else {
            onEnterPinP()
        }
    }
}
