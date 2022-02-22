package com.michael.video.v2

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.michael.video.AmvSettings
import com.michael.video.R
import com.michael.video.v2.elements.AmvExoVideoPlayer
import com.michael.video.v2.elements.AmvTrimmingSliderPanel
import com.michael.video.v2.misc.SliderWidthMediator
import com.michael.video.v2.viewmodel.TrimmingControllerViewModel
import io.github.toyota32k.bindit.*
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/**
 * トリミング用ビュー
 *  - player
 *  - slider
 *  - thumbnail list
 *  - play button
 * このビュー（および、TrimmingControllerViewModel）は、トリミング範囲を決定するまでが任務であり、実際のトリミング/トランスコード処理、および、
 * トリミング中のユーザー操作ブロック、プログレスバー、結果/エラーの表示、などは、このビューの機能には含まれない。必要に応じて利用するダイアログなどで追加する。
 */
class AmvTrimmingPlayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = AmvSettings.logger
    }

    val playerView: AmvExoVideoPlayer
    val sliderPanel: AmvTrimmingSliderPanel
    lateinit var viewModel:TrimmingControllerViewModel

    init {
        LayoutInflater.from(context).inflate(R.layout.v2_trimming_player_view, this)
        playerView = findViewById(R.id.tpv_player)
        sliderPanel = findViewById(R.id.tpv_slider_panel)
    }

    fun bindViewModel(viewModel:TrimmingControllerViewModel, binder: Binder) {
        this.viewModel = viewModel
        playerView.bindViewModel(viewModel.playerViewModel, binder)
        sliderPanel.bindViewModel(viewModel, binder)

        val playButton = findViewById<ImageButton>(R.id.tpv_playButton)
        val lifecycleOwner = lifecycleOwner()!!
        val drPlay: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_play)!!
        val drPause: Drawable = ContextCompat.getDrawable(context, R.drawable.ic_pause)!!
        binder.register(
            EnableBinding.create(lifecycleOwner, playButton, viewModel.playerViewModel.isReady.asLiveData()),
            AlphaBinding.create(lifecycleOwner, playButton, viewModel.playerViewModel.isReady.map { if(it) 1f else 0.4f }.asLiveData()),
            GenericBoolBinding.create(lifecycleOwner, playButton, viewModel.playerViewModel.isPlaying.asLiveData()) { view, playing->
                (view as? ImageButton)?.setImageDrawable( if(playing) drPause else drPlay )
            },
            viewModel.commandTogglePlay.connectViewEx(playerView),
            viewModel.commandTogglePlay.connectViewEx(playButton),
            TextBinding.create(lifecycleOwner, findViewById(R.id.tpv_trimStartText), viewModel.trimmingStartText.asLiveData()),
            TextBinding.create(lifecycleOwner, findViewById(R.id.tpv_trimEndText), viewModel.trimmingEndText.asLiveData()),
            TextBinding.create(lifecycleOwner, findViewById(R.id.tpv_trimmedRangeText), viewModel.trimmingSpanText.asLiveData()),
        )

        // トリミング用ノブの幅の分、プレーヤーの幅を小さくしておく。
        // これにより、横長動画再生時に、プレーヤーと、最大幅のサムネイルリストが縦に揃う。
        playerView.setPadding(sliderPanel.sliderView.leftExtentWidth.roundToInt(), 0,sliderPanel.sliderView.rightExtentWidth.roundToInt(), 0 )

        sliderWidthMediator = SliderWidthMediator(viewModel.frameList.contentWidth, sliderPanel, sliderPanel.sliderView.extentWidth.roundToInt(), lifecycleOwner.lifecycleScope)

//        combine(containerWidth, viewModel.frameList.contentWidth) { containerWiddth, contentWidth ->
//            if(containerWiddth>0 && contentWidth>0 ) {
//                val maxSliderWidth = contentWidth + sliderPanel.sliderView.extentWidth.roundToInt()
//                if(maxSliderWidth<containerWiddth) {
//                    maxSliderWidth
//                } else {
//                    LayoutParams.MATCH_PARENT
//                }
//            } else {
//                0
//            }
//        }.onEach {
//            if(it!=0 && sliderPanel.getLayoutWidth()!=it) {
//                sliderPanel.setLayoutWidth(it)
//            }
//        }.launchIn(lifecycleOwner.lifecycleScope)
    }

    private var sliderWidthMediator: SliderWidthMediator? = null
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sliderWidthMediator?.onContainerSizeChanged(w)
    }
}

