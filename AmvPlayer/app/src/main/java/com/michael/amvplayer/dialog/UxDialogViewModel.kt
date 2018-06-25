package com.michael.amvplayer.dialog

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.michael.amvplayer.utils.Stack

enum class UxDlgState {
    INIT, OK, CANCELED
}

/**
 * ダイアログデータを保持するクラス
 * （Activity/Fragmentと共有するデータ）
 */
class UxDialogViewModel : ViewModel() {
    /**
     * ダイアログの状態管理用データクラス
     */
    data class State (var state: UxDlgState, val tag:String?)

    /**
     * ダイアログの状態
     * これを、Activity/Fragment から observeして、ダイアログの終了を監視する
     */
    val state = MutableLiveData<State>()

    // Internals
    private val mTagStack = Stack<State>()
    private var mCurrentState = UxDialogViewModel.State(UxDlgState.INIT, null)

    /**
     * ダイアログが開かれたときの処理
     */
    fun onOpened (newTag: String) {
        mTagStack.push(mCurrentState);
        mCurrentState = State(UxDlgState.INIT, newTag);
        state.value = mCurrentState;
    }

    /**
     * ダイアログが閉じられるときの処理
     */
    fun onClosed  (ok: Boolean) : String {
        val current = mCurrentState;
        mCurrentState.state = if(ok) UxDlgState.OK else UxDlgState.CANCELED;
        state.value = mCurrentState;

        val prevState = mTagStack.pop()
        if(null!=prevState) {
            mCurrentState = prevState;
            state.value = mCurrentState;
        }
        return current.tag!!;
    }

//    fun setDialogState(s: UxDlgState) {
//        if(mCurrentState.state != s) {
//            state.value = mCurrentState;
//        }
//    }
}