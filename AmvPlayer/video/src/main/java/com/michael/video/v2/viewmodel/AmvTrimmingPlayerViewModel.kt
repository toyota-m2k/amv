package com.michael.video.v2.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.michael.video.v2.models.PlayerModel
import com.michael.video.v2.models.TrimmingControlPanelModel
import com.michael.video.v2.util.AmvFile
import com.michael.video.transcoder.AmvResult
import com.michael.video.v2.common.AmvPickedUriSource
import com.michael.video.v2.common.AmvSettings
import com.michael.video.v2.util.AmvClipping
import com.michael.video.transcoder.AmvCascadeTranscoder
import com.mihcael.video.transcoder.IAmvTranscoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

open class AmvTrimmingPlayerViewModel : ViewModel() {
    lateinit var playerModel: PlayerModel
    lateinit var controlPanelModel:TrimmingControlPanelModel

    private var sourceFile = MutableStateFlow<AmvFile?>(null)
    private var outputFile = MutableStateFlow<AmvFile?>(null)
    private val transcoder = MutableStateFlow<IAmvTranscoder?>(null)
    val isTranscodingNow = transcoder.map { it!=null }.stateIn(viewModelScope, SharingStarted.Lazily, false)
//    val isReadyToTransode: StateFlow<Boolean> = combine(sourceFile,outputFile,transcoder,playerModel.isReady) {src,dst,transcoder,ready->
//        src!=null && dst!=null && transcoder!=null && ready
//    }.stateIn(viewModelScope, SharingStarted.Lazily,false)
    val progress = MutableStateFlow(0)
    val remainingTime = MutableStateFlow(-1L)
    val result = MutableStateFlow<AmvResult?>(null)
    val hasError = result.map { it?.hasError?:false }

    fun reset() {
        result.value = null
    }

    open fun prepare(applicationContext: Context) {
        if(!this::playerModel.isInitialized) {
            playerModel = PlayerModel(applicationContext)
            controlPanelModel = TrimmingControlPanelModel(playerModel, 16, 80)
        }
    }

    fun setSourceFile(src:AmvFile) {
        sourceFile.value = src
        playerModel.setVideoSource(AmvPickedUriSource(src.toUri))
    }

    fun setOutputFile(dst:AmvFile) {
        outputFile.value = dst
    }

    val isReadyToTranscode:Boolean
        get() = sourceFile.value != null && outputFile.value != null && transcoder.value != null && playerModel.isReady.value

    fun transcode() {
        if(!isReadyToTranscode) {
            AmvSettings.logger.warn("not prepared yet.")
        }
        reset()
        val transcoder = AmvCascadeTranscoder(sourceFile.value!!, playerModel.context, progress)
        this.transcoder.value = transcoder

        CoroutineScope(Dispatchers.IO).launch {
            try {
                progress.onEach {
                    remainingTime.value = transcoder.remainingTime
                }.launchIn(this)
                result.value = transcoder.transcode(outputFile.value!!, controlPanelModel.trimmingRange ?: AmvClipping.empty)
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        transcoder.value?.cancel()
    }

    private fun close() {
        transcoder.value?.close()
        transcoder.value = null
    }

    override fun onCleared() {
        super.onCleared()
        controlPanelModel.close()
    }

    companion object {
        fun instanceFor(owner: ViewModelStoreOwner, applicationContext: Context):AmvTrimmingPlayerViewModel {
            return ViewModelProvider(owner)[AmvTrimmingPlayerViewModel::class.java].apply { prepare(applicationContext) }
        }
    }
}