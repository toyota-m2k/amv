package com.michael.video.viewmodel

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import android.view.View
import androidx.lifecycle.*
import com.michael.video.AmvError
import com.michael.video.AmvSettings
import com.michael.video.getActivity
import com.mihcael.video.transcoder.AmvCascadeTranscoder
import com.mihcael.video.transcoder.IAmvTranscoder
import java.io.File

class AmvTranscodeViewModel : ViewModel() {

    // region Live Data

    val progress = MutableLiveData<Float>()
    
    val status = MutableLiveData<Status>()

    val handler = Handler(Looper.getMainLooper())

    // endregion

    // region internal data

    class Status {
        var completed = false
        var result = false
        var error= AmvError()

        fun reset() {
            completed = false
            result = false
            error.reset()
        }
    }

    private val mStatus = Status()
    private var mTranscoder : IAmvTranscoder? = null

    init {
        progress.value = 0f
        status.value = mStatus
    }


    // region public's


    val isBusy = MutableLiveData<Boolean>(false)

    fun cancel() {
        if(mTranscoder==null) return
        mTranscoder?.cancel()
        mTranscoder?.dispose()
        mTranscoder = null
        progress.value = 0f
        mStatus.reset()
        isBusy.value = false
    }

    fun transcode(input: File, output:File, context: Context):Boolean {
        if(mTranscoder!=null) {
            return false
        }
        createTranscoder(input,context)?.transcode(output) ?: return false
        return true
    }

    fun truncate(input: File, output:File, start:Long, end:Long, context:Context):Boolean {
        if(mTranscoder!=null) {
            return false
        }
        createTranscoder(input,context)?.truncate(output, start, end) ?: return false
        return true
    }

    private fun createTranscoder(input:File, context:Context) : IAmvTranscoder? {
        mStatus.reset()
        try {
            isBusy.value = true
            mTranscoder = AmvCascadeTranscoder(input, context).apply {
                progressListener.set { _, p ->
                    handler.post {
                        progress.value = p
                    }
                }
                completionListener.set { t, r ->
                    handler.post {
                        mStatus.completed = true
                        mStatus.result = r
                        mStatus.error.copyFrom(t.error)
                        status.value = mStatus

                        mTranscoder = null
                        isBusy.value = false
                        mStatus.reset()

                        t.completionListener.reset()
                        t.progressListener.reset()
                    }
                }
            }
        } catch(e:Throwable) {
            logger.stackTrace(e,"Cannot create transcoder.")
            mTranscoder = null
            isBusy.value = false
        }
        return mTranscoder
    }


    companion object {
        val logger = AmvSettings.logger

        fun instanceFor(activity:FragmentActivity):AmvTranscodeViewModel {
            return ViewModelProvider(activity,ViewModelProvider.NewInstanceFactory())[AmvTranscodeViewModel::class.java]
        }

        @Suppress("MemberVisibilityCanBePrivate")
        fun registerTo(activity: FragmentActivity, onProgress: (Float) -> Unit, onCompleted:(Boolean, AmvError?)->Unit): AmvTranscodeViewModel {
            return ViewModelProvider(activity,ViewModelProvider.NewInstanceFactory())[AmvTranscodeViewModel::class.java].apply {
                progress.observe(activity) { p ->
                    if (null != p) {
                        onProgress(p)
                    }
                }
                status.observe(activity) { s ->
                    if (s != null) {
                        if (s.completed) {
                            onCompleted(s.result, s.error)
                        }
                    }
                }
            }
        }

        /**
         * ビューをオブザーバーとして登録する
         */
        fun registerTo(view: View, onProgress: (Float) -> Unit, onCompleted:(Boolean,AmvError?)->Unit): AmvTranscodeViewModel {
            val activity = view.getActivity()
            return if(null!=activity) {
                registerTo(activity, onProgress, onCompleted)
            } else {
                error("no activity")
            }
        }
    }
}