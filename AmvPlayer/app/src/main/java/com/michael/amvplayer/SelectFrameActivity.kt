package com.michael.amvplayer

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.michael.video.v2.common.AmvPickedUriSource
import com.michael.video.v2.models.ControlPanelModel
import io.github.toyota32k.bindit.Binder

class SelectFrameActivity : AppCompatActivity() {
    class SelectFrameViewModel: ViewModel() {
        var controlPanelModel: ControlPanelModel? = null

        override fun onCleared() {
            super.onCleared()
            controlPanelModel?.close()
            controlPanelModel = null
        }

        companion object {
            fun instanceFor(owner: FragmentActivity):SelectFrameViewModel {
                return ViewModelProvider(owner)[SelectFrameViewModel::class.java]
            }
        }
    }

    private var mSource: Uri? = null
    lateinit var viewModel: SelectFrameViewModel
    private val binder = Binder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.select_frame_activity)

        viewModel = SelectFrameViewModel.instanceFor(this)
        val controllerViewModel = viewModel.controlPanelModel ?: ControlPanelModel.create(this, 8, 80).apply {
            viewModel.controlPanelModel = this
            val s = intent.getParcelableExtra<Uri>("source")
            mSource = s as Uri
            playerModel.setVideoSource(AmvPickedUriSource(mSource!!), null)
        }

        findViewById<com.michael.video.v2.AmvFrameSelectorView>(R.id.frameSelector).bindViewModel(controllerViewModel,binder)
    }

}