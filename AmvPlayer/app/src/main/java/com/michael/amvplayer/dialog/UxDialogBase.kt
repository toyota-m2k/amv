package com.michael.amvplayer.dialog

import android.app.Dialog
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.michael.amvplayer.BR
import com.michael.amvplayer.R
import com.michael.amvplayer.databinding.UxDialogBaseBinding

/**
 * ux_dialog_base レイアウトを使った UxDialogの実装
 *
 *  このクラスは、ok/cancel/titleを提供するのみで、中身のことは知らない。
 *  createDialogComponentView()をオーバーライドすることにより、ダイアログの中身をカスタマイズできる。
 */
abstract class UxDialogBase : UxDialog() {

    inner class BaseDialogArgs @JvmOverloads constructor(
            title: String = "",
            okString: String? = null,
            okVisibility: Boolean = true,
            okEnabled: Boolean = true,
            cancelString: String? = null,
            cancelVisibility: Boolean = true,
            cancelEnabled: Boolean = true
    ) : BaseObservable() {

        @get:Bindable
        var title: String = title
            set(v) {
                field = v
                notifyPropertyChanged(BR.title)
            }

        @get:Bindable
        var okText:String? = okString
            get() = field ?: resources.getString(android.R.string.ok)
            set(v) {
                field = v
                notifyPropertyChanged(BR.okText)
            }

        @get:Bindable
        var okVisibility : Boolean = okVisibility
            set(v) {
                if(v!=field) {
                    field = v
                    notifyPropertyChanged(BR.okVisibility)
                }
            }

        @get:Bindable
        var okEnabled : Boolean = okEnabled
            set(v) {
                if(v!=field) {
                    field = v
                    notifyPropertyChanged(BR.okEnabled)
                }
            }

        @get:Bindable
        var cancelText:String? = cancelString
            get() = field ?: resources.getString(android.R.string.ok)
            set(v) {
                field = v
                notifyPropertyChanged(BR.cancelText)
            }
        @get:Bindable
        var cancelVisibility : Boolean = cancelVisibility
            set(v) {
                if(v!=field) {
                    field = v
                    notifyPropertyChanged(BR.cancelVisibility)
                }
            }
        @get:Bindable
        var cancelEnabled : Boolean = cancelEnabled
            set(v) {
                if(v!=field) {
                    field = v
                    notifyPropertyChanged(BR.cancelEnabled)
                }
            }
    }

    protected var baseDialogArgs = BaseDialogArgs()

    /**
     *
     */
    protected abstract fun onInitBaseDialog(args:BaseDialogArgs)

    /**
     * ダイアログの中身となるビューを構築して返すメソッド
     */
    protected abstract fun createDialogComponentView(dlg:Dialog, container:ViewGroup, savedInstanceState:Bundle?) : View?

    /**
     * ダイアログの結果をBundleにして返す
     */
    protected abstract fun getResult() : Bundle?

    /**
     * OKボタンが押されたときの処理
     * 必要に応じてオーバーライド
     * @return true: ダイアログを閉じる / false:閉じない
     */
    protected open fun onOk() : Boolean {
        return true
    }

    /**
     * Cancelボタンが押されたときの処理
     * 必要に応じてオーバーライド
     * @return true: ダイアログを閉じる / false:閉じない
     */
    protected open fun onCanceled() : Boolean {
        return true
    }

    /**
     * ダイアログの中身のビューを構築して返すメソッド
     * （UxDialogの仮想メソッドをオーバーライド）
     */
    override fun createContentView(dlg: Dialog, savedInstanceState: Bundle?): View? {
        val inflater = activity?.layoutInflater ?: return null

        onInitBaseDialog(baseDialogArgs)

        val binding = DataBindingUtil.inflate<UxDialogBaseBinding>(inflater, R.layout.ux_dialog_base, null, false )
        binding.args = baseDialogArgs
        binding.dialogBase = this

        val view = binding.root
        val container = binding.dlgContainer
        val body = createDialogComponentView(dlg, container, savedInstanceState)
        if(null!=body) {
            container.addView(body)
        }
        return view
//        return null;
    }

    /**
     * OKボタンクリック時の処理（Binding）
     */
    fun onOKClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        if(onOk()) {
            close(true, getResult())
        }
    }

    /**
     * Cancelボタンクリック時の処理（Binding）
     */
    fun onCancelClicked(@Suppress("UNUSED_PARAMETER") view: View) {
        if(onCanceled()) {
            close(false, null)
        }
    }
}