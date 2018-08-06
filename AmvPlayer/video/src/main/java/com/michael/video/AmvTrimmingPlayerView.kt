package com.michael.video

import android.annotation.SuppressLint
import android.content.Context
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.michael.utils.FuncyListener1
import com.michael.video.viewmodel.AmvTranscodeViewModel
import java.io.File
import kotlin.math.roundToInt

class AmvTrimmingPlayerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private inner class Controls {
        val player: AmvExoVideoPlayer by lazy {
            findViewById<AmvExoVideoPlayer>(R.id.videoPlayer)
        }
        val controller: AmvTrimmingController by lazy {
            findViewById<AmvTrimmingController>(R.id.trimmingController)
        }
        val progressLayer: ConstraintLayout by lazy {
            findViewById<ConstraintLayout>(R.id.progressLayer)
        }
        val progressBar: ProgressBar by lazy {
            findViewById<ProgressBar>(R.id.progressBar)
        }
        val message: TextView by lazy {
            findViewById<TextView>(R.id.message)
        }
        val cancelButton: Button by lazy {
            findViewById<Button>(R.id.cancelButton)
        }

        fun initialize() {
            controller.setVideoPlayer(player)
        }
    }

    private val mControls = Controls()
    private val mViewModel: AmvTranscodeViewModel?
    private var mNotified = false   // onCompletedが複数回呼ばれるので、onTrimmingCompletedListenerの通知を1回に限定するためのフラグ

    val onTrimmingCompletedListener = FuncyListener1<AmvTrimmingPlayerView, Unit>()

    // api for java
    interface ITrimmingCompletedHandler {
        fun onTrimmingCompleted(sender: AmvTrimmingPlayerView)
    }

    @Suppress("unused")
    fun setOnTrimmingCompletedListener(listener: ITrimmingCompletedHandler) {
        onTrimmingCompletedListener.set(listener::onTrimmingCompleted)
    }


    init {
        LayoutInflater.from(context).inflate(R.layout.trimming_player, this)
        mControls.initialize()

        mViewModel = AmvTranscodeViewModel.registerTo(this, this::onProgress, this::onTrimmingCompleted)

        // Transcode/Trimming 中のキャンセル
        mControls.cancelButton.setOnClickListener {
            mViewModel?.cancel()
            mControls.progressLayer.visibility = View.GONE
        }

        val vm = mViewModel
        if(null!=vm && vm.isBusy) {
            mControls.progressLayer.visibility = View.VISIBLE
            onProgress(vm.progress.value?:0f)
        }
    }

    /**
     * 進捗表示
     */
    @SuppressLint("SetTextI18n")
    private fun onProgress(progress:Float) {
        val percent = (progress*100).roundToInt()
        mControls.progressBar.progress = percent
        mControls.message.text = "$percent %"
    }

    /**
     * Trimmning/Transcode 完了時の処理
     */
    private fun onTrimmingCompleted(result:Boolean, error:AmvError?) {
        if (result) {
            // 成功したら、進捗表示を消す
            mControls.progressLayer.visibility = View.GONE
            if (!mNotified) {
                mNotified = true
                onTrimmingCompletedListener.invoke(this)
            }
        } else {
            // 失敗したら、エラーメッセージを表示
            mControls.message.text = error?.message ?: "Faild"
        }
    }

    val videoPlayer : IAmvVideoPlayer
        get() = mControls.player

    val videoController : IAmvVideoController
        get() = mControls.controller

    fun setSource(source: File) {
        mControls.player.setSource(source, false, 0)
    }


    @Suppress("MemberVisibilityCanBePrivate")
    val isTrimmed: Boolean
        get() = mControls.controller.isTrimmed

    @Suppress("MemberVisibilityCanBePrivate")
    val trimmingRange: IAmvVideoPlayer.Clipping
        get() = mControls.controller.trimmingRange


    /**
     * トランスコード or トリミングを開始
     */
    fun startTrimming(output:File) : Boolean {
        val vm = mViewModel
        val input = videoPlayer.source
        if (null == vm || null == input || !trimmingRange.isValid && !vm.isBusy) {
            return false
        }

        mNotified = false
        mControls.progressLayer.visibility = View.VISIBLE
        if (!isTrimmed) {
            vm.transcode(input, output, context)
        } else {
            vm.truncate(input, output, trimmingRange.start, trimmingRange.end, context)
        }
        return true
    }
}