package com.michael.amvplayer

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.michael.video.AmvPickedUriSource
import com.michael.video.v2.models.TrimmingControlPanelModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.dialog.task.UtMortalActivity

class TrimmingActivity : UtMortalActivity() {
    class TrimmingViewModel: ViewModel() {
        var trimmingControllerViewModel: TrimmingControlPanelModel? = null

        override fun onCleared() {
            super.onCleared()
            trimmingControllerViewModel?.close()
            trimmingControllerViewModel = null
        }

        companion object {
            fun instanceFor(owner:FragmentActivity):TrimmingViewModel {
                return ViewModelProvider(owner)[TrimmingViewModel::class.java]
            }
        }
    }

    private var mSource: Uri? = null
    lateinit var viewModel:TrimmingViewModel
    private val binder = Binder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.trimming_activity)

        viewModel = TrimmingViewModel.instanceFor(this)
        val trimmingControllerViewModel = viewModel.trimmingControllerViewModel ?: TrimmingControlPanelModel.create(this, 8, 80).apply {
            viewModel.trimmingControllerViewModel = this
            val s = intent.getParcelableExtra<Uri>("source")
            mSource = s as Uri
            playerModel.setVideoSource(AmvPickedUriSource(mSource!!), null)
        }

        findViewById<com.michael.video.v2.AmvTrimmingPlayerView>(R.id.trimmingPlayer).bindViewModel(trimmingControllerViewModel,binder)
    }
}
