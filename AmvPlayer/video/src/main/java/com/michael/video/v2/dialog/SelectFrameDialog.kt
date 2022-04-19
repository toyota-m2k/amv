package com.michael.video.v2.dialog

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.michael.video.R
import com.michael.video.v2.AmvFrameSelectorView
import com.michael.video.v2.common.IAmvSource
import com.michael.video.v2.core.AmvFrameExtractor
import com.michael.video.v2.models.ControlPanelModel
import com.michael.video.v2.util.AmvClipping
import com.michael.video.v2.util.AmvFitter
import com.michael.video.v2.util.FitMode
import com.michael.video.v2.util.MuSize
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.EnableBinding
import io.github.toyota32k.bindit.VisibilityBinding
import io.github.toyota32k.dialog.IUtDialog
import io.github.toyota32k.dialog.UtDialog
import io.github.toyota32k.dialog.task.*
import io.github.toyota32k.media.lib.format.HD720VideoStrategy.calcHD720Size
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.max

class SelectFrameDialog : UtDialog() {
    data class SelectFrameResult(val bitmap: Bitmap, val position:Long)

    class SelectFrameViewModel(application: Application) : AndroidViewModel(application), IUtImmortalTaskMutableContextSource {
        // IUtImmortalTaskMutableContextSource
        override lateinit var immortalTaskContext: IUtImmortalTaskContext

        val isBusy = MutableStateFlow(false)
        var result = MutableStateFlow<SelectFrameResult?>(null)
        val controlPanelModel: ControlPanelModel by lazy {
            ControlPanelModel.create(getApplication<Application>().applicationContext, 8, 80)
        }
        var scope:CoroutineScope? = null

        override fun onCleared() {
            super.onCleared()
            source?.release()
            source = null
            CoroutineScope(Dispatchers.Main).launch {
                controlPanelModel.close()
            }
        }

        private var source:IAmvSource? = null
        private var clippingStart:Long = 0

        fun setSource(source: IAmvSource, sourceClipping: AmvClipping? = null) {
            this.source = source.apply { addRef() }
            this.clippingStart = max(0L,sourceClipping?.start ?: 0L)
            controlPanelModel.playerModel.setVideoSource(source, sourceClipping)
        }

        fun extract() {
            isBusy.value = true
            val extractScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            extractScope.launch {
                try {
                    scope = extractScope
                    val pos = controlPanelModel.sliderPosition.value
                    val uri = source?.getUriAsync() ?: return@launch
                    val videoSize = calcHD720Size(controlPanelModel.playerModel.videoSize.value?.width ?: 720, controlPanelModel.playerModel.videoSize.value?.height ?: 720)
                    val extractor = AmvFrameExtractor(AmvFitter(FitMode.Inside, MuSize(videoSize)), extractScope)
                    extractor.open(uri, getApplication<Application>().applicationContext)
                    result.value = SelectFrameResult(extractor.extractFrame(pos), pos+clippingStart)
                } catch (e:Throwable) {

                } finally {
                    isBusy.value = false
                    scope = null
                }
            }
        }

        fun cancel() {
            scope?.cancel()
            scope = null
        }
    }

    val viewModel:SelectFrameViewModel by lazy { immortalTaskContext.getViewModel() }
    val binder = Binder()

    override fun preCreateBodyView() {
        super.preCreateBodyView()
        title = getString(R.string.title_dialog_amv_select_frame)
        setLeftButton(BuiltInButtonType.CANCEL)
        setRightButton(BuiltInButtonType.DONE)
        widthOption = WidthOption.FULL
        heightOption = HeightOption.FULL
    }

    override fun createBodyView(savedInstanceState: Bundle?, inflater: IViewInflater): View {
        return inflater.inflate(R.layout.v2_dialog_select_frame).also { dlg->
            dlg.findViewById<AmvFrameSelectorView>(R.id.frameSelector).bindViewModel(viewModel.controlPanelModel,binder)
            binder.register(
                VisibilityBinding.create(this, progressRingOnTitleBar, viewModel.isBusy.asLiveData()),
                EnableBinding.create(this, rightButton, combine(viewModel.isBusy,viewModel.controlPanelModel.playerModel.isReady){busy,ready->!busy&&ready}.asLiveData()),
            )
            viewModel.result.onEach {
                if(it!=null) {
                    complete(IUtDialog.Status.POSITIVE)
                }
            }.launchIn(lifecycleScope)
        }
    }

    override fun onPositive() {
        viewModel.extract()
    }

    override fun onNegative() {
        viewModel.cancel()
        super.onNegative()
    }

    companion object {
        /**
         *
         */
        suspend fun selectFrame(application: Application, source:IAmvSource, clipping: AmvClipping?=null): SelectFrameResult? {
            return UtImmortalSimpleTask.executeAsync(SelectFrameDialog::class.java.name) {
                val vm = createViewModel<SelectFrameViewModel>(application)
                vm.setSource(source, clipping)
                showDialog(taskName) { SelectFrameDialog() }
                vm.result.value
            }
        }
    }
}