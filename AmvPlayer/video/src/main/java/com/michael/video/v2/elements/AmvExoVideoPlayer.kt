package com.michael.video.v2.elements

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.exoplayer2.ui.PlayerView
import com.michael.video.*
import com.michael.video.v2.viewmodel.PlayerViewModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.BoolConvert
import io.github.toyota32k.bindit.TextBinding
import io.github.toyota32k.bindit.VisibilityBinding
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.flow.*

class AmvExoVideoPlayer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = AmvSettings.logger

        fun createViewModel(context:Context) : PlayerViewModel {
            return PlayerViewModel(context)
        }
    }


    // 使う人（ActivityやFragment）がセットすること
    private lateinit var viewModel: PlayerViewModel
    val useController:Boolean
    val fitParent:Boolean

    private val playerView:PlayerView
    private val rootView:ViewGroup

    init {
        LayoutInflater.from(context).inflate(R.layout.v2_video_exo_player, this)
        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvExoVideoPlayer,defStyleAttr,0)
        try {
            // タッチで再生/一時停止をトグルさせる動作の有効・無効
            //
            // デフォルト有効
            //      ユニットプレーヤー以外は無効化
            if (sa.getBoolean(R.styleable.AmvExoVideoPlayer_playOnTouch, true)) {
                this.setOnClickListener {
                    if (it is AmvExoVideoPlayer) {
                        it.viewModel.togglePlay()
                    }
                }
            }
            // ExoPlayerのControllerを表示するかしないか・・・表示する場合も、カスタマイズされたControllerが使用される
            //
            // デフォルト無効
            //      フルスクリーン再生の場合のみ有効
            useController = sa.getBoolean(R.styleable.AmvExoVideoPlayer_showControlBar, false)

            // AmvExoVideoPlayerのサイズに合わせて、プレーヤーサイズを自動調整するかどうか
            // 汎用的には、AmvExoVideoPlayer.setLayoutHint()を呼び出すことで動画プレーヤー画面のサイズを変更するが、
            // 実装によっては、この指定の方が便利なケースもありそう。
            //
            // デフォルト無効
            //      フルスクリーン再生の場合のみ有効
            fitParent = sa.getBoolean(R.styleable.AmvExoVideoPlayer_fitParent, false)
        } finally {
            sa.recycle()
        }
        playerView = findViewById<PlayerView>(R.id.exp_playerView)
        rootView = findViewById(R.id.exp_player_root)
    }

    fun bindViewModel(viewModel:PlayerViewModel, binder:Binder) {
        val owner = lifecycleOwner()!!
        val scope = owner.lifecycleScope

        this.viewModel = viewModel
        playerView.player = viewModel.player

        val errorMessageView : TextView = findViewById(R.id.exp_errorMessage)
        val progressRing : View = findViewById(R.id.exp_progressRing)
        binder.register(
            VisibilityBinding.create(owner, progressRing, viewModel.isLoadingFlow.asLiveData(), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible),
            VisibilityBinding.create(owner, errorMessageView, viewModel.isErrorFlow.asLiveData(), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByInvisible),
            TextBinding.create(owner,errorMessageView, viewModel.errorMessage.filterNotNull().asLiveData()),
        )

        viewModel.playerSizeFlow.onEach(this::updateLayout).launchIn(scope)
    }

    private fun updateLayout(videoSize:Size) {
        playerView.setLayoutSize(videoSize.width, videoSize.height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if(w>0 && h>0) {
            viewModel.rootViewSize.value = Size(w, h)
        }
    }
    
//    fun togglePlay() {
//        viewModel.togglePlay()
//    }
//
//    fun play() {
//        viewModel.play()
//    }
//
//    fun pause() {
//        viewModel.pause()
//    }
//
//    fun seekTo(pos:Long) {
//        viewModel.seekTo(pos)
//    }



}