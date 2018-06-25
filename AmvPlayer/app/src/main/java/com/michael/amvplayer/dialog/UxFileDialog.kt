package com.michael.amvplayer.dialog

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import org.parceler.Parcel
import org.parceler.Parcels

class UxFileDialog : UxDialogBase() {
    enum class ListType {
        INVALID,
        FILES,                      // ファイルだけを列挙
        DIRECTORIES,                // ディレクトリだけを列挙
        ALL;                        // ファイルとディレクトリを列挙
        companion object {
            fun of(name:String) : ListType {
                try {
                    return ListType.valueOf(name);
                } catch(e:IllegalArgumentException) {
                    return INVALID;
                }
            }
        }
    }

    enum class Purpose {
        INVALID,
        SELECT_FILE,                // ファイル選択用
        SELECT_DIR,                 // ディレクトリ選択用（ディレクトリ作成は不可）
        SELECT_OR_CREATE_DIR,       // ディレクトリ選択用（ディレクトリ作成可）
        CREATE_FILE,                // ファイル作成用
    }

    open class Packer<T>(val defKey:String) {
        fun unpack(from:Bundle, key:String?=null) : T {
            val actKey = key ?: defKey;
            return Parcels.unwrap<T>(from.getParcelable(actKey));
        }

        fun T.pack(to:Bundle?=null, key:String?=null) :Bundle{
            val outBundle = to?: Bundle();
            val actKey = key ?: defKey;
            outBundle.putParcelable(actKey, Parcels.wrap(this));
            return outBundle;
        }
    }


    @Parcel
    data class Args(val type:ListType, val purpose:Purpose, val extensions: Array<String>?) {
        private constructor() : this(ListType.INVALID, Purpose.INVALID, null)
        companion object : Packer<Args>("UxFileDialog.Args")
    }

    init {
        setTitle("Open File");
    }

    override fun createDialogComponentView(dlg: Dialog, container: ViewGroup, savedInstanceState: Bundle?): View? {
        val a = Args(ListType.DIRECTORIES, Purpose.SELECT_DIR, arrayOf<String>("a", "b"))
        val bundle = a.pack();
        val b = Args.unpack(bundle);
        Log.d("Amv", "Type=${b.type} / Purpose:${b.purpose}")


        return null;
    }

    companion object {
        fun newInstance() = UxFileDialog()
    }

}

