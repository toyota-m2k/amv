package com.michael.amvplayer.dialog

import android.app.Dialog
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import android.view.View

/**
 * OK/Cancelボタンとタイトルを持つダイアログ(DialogFragment派生）の既定クラス
 * 基本動作（show/close）と、サブダイアログチェーンの管理を提供
 *
 * サブクラスでcreateContentView()をオーバーライドすることにより、任意のダイアログを実装可能。
 */
abstract class UxDialog : androidx.fragment.app.DialogFragment() {

    private lateinit var mViewModel: UxDialogViewModel

    /**
     * ダイアログの中身のビューを構築して返すメソッド
     * （サブクラスでオーバーライドすること）
     */
    abstract fun createContentView(dlg:Dialog, savedInstanceState: Bundle?) : View?

    /**
     * ダイアログ構築時の処理
     * Android OSから呼び出される
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mViewModel = ViewModelProviders.of(this.activity!!).get(UxDialogViewModel::class.java)
        return Dialog(activity, this.theme).apply {
            val view = createContentView(this, savedInstanceState)
            if(null!=view) {
                setContentView(view)

            }
        }
    }

    /**
     * ダイアログを閉じる (ok/cancel時の処理）
     * @param state ok:true / cancel:false
     * @param result ダイアログからの戻り値(cancelの場合はnull）
     */
    protected fun close(state:Boolean, result:Bundle?) {
        val activity = this.activity
        val viewModel = this.mViewModel
        if(null!=activity) {
            val tag = viewModel.onClosed(state, result)
            if(null!=tag) {
                val dlg = fragmentManager?.findFragmentByTag(tag) as? androidx.fragment.app.DialogFragment
                dlg?.dialog?.show()
            }
        }
        this.dialog.dismiss()
    }

    /**
     * このメソッドは直接呼んではならない
     */
    override fun show(manager: androidx.fragment.app.FragmentManager?, tag: String?) {
        assert(false)
        super.show(manager, tag)
    }

    /**
     * このメソッドは直接呼んではならない
     */
    override fun show(transaction: androidx.fragment.app.FragmentTransaction?, tag: String?): Int {
        assert(false)
        return super.show(transaction, tag)
    }

    /**
     * ダイアログを表示する
     */
    fun show(activity: androidx.fragment.app.FragmentActivity, tag: String) {
        val viewModel = ViewModelProviders.of(activity).get(UxDialogViewModel::class.java)
        viewModel.onOpened(tag)
        super.show(activity.supportFragmentManager, tag)
    }

    /**
     * サブダイアログを表示する
     * @param tag 新たに開くダイアログのタグ
     * @param hideCurrent サブダイアログを開く前にカレントダイアログを閉じる(true)か、閉じない(false)か
     */
    protected fun showSubDialog(tag: String, hideCurrent: Boolean = true) {
        val activity = this.activity
        if(null!=activity) {
            if(hideCurrent) {
                this.dialog.hide()
            }
            this.show(activity, tag)
        }
    }
}