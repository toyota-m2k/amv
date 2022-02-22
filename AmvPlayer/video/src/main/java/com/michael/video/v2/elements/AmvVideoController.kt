package com.michael.video.v2.elements

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.asLiveData
import com.michael.utils.VectorDrawableTinter
import com.michael.video.AmvSettings
import com.michael.video.R
import com.michael.video.dp2px
import com.michael.video.v2.viewmodel.FullControllerViewModel
import io.github.toyota32k.bindit.*
import io.github.toyota32k.utils.disposableObserve
import io.github.toyota32k.utils.lifecycleOwner

class AmvVideoController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : FrameLayout(context,attrs,defStyleAttr) {
    companion object {
        val logger = AmvSettings.logger
    }


    private lateinit var viewModel: FullControllerViewModel
//    private val mMinimalMode:Boolean

    val buttonsGroup: LinearLayout by lazy { findViewById<LinearLayout>(R.id.vct_buttons) }

    val playButton: ImageButton by lazy { findViewById<ImageButton>(R.id.vct_playButton) }
    val playButtonMini: ImageButton by lazy { findViewById<ImageButton>(R.id.vct_playButton2) }
//    val actualPlayButton: ImageButton get() = if(viewModel.) playButtonMini else playButton

    val backButton: ImageButton by lazy { findViewById<ImageButton>(R.id.vct_backButton) }
    val forwardButton: ImageButton by lazy { findViewById<ImageButton>(R.id.vct_forwardButton) }
    val markButton: ImageButton by lazy { findViewById<ImageButton>(R.id.vct_markButton) }
    val pinpButton: ImageButton by lazy { findViewById<ImageButton>(R.id.vct_pinpButton) }
    val fullButton: ImageButton by lazy { findViewById<ImageButton>(R.id.vct_fullscreenButton) }
    val showFramesButton: ImageButton by lazy { findViewById<ImageButton>(R.id.vct_showFramesButton) }
    val snapshotButton: ImageButton by lazy { findViewById(R.id.vct_snapshotButton) }
    val muteButton: ImageButton by lazy { findViewById(R.id.vct_muteButton) }
    val markerView: AmvMarkerView by lazy { findViewById<AmvMarkerView>(R.id.vct_markerView) }
    val slider: AmvSliderPanel by lazy { findViewById<AmvSliderPanel>(R.id.vct_sliderPanel) }
    val counterBar: TextView by lazy { findViewById<TextView>(R.id.vct_counterBar) }
    val drPlay: Drawable? by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_play) }
    val drPause: Drawable? by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_pause) }
    val drShowFrameOff: Drawable? by lazy { AppCompatResources.getDrawable(context, R.drawable.ic_frames) }
    val drShowFrameOn: Drawable? by lazy { VectorDrawableTinter.tintDrawable(AppCompatResources.getDrawable(context, R.drawable.ic_frames)!!, ContextCompat.getColor(context, R.color.trimming_sel)) }


    init {
        LayoutInflater.from(context).inflate(R.layout.v2_video_controller, this)
//        val sa = context.theme.obtainStyledAttributes(attrs, R.styleable.AmvVideoController,defStyleAttr,0)
//        try {
//            mMinimalMode = sa.getBoolean(R.styleable.AmvVideoController_minimal, false)
//        } finally {
//            sa.recycle()
//        }
        if(!AmvSettings.allowPictureInPicture) {
            pinpButton.visibility = View.GONE
        }

        // コントローラーの最小幅 = ボタン数ｘ40dp
//        findViewById<ViewGroup>(R.id.vct_controllerRoot).minimumWidth = buttonCount * context.dp2px(40)
    }



    fun bindViewModel(viewModel:FullControllerViewModel, binder:Binder) {
        this.viewModel = viewModel
        findViewById<AmvMarkerView>(R.id.vct_markerView).bindViewModel(viewModel, binder)
        findViewById<AmvSliderPanel>(R.id.vct_sliderPanel).bindViewModel(viewModel, binder)

        // min width
        val buttonCount:Int = buttonsGroup.childCount+1 - if(AmvSettings.allowPictureInPicture) 0 else 1
        viewModel.controllerMinWidth.value = context.dp2px(40) * buttonCount

        val owner = lifecycleOwner()!!
        binder.register(
            MultiVisibilityBinding(viewModel.mininalMode.asLiveData(), BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone).apply { connectAll(owner, markerView, buttonsGroup, counterBar, showFramesButton) },
            VisibilityBinding.create(owner, playButtonMini, viewModel.mininalMode.asLiveData(), BoolConvert.Straight, VisibilityBinding.HiddenMode.HideByGone),
            viewModel.commandTogglePlay.connectViewEx(playButton),
            viewModel.commandTogglePlay.connectViewEx(playButtonMini),
            viewModel.commandNextMarker.connectViewEx(forwardButton),
            viewModel.commandPrevMarker.connectViewEx(backButton),
            viewModel.commandAddMarker.connectViewEx(markButton),
            viewModel.commandShowFrameList.connectViewEx(showFramesButton),
            // pinpButton
            // fullButton
            // snapshot
            // mute
            viewModel.commandSnapshot.connectViewEx(snapshotButton),
            viewModel.commandMute.connectViewEx(muteButton),
            viewModel.playerViewModel.isPlayingFlow.asLiveData().disposableObserve(owner) { playing->
                val drawable = if(playing==true) drPause else drPlay
                playButton.setImageDrawable(drawable)
                playButtonMini.setImageDrawable(drawable)
            },
            viewModel.showFrameList.asLiveData().disposableObserve(owner) { show->
                showFramesButton.setImageDrawable(if(show==true) drShowFrameOn else drShowFrameOff )
            },
            MultiEnableBinding.create(owner, playButton, playButtonMini, forwardButton, backButton, markButton, pinpButton, fullButton, muteButton, snapshotButton, data = viewModel.playerViewModel.isReadyFlow.asLiveData() ),
            GenericBoolMultiBinding.create(owner, playButton, playButtonMini, forwardButton, backButton, markButton, pinpButton, fullButton, muteButton, snapshotButton, data = viewModel.playerViewModel.isReadyFlow.asLiveData()) { views, enabled ->
                views.forEach { it.alpha = if(enabled) 1.0f else 0.5f }
            },
            TextBinding.create(owner, counterBar, viewModel.counterText.asLiveData()),
        )
    }
}