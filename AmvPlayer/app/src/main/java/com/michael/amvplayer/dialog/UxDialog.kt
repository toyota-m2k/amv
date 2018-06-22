package com.michael.amvplayer.dialog

import android.app.Dialog
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentTransaction
import android.view.View

abstract class UxDialog : DialogFragment() {

    protected var mViewModel: UxDialogViewModel? = null;

    abstract fun createContentView(dlg:Dialog) : View;

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mViewModel = ViewModelProviders.of(this.activity!!).get(UxDialogViewModel::class.java)
        return Dialog(getActivity(), this.theme).apply {
            setContentView(createContentView(this));
        }
    }

    /**
     * ダイアログを閉じる (ok/cancel時の処理）
     */
    protected fun close(state:Boolean) {
        val activity = this.activity
        val viewModel = this.mViewModel
        if(null!=activity && null!=viewModel) {
            val tag = viewModel.onClosed(state)
            if(null!=tag) {
                val dlg = fragmentManager?.findFragmentByTag(tag) as? DialogFragment
                dlg?.dialog?.show()
            }
        }
        this.dialog.dismiss()
    }

    /**
     * このメソッドは直接呼んではならない
     */
    override fun show(manager: FragmentManager?, tag: String?) {
        assert(false);
        super.show(manager, tag)
    }

    /**
     * このメソッドは直接呼んではならない
     */
    override fun show(transaction: FragmentTransaction?, tag: String?): Int {
        assert(false);
        return super.show(transaction, tag)
    }

    /**
     * ダイアログを表示する
     */
    protected fun show(activity: FragmentActivity, tag: String) {
        val viewModel = ViewModelProviders.of(activity).get(UxDialogViewModel::class.java)
        viewModel.onOpened(tag)
        super.show(activity.supportFragmentManager, tag)
    }

    /**
     * サブダイアログを表示する
     */
    protected fun showSubDialog(tag: String, hideCurrent: Boolean = true) {
        val activity = this.activity;
        if(null!=activity) {
            if(hideCurrent) {
                this.dialog.hide();
            }
            this.show(activity, tag);
        }
    }
}