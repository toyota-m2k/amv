package com.michael.amvplayer.dialog

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.os.Bundle
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
    data class State (var state: UxDlgState, val tag:String, var result:Bundle? = null)

    /**
     * ダイアログの状態
     * これを、Activity/Fragment から observeして、ダイアログの終了を監視する
     */
    val state = MutableLiveData<State>()

    // Internals
    private val mTagStack = Stack<State>()
    private var mCurrentState = UxDialogViewModel.State(UxDlgState.INIT, "#")

    /**
     * ダイアログが開かれたときの処理
     */
    fun onOpened (newTag: String) {
        mTagStack.push(mCurrentState)
        mCurrentState = State(UxDlgState.INIT, newTag)
        state.value = mCurrentState
    }

    /**
     * ダイアログが閉じられるときの処理
     * @return 閉じたダイアログの親のダイアログのタグ（ルートダイアログを閉じた場合はnull）
     */
    fun onClosed  (ok: Boolean, result:Bundle?) : String? {
        mCurrentState.state = if(ok) UxDlgState.OK else UxDlgState.CANCELED
        mCurrentState.result = result
        state.value = mCurrentState

        val prevState = mTagStack.pop()
        if(null!=prevState) {
            mCurrentState = prevState
            state.value = mCurrentState
            return mCurrentState.tag
        }
        return null
    }

//    fun setDialogState(s: UxDlgState) {
//        if(mCurrentState.state != s) {
//            state.value = mCurrentState;
//        }
//    }
}