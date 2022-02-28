package com.michael.amvplayer

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.michael.video.AmvPickedUriSource
import com.michael.video.v2.AmvPlayerUnitView
import com.michael.video.v2.models.FullControlPanelModel
import com.michael.video.v2.models.PlayerModel
import com.michael.video.v2.viewmodel.AmvPlayerUnitViewModel
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.BoolConvert
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.bindit.VisibilityBinding
import io.github.toyota32k.utils.ApplicationViewModelStoreOwner
import io.github.toyota32k.utils.lifecycleOwner

class PlayerActivity : AppCompatActivity() {
    lateinit var viewModel: AmvPlayerUnitViewModel
    lateinit var playerUnitView: AmvPlayerUnitView

    val playerModel: PlayerModel get() = viewModel.playerModel

    val binder = Binder()
    val commandExpand = Command(this::onExpand)
    val commandReduce = Command(this::onReduce)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        viewModel = AmvPlayerUnitViewModel.primaryInstance(ApplicationViewModelStoreOwner, applicationContext)
        binder.register(
            commandExpand.connectViewEx(findViewById(R.id.expandButton)),
            commandReduce.connectViewEx(findViewById(R.id.reduceButton)),
            viewModel.controlPanelModel.commandCloseFullscreen.connectViewEx(findViewById(R.id.closeButton)),
            VisibilityBinding.create(lifecycleOwner()!!, findViewById(R.id.closeButton), viewModel.isOwner.asLiveData(), BoolConvert.Inverse, VisibilityBinding.HiddenMode.HideByGone)
        )
        playerUnitView = findViewById<AmvPlayerUnitView>(R.id.playerUnitView)
        playerUnitView.bindViewModel(viewModel, binder)
        viewModel.attachView(playerUnitView.player)

        if(savedInstanceState==null) {
            val s = intent.getParcelableExtra<Uri>("source")
            playerModel.setVideoSource(AmvPickedUriSource(s as Uri), null)
        }
    }

    fun onExpand(view: View?) {
        playerUnitView.playerWidth = (playerUnitView.playerWidth * 1.1f).toInt()
    }
    fun onReduce(view: View?) {
        playerUnitView.playerWidth = (playerUnitView.playerWidth / 1.1f).toInt()
    }
}