package com.michael.amvplayer

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.asLiveData
import com.michael.video.v2.AmvPlayerUnitView
import com.michael.video.v2.common.AmvPickedUriSource
import com.michael.video.v2.dialog.SelectFrameDialog
import com.michael.video.v2.dialog.TrimmingDialog
import com.michael.video.v2.models.PlayerModel
import com.michael.video.v2.util.AmvFile
import com.michael.video.v2.viewmodel.AmvPlayerUnitViewModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.BoolConvert
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.bindit.VisibilityBinding
import io.github.toyota32k.dialog.broker.pickers.UtFilePickerStore
import io.github.toyota32k.dialog.task.UtImmortalTaskManager
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.utils.ApplicationViewModelStoreOwner
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.launch

class PlayerActivity : UtMortalActivity() {
    private lateinit var viewModel: AmvPlayerUnitViewModel
    private lateinit var playerUnitView: AmvPlayerUnitView

    private val playerModel: PlayerModel get() = viewModel.playerModel

    private val binder = Binder()
    private val commandExpand = Command(this::onExpand)
    private val commandReduce = Command(this::onReduce)
    private val filePickers: UtFilePickerStore = UtFilePickerStore(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        viewModel = AmvPlayerUnitViewModel.primaryInstance(ApplicationViewModelStoreOwner, applicationContext)
        binder.register(
            commandExpand.connectViewEx(findViewById(R.id.expandButton)),
            commandReduce.connectViewEx(findViewById(R.id.reduceButton)),
            Command { selectFrame() }.connectViewEx(findViewById(R.id.selectFrameButton)),
            Command { trimming() }.connectViewEx(findViewById(R.id.trimmingButton)),
            viewModel.controlPanelModel.commandCloseFullscreen.connectViewEx(findViewById(R.id.closeButton)),
            VisibilityBinding.create(lifecycleOwner()!!, findViewById(R.id.closeButton), viewModel.isOwner.asLiveData(), BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone)
        )
        playerUnitView = findViewById(R.id.playerUnitView)
        playerUnitView.bindViewModel(viewModel, binder)
        viewModel.attachView(playerUnitView.player)

        if(savedInstanceState==null) {
            val s = intent.getParcelableExtra<Uri>("source")
            playerModel.setVideoSource(AmvPickedUriSource(s as Uri), null)
        }
    }

    private fun onExpand(@Suppress("UNUSED_PARAMETER") view: View?) {
        playerUnitView.playerWidth = (playerUnitView.playerWidth * 1.1f).toInt()
    }
    private fun onReduce(@Suppress("UNUSED_PARAMETER") view: View?) {
        playerUnitView.playerWidth = (playerUnitView.playerWidth / 1.1f).toInt()
    }

    private fun selectFrame() {
        UtImmortalTaskManager.immortalTaskScope.launch {
            val result = SelectFrameDialog.selectFrame(application, AmvPickedUriSource(intent.getParcelableExtra<Uri>("source") as Uri))
            if(result!=null) {
                val owner = UtImmortalTaskManager.mortalInstanceSource.getOwnerOf(this@PlayerActivity::class.java)
                (owner.asActivity() as? PlayerActivity)?.findViewById<ImageView>(R.id.thumbnail)?.setImageBitmap(result.bitmap)
//                bitmap.recycle()
            }

        }
    }

    private fun trimming() {
        UtImmortalTaskManager.immortalTaskScope.launch {
            val outUri = filePickers.createFilePicker.selectFile("output.mp4", "video/mp4") ?: return@launch
            val inUri = intent.getParcelableExtra<Uri>("source") ?: return@launch
            if(TrimmingDialog.trimming(application, AmvFile(inUri,applicationContext), AmvFile(outUri,applicationContext))) {
                playerModel.setVideoSource(AmvPickedUriSource(outUri))
            }
        }
    }
}