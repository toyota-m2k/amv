package com.michael.video.v2.viewmodel

import android.content.Context
import androidx.lifecycle.*
import com.michael.video.v2.elements.AmvExoVideoPlayer
import com.michael.video.v2.models.FullControlPanelModel
import com.michael.video.v2.models.IPlayerOwner
import com.michael.video.v2.models.PlayerModel
import io.github.toyota32k.utils.lifecycleOwner
import kotlinx.coroutines.flow.*

class AmvPlayerUnitViewModel : ViewModel(), IPlayerOwner {
    lateinit var playerModel: PlayerModel
    lateinit var controlPanelModel: FullControlPanelModel
    override var isPrimary:Boolean = true   // true:メインプレーヤー / false:PinP/Fullscreenプレーヤー
        private set

    lateinit var isOwner: StateFlow<Boolean>

    fun prepareAsPrimary(applicationContext: Context) {
        if(!this::playerModel.isInitialized) {
            playerModel = PlayerModel(applicationContext)
            controlPanelModel = FullControlPanelModel(playerModel, 16, 80)
            controlPanelModel.registerPlayerOwner(this)
            isOwner = controlPanelModel.ownership.map { it==this }.stateIn(viewModelScope, SharingStarted.Eagerly,false)
            isPrimary = true
        }
    }
    fun prepareAsSecondary(primaryModel:FullControlPanelModel?) {
        if(!this::playerModel.isInitialized) {
            if(primaryModel==null) error("no primary model.")
            playerModel = primaryModel.playerModel
            controlPanelModel = primaryModel
            controlPanelModel.registerPlayerOwner(this)
            isOwner = controlPanelModel.ownership.map { it==this }.stateIn(viewModelScope, SharingStarted.Eagerly,false)
            isPrimary = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        controlPanelModel.unregisterPlayerOwner(this)
    }

    fun attachView(view:AmvExoVideoPlayer) {
        controlPanelModel.ownership.map { it == this }.onEach { own->
            view.associatePlayer(own)
        }.launchIn(view.lifecycleOwner()!!.lifecycleScope)

        if(!isPrimary) {
            controlPanelModel.requestOwnership(this)
        }
    }

    companion object {
        fun primaryInstance(appOwner: ViewModelStoreOwner, applicationContext: Context):AmvPlayerUnitViewModel {
            return ViewModelProvider(appOwner)[AmvPlayerUnitViewModel::class.java].apply { prepareAsPrimary(applicationContext) }
        }
        fun secondaryInstance(owner:ViewModelStoreOwner, primaryModel: FullControlPanelModel?):AmvPlayerUnitViewModel {
            return ViewModelProvider(owner)[AmvPlayerUnitViewModel::class.java].apply { prepareAsSecondary(primaryModel) }
        }
    }
}