package com.michael.video

import android.content.Context
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.DataBindingUtil
import android.media.session.MediaController
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import com.michael.video.databinding.VideoControllerBinding
import android.databinding.BindingAdapter
import android.widget.ImageButton
import android.widget.ImageView


class AmvVideoController @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : ConstraintLayout(context,attrs,defStyleAttr), IAmvVideoController {

    companion object {
        @JvmStatic
        @BindingAdapter("srcCompat")
        fun srcCompat(view: ImageButton, resourceId: Int) {
            view.setImageResource(resourceId)
        }
    }

    private var mBinding : VideoControllerBinding
    private val mBindingParams = BindingParams()
    private lateinit var mPlayer:IAmvVideoPlayer

    /**
     * Binding Data
     */
    inner class BindingParams : BaseObservable() {
        @get:Bindable
        val isPlaying
            get() = playerState == IAmvVideoPlayer.PlayerState.Playing

        var playerState:IAmvVideoPlayer.PlayerState = IAmvVideoPlayer.PlayerState.None
            set(v) {
                if(field != v) {
                    field = v;
                    notifyPropertyChanged(BR.playing)
                }
            }

        val hasPrev : Boolean = false

        val hasNext : Boolean = true
    }

    init {
        mBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.video_controller, this, true)
        mBinding.handlers = this
        mBinding.params = mBindingParams
//        mBinding.backButton.isEnabled = false
//        mBinding.forwardButton.isEnabled = true
//        mBinding.backButton
    }


    fun setVideoPlayer(player:IAmvVideoPlayer) {
        mPlayer = player
        mBindingParams.playerState = player.playerState;
        mPlayer.playerStateChangedListener.add("videoController") { m,state ->
            mBindingParams.playerState = state
        }
    }

    fun onPlayClicked(view: View) {
        when(mPlayer.playerState) {
            IAmvVideoPlayer.PlayerState.Paused -> mPlayer.play()
            IAmvVideoPlayer.PlayerState.Playing -> mPlayer.pause()
            else -> {}
        }
    }
}