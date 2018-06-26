package com.michael.amvplayer.dialog

import android.app.Dialog
import android.databinding.BaseObservable
import android.databinding.Bindable
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.FragmentActivity
import android.view.View
import android.view.ViewGroup
import com.michael.amvplayer.BR
import com.michael.amvplayer.R
import com.michael.amvplayer.databinding.UxFileDialogBinding
import com.michael.amvplayer.utils.Packing
import com.michael.amvplayer.utils.UnPacker
import com.michael.amvplayer.utils.unpack
import org.parceler.Parcel
import java.io.File

class UxFileDialog : UxDialogBase(), UxFileListView.IOnFileSelected {

    // Binding
    private lateinit var binding: UxFileDialogBinding;

    /**
     * ダイアログの結果をBundleにして返す
     */
    override fun getResult(): Bundle? {
        return status.pack();
    }

    /**
     * 列挙するファイル/ディレクトリの指定
     */
    enum class ListType {
        INVALID {       // 無効値
            override fun file(): Boolean = false
            override fun directory(): Boolean = false
        },
        FILES {         // ファイルだけを列挙
            override fun file(): Boolean = true
            override fun directory(): Boolean = false
        },
        DIRECTORIES {   // ディレクトリだけを列挙
            override fun file(): Boolean = false
            override fun directory(): Boolean = true
        },
        ALL {           // ファイルとディレクトリを列挙
            override fun file(): Boolean = true
            override fun directory(): Boolean = true
        };

        abstract fun file() : Boolean
        abstract fun directory() : Boolean
    }

    /**
     * 操作の目的を区別（これに合わせてダイアログの構成を調整する）
     */
    enum class Purpose {
        INVALID,
        SELECT_FILE,                // ファイル選択用
        SELECT_DIR,                 // ディレクトリ選択用（ディレクトリ作成は不可）
        SELECT_OR_CREATE_DIR,       // ディレクトリ選択用（ディレクトリ作成可）
        CREATE_FILE,                // ファイル作成用
    }

    /**
     * ダイアログの初期値（Showする前にargumentsにセットする）
     * ・・・ダイアログの操作で変更されない
     */
    @Parcel
    data class Args (
            val type:ListType,
            val purpose:Purpose,
            val extensions: Array<String>?,
            val initialDir: File?
                ) : Packing {
        constructor() : this(ListType.INVALID, Purpose.INVALID, null, null)
        fun pack(to:Bundle?=null) = pack(defKey, to)
        companion object : UnPacker<Args>("UxFileDialog.Args")

        val isForDirectory: Boolean
            get() = purpose == Purpose.SELECT_DIR || purpose == Purpose.SELECT_OR_CREATE_DIR;
    }

    /**
     * ダイアログの状態・・・ダイアログの操作によって変化する値
     *  - ビューにバインド
     *  - 利用者(Activity）へ結果として返す
     */
    @Parcel
    class Status : BaseObservable(), Packing {
        fun pack(to:Bundle?=null) = pack(defKey, to)
        companion object : UnPacker<Status>("UxFileDialog.Status")

        @get:Bindable
        var baseDir:File? = null
            set(value) {
                field = value
                notifyPropertyChanged(BR.baseDir)
            }

        @get:Bindable
        var file:File? = null
            set(value) {
                field = value
                notifyPropertyChanged(BR.file)
            }

        @get:Bindable("baseDir")
        val dir:String
            get() = baseDir?.path ?: ""

        @get:Bindable("file")
        val fileName:String
            get() = file?.name ?: ""
    }

    private lateinit var status : Status
    private lateinit var args : Args



    init {
        setTitle("Open File");
    }

    override fun createDialogComponentView(dlg: Dialog, container: ViewGroup, savedInstanceState: Bundle?): View? {
//        val a = Args(ListType.DIRECTORIES, Purpose.SELECT_DIR, /*arrayOf<String>("a", "b")*/ null, Environment.getExternalStorageDirectory())
//        val bundle = a.pack();
//        val b = Args.unpack(bundle);
//        val c = bundle.unpack<Args>(Args.defKey)
//        Log.d("Amv", "Type=${b.type} / Purpose:${b.purpose}")

        val inflater = activity?.layoutInflater;
        if(null==inflater) {
            return null;
        }

        args = arguments?.unpack(Args.defKey) ?: Args(ListType.ALL, Purpose.SELECT_FILE, null, null)
        if(null!=savedInstanceState) {
            status = Status.unpack(savedInstanceState);
        } else {
            status = Status()
        }
        if(null == status.baseDir) {
            status.baseDir = args.initialDir?: Environment.getExternalStorageDirectory();
        }


        binding = DataBindingUtil.inflate(inflater, R.layout.ux_file_dialog, null, false )
        binding.status = this.status
        binding.args = this.args
        binding.fileView.also {
            it.baseDir = status.baseDir
            it.filter = UxFileListView.ExtFilter(args.type, args.isForDirectory, args.extensions)
            it.selectListener = this
        }
        return binding.root;
    }


    override fun onFileSelected(file: File): Boolean {
        if (file.isDirectory()) {
            status.baseDir = file
            // mHistory.push(mBaseDir)
            // updateButtons()
        } else {
            status.file = file
            if(args.purpose==Purpose.SELECT_FILE) {
                close(true, status.pack())
                return false
            }
        }
        return true
    }

    companion object {

        @JvmOverloads fun selectFile(activity:FragmentActivity, tag:String="FileSelect", initialDir:File?=null, extensions: Array<String>?=null) {
            val dlg = UxFileDialog()
            dlg.arguments = Args(ListType.ALL, Purpose.SELECT_FILE, extensions, initialDir).pack()
            dlg.show(activity, tag)
        }
    }

}

