package com.michael.video.v2.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.michael.video.v2.models.PlayerModel
import com.michael.video.v2.models.TrimmingControlPanelModel
import com.michael.video.v2.util.AmvFile
import com.michael.video.v2.transcoder.AmvResult
import com.michael.video.v2.common.AmvPickedUriSource
import com.michael.video.v2.common.AmvSettings
import com.michael.video.v2.util.AmvClipping
import com.michael.video.v2.transcoder.AmvCascadeTranscoder
import com.michael.video.v2.transcoder.IAmvTranscoder
import io.github.toyota32k.dialog.task.IUtImmortalTaskContext
import io.github.toyota32k.dialog.task.IUtImmortalTaskMutableContextSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

open class AmvTrimmingPlayerViewModel(application:Application) : AndroidViewModel(application), IUtImmortalTaskMutableContextSource {
    override lateinit var immortalTaskContext: IUtImmortalTaskContext
    val playerModel: PlayerModel by lazy { PlayerModel(application.applicationContext) }
    val controlPanelModel:TrimmingControlPanelModel by lazy { TrimmingControlPanelModel(playerModel, 16, 80) }

    private var sourceFile = MutableStateFlow<AmvFile?>(null)
    private var outputFile = MutableStateFlow<AmvFile?>(null)
    private val transcoder = MutableStateFlow<IAmvTranscoder?>(null)
    val isTranscodingNow = transcoder.map { it!=null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isReady = combine(sourceFile, outputFile, playerModel.isReady, isTranscodingNow) { src, dst, player, busy-> src!=null && dst!=null && player && !busy }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val progress = MutableStateFlow(0)
    val remainingTime:Flow<Long> by lazy { progress.map { (transcoder.value as? AmvCascadeTranscoder)?.remainingTime ?: -1L } }
    val result = MutableStateFlow<AmvResult?>(null)
    val hasError = result.map { it?.hasError?:false }

    fun reset() {
        result.value = null
        progress.value = 0
        closeTranscoder()
    }

    fun setSourceFile(src:AmvFile) {
        sourceFile.value = src
        playerModel.setVideoSource(AmvPickedUriSource(src.toUri))
    }

    fun setOutputFile(dst:AmvFile) {
        outputFile.value = dst
    }

    fun transcode() {
        if(!isReady.value) {
            AmvSettings.logger.warn("not prepared yet or busy now.")
        }
        reset()
        val transcoder = AmvCascadeTranscoder(sourceFile.value!!, playerModel.context, progress)
        this.transcoder.value = transcoder

        CoroutineScope(Dispatchers.IO).launch {
            try {
//                progress.onEach {
//                    remainingTime.value = transcoder.remainingTime
//                }.launchIn(this)
                result.value = transcoder.transcode(outputFile.value!!, controlPanelModel.trimmingRange ?: AmvClipping.empty)
            } finally {
                closeTranscoder()
            }
        }
    }

    fun cancel() {
        transcoder.value?.cancel()
    }

    private fun closeTranscoder() {
        transcoder.value?.close()
        transcoder.value = null
    }

    override fun onCleared() {
        super.onCleared()
        CoroutineScope(Dispatchers.Main).launch {
            controlPanelModel.close()
        }
    }

    companion object {
//        fun instanceFor(owner: ViewModelStoreOwner, applicationContext: Context):AmvTrimmingPlayerViewModel {
//            return ViewModelProvider(owner)[AmvTrimmingPlayerViewModel::class.java].apply { prepare(applicationContext) }
//        }
    }
}