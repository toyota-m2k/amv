package com.michael.video.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Handler
import androidx.fragment.app.FragmentActivity
import android.view.View
import com.michael.utils.UtLogger
import com.michael.video.AmvError
import com.michael.video.AmvTranscoder
import com.michael.video.getActivity
import java.io.File

class AmvTranscodeViewModel : ViewModel() {

    // region Live Data

    val progress = MutableLiveData<Float>()
    
    val status = MutableLiveData<Status>()

    val handler = Handler()

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
    private var mTranscoder : AmvTranscoder? = null

    init {
        progress.value = 0f
        status.value = mStatus
    }


    // region Publics


    val isBusy
        get() = mTranscoder != null

    fun cancel() {
        mTranscoder?.cancel()
        mTranscoder?.dispose()
        mTranscoder = null
    }

    fun transcode(input: File, output:File, context: Context):Boolean {
        if(isBusy) {
            return false
        }
        createTranscoder(input,context)?.transcode(output) ?: return false
        return true
    }

    fun truncate(input: File, output:File, start:Long, end:Long, context:Context):Boolean {
        if(isBusy) {
            return false
        }
        createTranscoder(input,context)?.truncate(output, start, end) ?: return false
        return true
    }

    private fun createTranscoder(input:File, context:Context) : AmvTranscoder? {
        mStatus.reset()
        try {
            mTranscoder = AmvTranscoder(input, context).apply {
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
                        mStatus.reset()

                        t.completionListener.reset()
                        t.progressListener.reset()
                    }
                }
            }
        } catch(e:Throwable) {
            UtLogger.stackTrace(e,"Cannot create transcoder.")
            mTranscoder = null
        }
        return mTranscoder
    }


    companion object {
        @Suppress("MemberVisibilityCanBePrivate")
        fun registerTo(activity: androidx.fragment.app.FragmentActivity, onProgress: (Float) -> Unit, onCompleted:(Boolean, AmvError?)->Unit): AmvTranscodeViewModel {
            return ViewModelProviders.of(activity).get(AmvTranscodeViewModel::class.java).apply {
                progress.observe(activity, Observer<Float> { p->
                    if(null!=p) {
                        onProgress(p)
                    }
                })
                status.observe(activity, Observer<Status> { s->
                    if(s!=null) {
                        if(s.completed) {
                            onCompleted(s.result, s.error)
                        }
                    }
                })
            }
        }

        /**
         * ビューをオブザーバーとして登録する
         */
        fun registerTo(view: View, onProgress: (Float) -> Unit, onCompleted:(Boolean,AmvError?)->Unit): AmvTranscodeViewModel? {
            val activity = view.getActivity() as? androidx.fragment.app.FragmentActivity
            return if(null!=activity) {
                registerTo(activity, onProgress, onCompleted)
            } else {
                null
            }
        }
    }
}