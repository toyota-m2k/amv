package com.michael.amvplayer

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.michael.video.v2.AmvTrimmingPlayerView
import com.michael.video.v2.util.AmvFile
import com.michael.video.v2.viewmodel.AmvTrimmingPlayerViewModel
import io.github.toyota32k.bindit.*
import io.github.toyota32k.dialog.broker.pickers.UtFilePickerStore
import io.github.toyota32k.dialog.task.UtGeneralViewModelStoreOwner
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.UtMortalActivity
import kotlinx.coroutines.flow.combine

class TrimmingActivity : UtMortalActivity() {
    companion object {
        val viewModelStoreOwner = UtGeneralViewModelStoreOwner()
        var staticViewModel: AmvTrimmingPlayerViewModel? = null
    }

    fun prepareViewModel():AmvTrimmingPlayerViewModel {
        logger.debug()
        if(staticViewModel==null) {
            staticViewModel = ViewModelProvider(viewModelStoreOwner, ViewModelProvider.AndroidViewModelFactory(application))[AmvTrimmingPlayerViewModel::class.java]
        }
        return staticViewModel!!
    }
    fun disposeViewModel() {
        logger.debug()
        viewModelStoreOwner.release()
        staticViewModel = null
    }

    val viewModel:AmvTrimmingPlayerViewModel get() = staticViewModel!!


    private val binder = Binder()

    val filePickers: UtFilePickerStore = UtFilePickerStore(this)

    val sourceCommand = Command {
        UtImmortalSimpleTask.run("select source") {
            val uri = filePickers.openReadOnlyFilePicker.selectFile("video/*") ?: return@run false
            withOwner {
                (it.asActivity() as? TrimmingActivity)?.viewModel?.setSourceFile(AmvFile(uri,applicationContext))
            }
            true
        }
    }

    val transcodeCommand = Command {
        UtImmortalSimpleTask.run("select source") {
            val uri = filePickers.createFilePicker.selectFile("video.mp4") ?: return@run false
            withOwner {
                (it.asActivity() as? TrimmingActivity)?.viewModel?.apply {
                    setOutputFile(AmvFile(uri, applicationContext))
                    transcode()
                }
            }
            true
        }
        // 次のコードは、うまく行きそうだけど、Activityを保持しない設定でうまくいかない。
        // FilePickerを開くとActivityが閉じられ、AmvTrimmingPlayerViewModel もClearされる。
        // 戻ってきたとき、ViewModelは再構築されるが、Commandは、破棄されたActivityの持ち物なので、古いviewModelを持ってしまっている。
//        UtImmortalTaskManager.immortalTaskScope.launch {
//            val uri = filePickers.createFilePicker.selectFile("video/mp4") ?: return@launch
//            viewModel.setOutputFile(AmvFile(uri, applicationContext))
//            viewModel.transcode()
//        }
    }

    val cancelCommand = Command {
        viewModel.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.trimming_activity)

        prepareViewModel()
//        staticViewModel = AmvTrimmingPlayerViewModel.instanceFor(UtImmortalTaskManager., applicationContext)
        findViewById<AmvTrimmingPlayerView>(R.id.trimmingPlayer).bindViewModel(viewModel.controlPanelModel,binder)

        binder.register(
            sourceCommand.connectViewEx(findViewById(R.id.trimming_source_button)),
            transcodeCommand.connectViewEx(findViewById(R.id.trimming_execute_button)),
            cancelCommand.connectViewEx(findViewById(R.id.trimming_cancel_button)),
            EnableBinding.create(this, findViewById(R.id.trimming_cancel_button), viewModel.isTranscodingNow.asLiveData()),
            EnableBinding.create(this, findViewById(R.id.trimming_execute_button), combine(viewModel.playerModel.isReady, viewModel.isTranscodingNow) { ready, busy->
                ready && !busy}.asLiveData()),
            VisibilityBinding.create(this, findViewById(R.id.progress_panel), viewModel.isTranscodingNow.asLiveData()),
            ProgressBarBinding.create(this, findViewById(R.id.progress_bar), viewModel.progress.asLiveData()),
            TextBinding.create(this, findViewById(R.id.progress_text), combine(viewModel.progress,viewModel.remainingTime) { progress, remaining->
                if(remaining>0) {
                    "$progress % (残り：${remaining/1000}秒)"
                } else {
                    "$progress %"
                }
            }.asLiveData())
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isFinishing) {
            logger.debug()
            disposeViewModel()
        }
    }
}
