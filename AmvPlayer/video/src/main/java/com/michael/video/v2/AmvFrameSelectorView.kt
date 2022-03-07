package com.michael.video.v2

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.michael.video.R
import com.michael.video.v2.elements.AmvExoVideoPlayer
import com.michael.video.v2.elements.AmvSliderPanel
import com.michael.video.v2.misc.SliderWidthMediator
import com.michael.video.v2.models.ControlPanelModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.utils.lifecycleOwner
import kotlin.math.roundToInt

class AmvFrameSelectorView@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    private val playerView: AmvExoVideoPlayer
    private val sliderPanel: AmvSliderPanel
    lateinit var viewModel: ControlPanelModel

    init {
        LayoutInflater.from(context).inflate(R.layout.v2_frame_selector_view, this)
        playerView = findViewById(R.id.vfs_player)
        sliderPanel = findViewById(R.id.vfs_slider_panel)
    }

    fun bindViewModel(viewModel: ControlPanelModel, binder: Binder) {
        this.viewModel = viewModel
        playerView.bindViewModel(viewModel, binder)
        sliderPanel.bindViewModel(viewModel, binder)
        val lifecycleOwner = lifecycleOwner()!!
        sliderWidthMediator= SliderWidthMediator(viewModel.frameList.contentWidth, sliderPanel, sliderPanel.sliderView.extentWidth.roundToInt(), lifecycleOwner.lifecycleScope)
    }

    private var sliderWidthMediator: SliderWidthMediator? = null
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sliderWidthMediator?.onContainerSizeChanged(w)
    }
}