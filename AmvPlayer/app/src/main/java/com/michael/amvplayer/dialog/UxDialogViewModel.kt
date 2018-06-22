package com.michael.amvplayer.dialog

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.michael.amvplayer.UiFileSelectionDialog
import com.michael.amvplayer.utils.Stack

enum class UxDlgState {
    INIT, OK, CANCELED
}

class UxDialogViewModel : ViewModel() {
    data class State (var state:UxDlgState, val tag:String?)

    val state = MutableLiveData<State>()

    private val mTagStack = Stack<State>()
    private var mCurrentState : State = State(UxDlgState.INIT,null);

    fun onOpened (newTag: String) {
        mTagStack.push(mCurrentState)
        mCurrentState = State(UxDlgState.INIT, newTag)
        state.value = mCurrentState;
    }

    fun onClosed  (ok: Boolean) : String? {
        val prevState = mTagStack.pop()
        if(null!=prevState) {
            mCurrentState = prevState;
        }
        mCurrentState.state = if(ok) UxDlgState.OK else UxDlgState.CANCELED;
        state.value = mCurrentState;
        return mCurrentState.tag;
    }

//    fun setDialogState(s: UxDlgState) {
//        if(mCurrentState.state != s) {
//            state.value = mCurrentState;
//        }
//    }
}