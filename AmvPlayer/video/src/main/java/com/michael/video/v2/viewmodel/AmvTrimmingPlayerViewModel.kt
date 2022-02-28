package com.michael.video.v2.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.michael.video.v2.models.PlayerModel
import com.michael.video.v2.models.TrimmingControlPanelModel

open class AmvTrimmingPlayerViewModel : ViewModel() {
    lateinit var playerModel: PlayerModel
    lateinit var controlPanelModel:TrimmingControlPanelModel

    open fun prepare(applicationContext: Context) {
        if(!this::playerModel.isInitialized) {
            playerModel = PlayerModel(applicationContext)
            controlPanelModel = TrimmingControlPanelModel(playerModel, 16, 80)
        }
    }

    companion object {
        fun instanceFor(owner: ViewModelStoreOwner, applicationContext: Context):AmvTrimmingPlayerViewModel {
            return ViewModelProvider(owner)[AmvTrimmingPlayerViewModel::class.java].apply { prepare(applicationContext) }
        }
    }
}