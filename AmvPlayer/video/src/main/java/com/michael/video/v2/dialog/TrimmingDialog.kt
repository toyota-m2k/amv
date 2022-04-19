package com.michael.video.v2.dialog

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.michael.video.R
import com.michael.video.v2.AmvTrimmingPlayerView
import com.michael.video.v2.util.AmvFile
import com.michael.video.v2.viewmodel.AmvTrimmingPlayerViewModel
import io.github.toyota32k.bindit.*
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.createViewModel
import io.github.toyota32k.dialog.task.getViewModel
import io.github.toyota32k.dialog.task.immortalTaskContext
import io.github.toyota32k.utils.onFalse
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class TrimmingDialog : UtDialog() {
    val viewModel:AmvTrimmingPlayerViewModel by lazy { immortalTaskContext.getViewModel() }
    val binder = Binder()

    override fun preCreateBodyView() {
        super.preCreateBodyView()
        title = getString(R.string.title_dialog_amv_trimming)
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        return inflater.inflate(R.layout.v2_dialog_trimming).also { dlg->
            dlg.findViewById<AmvTrimmingPlayerView>(R.id.trimmingPlayer).bindViewModel(viewModel.controlPanelModel, binder)
            binder.register(
                VisibilityBinding.create(this, dlg.findViewById(R.id.progress_panel), viewModel.isTranscodingNow.asLiveData()),
                EnableBinding.create(this, rightButton, viewModel.isReady.asLiveData()),
                ProgressBarBinding.create(this, dlg.findViewById(R.id.progress_bar), viewModel.progress.asLiveData()),
                TextBinding.create(this, dlg.findViewById(R.id.progress_text), viewModel.progress.map { "$it %" }.asLiveData()),
                )
            viewModel.result.onEach {
                if(it?.succeeded == true) {
                    complete(IUtDialog.Status.POSITIVE)
                }
            }.launchIn(lifecycleScope)
        }
    }

    override fun onPositive() {
        viewModel.transcode()
    }

    override fun onNegative() {
        if(viewModel.isTranscodingNow.value) {
            viewModel.cancel()
        } else {
            super.onNegative()
        }
    }

    companion object {
        suspend fun trimming(application: Application, source: AmvFile, destination: AmvFile) : Boolean {
            return UtImmortalSimpleTask.runAsync(TrimmingDialog::class.java.name) {
                val vm = createViewModel<AmvTrimmingPlayerViewModel>(application).apply {
                    setSourceFile(source)
                    setOutputFile(destination)
                }
                val r = showDialog(taskName) { TrimmingDialog() }
                r.status.ok.onFalse {
                    destination.safeDelete()
                }
            }
        }
    }
}