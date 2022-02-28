package com.michael.video.v2.elements

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.michael.video.AmvSettings
import com.michael.video.R
import com.michael.video.v2.models.TrimmingControlPanelModel
import io.github.toyota32k.bindit.Binder

class AmvTrimmingSliderPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = AmvSettings.logger
    }

    lateinit var viewModel: TrimmingControlPanelModel
    val frameListView: AmvFrameListView
    val sliderView: AmvSliderView

    init {
        LayoutInflater.from(context).inflate(R.layout.v2_trimming_slider_panel, this)
        frameListView = findViewById(R.id.tsp_frameList)
        sliderView = findViewById(R.id.tsp_slider)
    }

    fun bindViewModel(viewModel: TrimmingControlPanelModel, binder:Binder) {
        this.viewModel = viewModel
        frameListView.bindViewModel(viewModel, binder)
        sliderView.bindViewModel(viewModel, binder)
    }
}