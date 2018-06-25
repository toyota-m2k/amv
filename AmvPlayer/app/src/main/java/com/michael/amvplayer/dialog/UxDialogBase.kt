package com.michael.amvplayer.dialog

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.databinding.ObservableField
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.michael.amvplayer.R
import com.michael.amvplayer.databinding.UxDialogBaseBinding

/**
 * ux_dialog_base レイアウトを使った UxDialogの実装
 *
 *  このクラスは、ok/cancel/titleを提供するのみで、中身のことは知らない。
 *  createDialogComponentView()をオーバーライドすることにより、ダイアログの中身をカスタマイズできる。
 */
abstract class UxDialogBase : UxDialog() {
    val title = ObservableField<String>()

    protected fun setTitle(title:String) {
        this.title.set(title);
    }

    /**
     * ダイアログの中身となるビューを構築して返すメソッド
     */
    abstract fun createDialogComponentView(dlg:Dialog, container:ViewGroup, savedInstanceState:Bundle?) : View?;

    /**
     * OKボタンが押されたときの処理
     * 必要に応じてオーバーライド
     * @return true: ダイアログを閉じる / false:閉じない
     */
    protected fun onOk() : Boolean {
        return true
    }

    /**
     * Cancelボタンが押されたときの処理
     * 必要に応じてオーバーライド
     * @return true: ダイアログを閉じる / false:閉じない
     */
    protected fun onCanceled() : Boolean {
        return true
    }

    /**
     * ダイアログの中身のビューを構築して返すメソッド
     * （UxDialogの仮想メソッドをオーバーライド）
     */
    override fun createContentView(dlg: Dialog, savedInstanceState: Bundle?): View? {
        val inflater = activity?.layoutInflater;
        if(null==inflater) {
            return null;
        }
        val binding = DataBindingUtil.inflate<UxDialogBaseBinding>(inflater, R.layout.ux_dialog_base, null, false )
        binding.dialogBase = this;

        val view = binding.root;
        val container = binding.dlgContainer;
        val body = createDialogComponentView(dlg, container, savedInstanceState);
        if(null!=body) {
            container.addView(body);
        }
        return view;
//        return null;
    }

    /**
     * OKボタンクリック時の処理（Binding）
     */
    fun onOKClicked(view: View) {
        if(onOk()) {
            close(true)
        }
    }

    /**
     * Cancelボタンクリック時の処理（Binding）
     */
    fun onCancelClicked(view: View) {
        if(onCanceled()) {
            close(false)
        }
    }
}