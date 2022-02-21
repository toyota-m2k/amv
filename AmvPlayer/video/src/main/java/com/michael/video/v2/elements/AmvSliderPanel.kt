/**
 * トラッキング、トリミング兼用 スライダー
 *  サムネイルビュー(AmvFrameListView) + スライダー(AmvSliderView)
 *
 * @author M.TOYOTA 2018.07.12 Created
 * @author M.TOYOTA 2022.02.17 Updated (v2)
 * Copyright © 2018-2022 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video.v2.elements

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import com.michael.video.AmvSettings
import com.michael.video.R
import com.michael.video.v2.viewmodel.ControllerViewModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AmvSliderPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger get() = AmvSettings.logger
    }
    lateinit var viewModel:ControllerViewModel
    val frameListView: AmvFrameListView
    val sliderView: AmvSliderView

    init {
        LayoutInflater.from(context).inflate(R.layout.v2_slider_panel, this)
        frameListView = findViewById(R.id.sp_frameList)
        sliderView = findViewById(R.id.sp_slider)
    }

    fun bindViewModel(viewModel:ControllerViewModel, binder:Binder) {
        this.viewModel = viewModel
        viewModel.showFrameList.onEach {
            frameListView.visibility = if(it) View.VISIBLE else View.GONE
        }.launchIn(lifecycleOwner()!!.lifecycleScope)
        frameListView.bindViewModel(viewModel, binder)
        sliderView.bindViewModel(viewModel, binder)
    }
}