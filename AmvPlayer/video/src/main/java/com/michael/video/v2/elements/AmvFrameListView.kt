package com.michael.video.v2.elements

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import com.michael.video.AmvSettings
import com.michael.video.R
import com.michael.video.v2.models.ControlPanelModel
import com.michael.video.v2.models.TrimmingControlPanelModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AmvFrameListView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    companion object {
        val logger = AmvSettings.logger
    }
    val scroller: AmvHorzScrollView by lazy { findViewById(R.id.flv_scroller)}
    init {
        LayoutInflater.from(context).inflate(R.layout.v2_frame_list_view, this) as FrameLayout
        val sa = context.theme.obtainStyledAttributes(attrs, R.styleable.AmvFrameListView,defStyleAttr,0)
        try {
            val left = sa.getDimensionPixelSize(R.styleable.AmvFrameListView_extentLeft, 0)
            val right = sa.getDimensionPixelSize(R.styleable.AmvFrameListView_extentRight, 0)
            if(left>0||right>0) {
                setPadding(left, 0,right,0)
            }
        } finally {
            sa.recycle()
        }

//        if(controls.scroller.trimmingEnabled) {
//            controls.knob.background = ContextCompat.getDrawable(context, R.drawable.ic_slider_trim_guide)
//            controls.knob.visibility = View.GONE
//        }
    }

    private lateinit var viewModel: ControlPanelModel

    fun bindViewModel(viewModel: ControlPanelModel, binder:Binder) {
        this.viewModel = viewModel
        val scope = this.lifecycleOwner()!!.lifecycleScope
        combine(viewModel.frameList.isReady, viewModel.playerModel.naturalDuration) { ready, duration->
            if(ready) duration else 0L
        }.onEach { duration ->
            if(duration==0L) {
                scroller.clear()
            } else {
                val size = viewModel.frameList.getThumbnailSize()
                scroller.prepare(viewModel.frameList.count, size.width, size.height)
                var index = 0
                viewModel.getThumbnails { bmp->
                    scroller.addImage(ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_XY
                        layoutParams = ViewGroup.LayoutParams(size.width, size.height)
                        setImageBitmap(bmp)
                    })
                    index++
                }
                scroller.totalRange = duration
            }
        }.launchIn(scope)

        viewModel.sliderPosition.onEach {
            scroller.position = it
        }.launchIn(scope)

        if(viewModel is TrimmingControlPanelModel) {
            scroller.trimmingEnabled = true
            viewModel.trimmingStart.onEach() {
                logger.debug("TrimmingStart")
                scroller.trimStart = it
            }.launchIn(scope)
            viewModel.trimmingEnd.onEach() {
                scroller.trimEnd = it
            }.launchIn(scope)
        }
    }
}