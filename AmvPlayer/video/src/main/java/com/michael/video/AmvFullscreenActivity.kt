package com.michael.video

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
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.michael.utils.Funcies1
import com.michael.utils.Funcies2
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
        val logger = AmvSettings.logger
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

        // 外部からPinPを閉じるためのメソッド
        fun close() {
            activityState.close()
        }

        /**
         * AmvFullscreenActivity の状態を保持するクラス
         */
        private class ActivityState {
            var state:State = State.NONE
                private set
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
                this.state = state
                stateListener.invoke(state, source)
            }

            /**
             * PinP表示中の場合にのみ、アクティビティを閉じる
             * (EditorActivity.onDestroy から呼ばれるので、全画面表示のときに閉じるとまずい。
             */
            fun close() {
                if(state==State.PINP) {
                    activity?.get()?.finishAndRemoveTask()
                }
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
    @Suppress("PrivatePropertyName")
    private val fsa_player: AmvExoVideoPlayer  by lazy {
        findViewById(R.id.fsa_player)
    }
    @Suppress("PrivatePropertyName")
    private val fsa_root: ConstraintLayout by lazy {
        findViewById(R.id.fsa_root)
    }

    /**
     * 構築
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        logger.debug()
        super.onCreate(savedInstanceState)

        activityState.onCreated(this)
        setContentView(R.layout.activity_amv_fullscreen)

        if (AmvSettings.isPinPAvailable(this)) {
            fsa_player.playerStateChangedListener.add(handlerName) { _, state ->
                if(isPinP) {
                    val playing = when (state) {
                        IAmvVideoPlayer.PlayerState.Playing -> true
                        else -> false
                    }
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
            if (requestPinP && AmvSettings.isPinPAvailable(this)) {     // PinPで起動後、全画面表示になるケースだけ、PinPボタンを表示する
                it.visibility = View.VISIBLE
                it.setOnClickListener {
                    requestPinP = true
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
        activityState.onDestroy()
        mSource?.release()
        mSource = null
        fsa_player.playerStateChangedListener.remove(handlerName)
        logger.debug()
    }

    /**
     * このActivityは singleTask モードで実行されるので、すでにActivityが存在する状態で、startActivity されると、
     * onCreate()ではなく、onNewIntent()が呼ばれる。
     * 渡されたインテントに基づいて、適切に状態を再構成する
     */
    override fun onNewIntent(intent: Intent?) {
        logger.debug()
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
    @TargetApi(Build.VERSION_CODES.O)
    private fun enterPinP() {
        if (AmvSettings.isPinPAvailable(this)) {
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
        logger.debug()
        super.onRestart()
    }

    override fun onStart() {
        logger.debug()
        super.onStart()
    }

    @Suppress("DEPRECATION")
    override fun onResume() {
        logger.debug()
        super.onResume()
        // もともと、次のフラグは、onCreate()で設定していたが、
        // Full<->PinPを行き来していると、いつの間にか、一部フラグが落ちて NavBarが表示されてしまうので、resume時にセットすることにした。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            fsa_root.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        }
    }

    override fun onPause() {
        logger.debug()
        super.onPause()
    }

    /**
     * PinP画面で、×ボタンを押したとき（閉じる）と、□ボタンを押したとき（全画面に戻る）で、
     * onPictureInPictureModeChanged()のパラメータに区別がないのだが、
     * ×を押したときは、onPictureInPictureModeChangedが呼ばれる前に onStop()が呼ばれ、□ボタンの場合は呼ばれないことが分かったので、
     * これによって、×と□を区別する。
     *
     * Android8/9 では上記の通りだったが、Android10 では、onStop より先に、onPictureInPictureModeChanged が呼ばれるようになった。
     * つまり、onStop()が呼ばれるタイミングで、Android8/9 なら、PinP中の場合と、FullScreenの場合が混在するのに対して、
     * android10 では、必ず（PinPは解除されて）FullScreenの状態になっている。このことから、PinPモードでなければ(FullScreenなら）、
     * onStop()でアクティビティを終了すれば、PinP解除＋終了というフローが維持できる。副作用として、最初からFullScreenとして起動している場合に、
     * タスクマネージャなどを表示して、アクティビティを切り替えようとしただけで、このアクティビティが終了してしまうが、
     * それ自体が、望ましい動作（FullscreenActivityを残したまま、MainActivityに戻るのを禁止したい）なので、たぶん問題はないと思われる。
     */
    override fun onStop() {
        logger.debug()
        closing = true
        intent.putExtra(KEY_PLAYING, fsa_player.isPlayingOrReservedToPlay)
        intent.putExtra(KEY_POSITION, fsa_player.seekPosition)
        fsa_player.pause()
        super.onStop()
        onResultListener.invoke(intent)
        if(!isPinP) {
            // ここに入ってくるのは、
            // - 全画面表示中に、閉じるボタンがタップされた場合(Android 9,10共通)
            // - PinP中に、×ボタンがタップされたとき(Android 10のみ）
            //
            // PinPの×ボタンを押すと、
            //  Android 10 の場合は、onPictureInPictureModeChanged で、isPinP=false にされたあと、onStopが呼ばれる。
            //  Android 9- の場合は、onStop()が呼ばれてから onPictureInPictureModeChanged が呼ばれるので、isPinPはまだtrue。
            finishAndRemoveTask()
        }
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
            // Android 9- の場合に、
            // ×ボタンがタップされた(＝ここに来る前にonStopが呼ばれている）
            // --> Activityを閉じる
            finishAndRemoveTask()
        } else {
            fsa_player.showDefaultController = true
            if(reloadingPinP) {
                // PinP中に、onNewIntent()で、pinpでの実行が要求された
                // onNewIntent()が呼ばれるケースでは、システムが、PinPモードを強制的に解除してくるので、ここに入ってくる。
                // このときは（他にうまい方法があればよいのだが）少し時間をあけて、PinPへの移行を要求する。
                // ちなみに、手持ちの Huawei の場合、100ms ではダメで、500ms ならokだった。
                reloadingPinP = false
                Handler(Looper.getMainLooper()).postDelayed({
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
        logger.debug("pinp=$isInPictureInPictureMode")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPinP = isInPictureInPictureMode
        if(!isInPictureInPictureMode) {
            onExitPinP()
        } else {
            onEnterPinP()
        }
    }
}
