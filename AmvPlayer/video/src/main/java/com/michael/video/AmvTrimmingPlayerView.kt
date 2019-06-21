/**
 * トリミング操作ビュー
 * （AmvExoVideoPlayer + AmvTrimmingPlayerView)
 *
 * @author M.TOYOTA 2018.07.26 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.michael.utils.FuncyListener1
import com.michael.video.viewmodel.AmvTranscodeViewModel
import kotlinx.android.synthetic.main.trimming_player.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class AmvTrimmingPlayerView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // region Internals

    private inner class Controls {
        val player: AmvExoVideoPlayer by lazy {
            findViewById<AmvExoVideoPlayer>(R.id.trp_videoPlayer)
        }
        val controller: AmvTrimmingController by lazy {
            findViewById<AmvTrimmingController>(R.id.trp_trimmingController)
        }
        val progressLayer: ConstraintLayout by lazy {
            findViewById<ConstraintLayout>(R.id.trp_progressLayer)
        }
        val progressBar: ProgressBar by lazy {
            findViewById<ProgressBar>(R.id.trp_progressBar)
        }
        val message: TextView by lazy {
            findViewById<TextView>(R.id.trp_message)
        }
        val cancelButton: Button by lazy {
            findViewById<Button>(R.id.trp_cancelButton)
        }

    }
    private val controls = Controls()

    private val videoPlayer : IAmvVideoPlayer
        get() = controls.player

    private val videoController : IAmvVideoController
        get() = controls.controller

    private val mViewModel: AmvTranscodeViewModel?
//    private var mNotified = false   // onCompletedが複数回呼ばれるので、onTrimmingCompletedListenerの通知を1回に限定するためのフラグ

    init {
        LayoutInflater.from(context).inflate(R.layout.trimming_player, this)

        AmvStringPool[R.string.cancel]?.apply {
            trp_cancelButton.text = this
        }

        val sa = context.theme.obtainStyledAttributes(attrs,R.styleable.AmvExoVideoPlayer,defStyleAttr,0)
        try {
            if(!sa.getBoolean(R.styleable.AmvTrimmingPlayerView_showCancelButton, false)) {
                controls.cancelButton.visibility = View.INVISIBLE
            }
        } finally {
            sa.recycle()
        }
        videoController.setVideoPlayer(videoPlayer)

        mViewModel = AmvTranscodeViewModel.registerTo(this, this::onProgress, this::onTrimmingCompleted)

        // Transcode/Trimming 中のキャンセル
        controls.cancelButton.setOnClickListener {
            cancel()
        }

        val vm = mViewModel
        if(null!=vm && vm.isBusy) {
            controls.progressLayer.visibility = View.VISIBLE
            onProgress(vm.progress.value?:0f)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val sliderHeight = max(h - controls.controller.controllerHeight, context.dp2px(50)).toFloat() // measuredHeight // + context.dp2px(16)
        val sliderWidth = w - controls.controller.extentWidth
        controls.player.setLayoutHint(FitMode.Inside, sliderWidth, sliderHeight)
    }


    // endregion

    // region Progress

    /**
     * 進捗表示
     */
    @SuppressLint("SetTextI18n")
    private fun onProgress(progress:Float) {
        var percent = (progress*100).roundToInt()
        if(percent>100) {
            percent %= 100
        }
        if(controls.progressBar.progress!=percent) {
            controls.progressBar.progress = percent
            controls.message.text = "$percent %"
        }
    }

    /**
     * Trimming/Transcode 完了時の処理
     */
    private fun onTrimmingCompleted(result:Boolean, @Suppress("UNUSED_PARAMETER") error:AmvError?) {
        controls.controller.resumeFrameExtraction()
        if (result) {
            // 成功したら、進捗表示を消す
            controls.progressLayer.visibility = View.GONE
            onTrimmingCompletedListener.apply {
                invoke(this@AmvTrimmingPlayerView)
                reset()
            }
        } else {
            // 失敗したら、エラーメッセージを表示
            controls.message.text = /*error?.message ?: */ AmvStringPool[R.string.error] ?: context.getString(R.string.error)
        }
    }

    // endregion

    // region Event Listener

    val onTrimmingCompletedListener = FuncyListener1<AmvTrimmingPlayerView, Unit>()

    // api for java
    interface ITrimmingCompletedHandler {
        fun onTrimmingCompleted(sender: AmvTrimmingPlayerView)
    }

    @Suppress("unused")
    fun setOnTrimmingCompletedListener(listener: ITrimmingCompletedHandler) {
        onTrimmingCompletedListener.set(listener::onTrimmingCompleted)
    }

    // endregion

    // region Public API's

    val isBusy:Boolean
        get() = mViewModel?.isBusy ?: false

    val error: AmvError
        get() = mViewModel?.status?.value?.error ?: AmvError()

    var source:File? = null
        set(v) {
            field = v
            controls.player.setSource(AmvFileSource(v), false, 0)
        }

    /**
     * 後始末
     * ViewModelに保持しているフレームサムネイルリストをクリアする
     */
    fun dispose() {
        controls.controller.dispose()
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val isTrimmed: Boolean
        get() = controls.controller.isTrimmed

    @Suppress("MemberVisibilityCanBePrivate")
    val trimmingRange: IAmvVideoPlayer.Clipping
        get() = controls.controller.trimmingRange


    /**
     * トランスコード or トリミングを開始
     */
    fun startTrimming(output:File) : Boolean {
        val vm = mViewModel
        val input = source
        if (null == vm || null == input || !trimmingRange.isValid && !vm.isBusy) {
            return false
        }

        controls.controller.pauseFrameExtraction()
        controls.progressLayer.visibility = View.VISIBLE
        if (!isTrimmed) {
            vm.transcode(input, output, context)
        } else {
            vm.truncate(input, output, trimmingRange.start, trimmingRange.end, context)
        }
        return true
    }

    fun cancel() : Boolean {
        val vm = mViewModel
        if(null!=vm && vm.isBusy) {
            vm.cancel()
            controls.progressLayer.visibility = View.GONE
            controls.controller.resumeFrameExtraction()
            return true
        }
        return false
    }

    // endregion
}