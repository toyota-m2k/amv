package com.michael.amvplayer.dialog

import android.app.Dialog
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.DataBindingUtil
import android.os.Bundle
import android.os.Environment
import androidx.fragment.app.FragmentActivity
import android.view.View
import android.view.ViewGroup
import com.michael.amvplayer.BR
import com.michael.amvplayer.R
import com.michael.amvplayer.databinding.UxFileDialogBinding
import java.io.File
import java.util.*

class UxFileDialog : UxDialogBase(), UxFileListView.IOnFileSelected {

    // Binding
    private lateinit var binding: UxFileDialogBinding

    /**
     * ダイアログの結果をBundleにして返す
     */
    override fun getResult(): Bundle? {
        return status.pack()
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
    data class Args constructor (
            val type:ListType,
            val purpose:Purpose,
            val extensions: Array<String>?,
            val initialDir: File?,
            val title: String?
        ) {
        companion object {
            const val KEY_TYPE = "type";
            const val KEY_PURPOSE = "purpose"
            const val KEY_EXTENSIONS = "extensions"
            const val KEY_INITIAL_DIR = "initialDir"
            const val KEY_TITLE = "title"

            fun unpack(bundle:Bundle?):Args {
                return Args(
                        (bundle?.getSerializable(KEY_TYPE) ?: ListType.ALL) as ListType,
                        (bundle?.getSerializable(KEY_PURPOSE) ?: Purpose.SELECT_FILE) as Purpose,
                        bundle?.getStringArray(KEY_EXTENSIONS),
                        bundle?.getSerializable(KEY_INITIAL_DIR) as? File?,
                        bundle?.getString(KEY_TITLE))
            }
        }

        fun pack():Bundle {
            return Bundle().apply {
                putSerializable(KEY_TYPE, type)
                putSerializable(KEY_PURPOSE, purpose)
                putStringArray(KEY_EXTENSIONS, extensions)
                putSerializable(KEY_INITIAL_DIR, initialDir)
                putString(KEY_TITLE, title)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Args

            if (type != other.type) return false
            if (purpose != other.purpose) return false
            if (!Arrays.equals(extensions, other.extensions)) return false
            if (initialDir != other.initialDir) return false
            if (title != other.title) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type.hashCode()
            result = 31 * result + purpose.hashCode()
            result = 31 * result + (extensions?.let { Arrays.hashCode(it) } ?: 0)
            result = 31 * result + (initialDir?.hashCode() ?: 0)
            result = 31 * result + (title?.hashCode() ?: 0)
            return result
        }

        val isForDirectory: Boolean
            get() = purpose == Purpose.SELECT_DIR || purpose == Purpose.SELECT_OR_CREATE_DIR
    }

    /**
     * ダイアログの状態・・・ダイアログの操作によって変化する値
     *  - ビューにバインド
     *  - 利用者(Activity）へ結果として返す
     */
    class Status : BaseObservable() {
        companion object {
            const val KEY_ROOT = "UxFileDialog_Status"

            const val KEY_BASEDIR: String = "BaseDir"
            const val KEY_FILE: String = "File"

            @JvmStatic
            fun unpack(bundle:Bundle?) : Status {
                return Status().apply {
                    baseDir = bundle?.getSerializable(KEY_BASEDIR) as? File?
                    file = bundle?.getSerializable(KEY_FILE) as? File?
                }
            }
        }
        fun pack():Bundle {
            return Bundle().apply {
                putSerializable(KEY_BASEDIR, baseDir)
                putSerializable(KEY_FILE, file)
            }
        }

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


    override fun onInitBaseDialog(args: BaseDialogArgs) {
//        args.title = "File Selection"
    }

    override fun createDialogComponentView(dlg: Dialog, container: ViewGroup, savedInstanceState: Bundle?): View? {
//        val a = Args(ListType.DIRECTORIES, Purpose.SELECT_DIR, /*arrayOf<String>("a", "b")*/ null, Environment.getExternalStorageDirectory())
//        val bundle = a.pack();
//        val b = Args.unpack(bundle);
//        val c = bundle.unpack<Args>(Args.defKey)
//        Log.d("Amv", "Type=${b.type} / Purpose:${b.purpose}")

        val inflater = activity?.layoutInflater ?: return null

        args = Args.unpack(arguments)

        when(args.purpose) {
            Purpose.SELECT_FILE -> {
                baseDialogArgs.okVisibility = false
                baseDialogArgs.cancelText = resources.getString(R.string.close)
                baseDialogArgs.title = if (args.title != null) args.title!! else resources.getString(R.string.file_select_dlg_title)
            }
            Purpose.CREATE_FILE -> {
                baseDialogArgs.title = if (args.title != null) args.title!! else resources.getString(R.string.file_create_dlg_title)
            }
            Purpose.SELECT_DIR, Purpose.SELECT_OR_CREATE_DIR -> {
                baseDialogArgs.title = if (args.title != null) args.title!! else resources.getString(R.string.folder_select_dlg_title)
            }
            else -> return null
        }


        status = Status.unpack(savedInstanceState?.getBundle(Status.KEY_ROOT))

        if(null == status.baseDir) {
            status.baseDir = args.initialDir?: Environment.getExternalStorageDirectory()
        }


        binding = DataBindingUtil.inflate(inflater, R.layout.ux_file_dialog, null, false )
        binding.status = this.status
        binding.args = this.args
        binding.fileView.also {
            it.baseDir = status.baseDir
            it.filter = UxFileListView.ExtFilter(args.type, args.isForDirectory, args.extensions)
            it.selectListener = this
        }
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(Status.KEY_ROOT, status.pack())
    }

    override fun onFileSelected(file: File): Boolean {
        if (file.isDirectory) {
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

        @JvmOverloads
        @JvmStatic
        fun selectFile(activity: androidx.fragment.app.FragmentActivity, tag:String="FileSelect", initialDir:File?=null, extensions: Array<String>?=null, title:String?=null) {
            val dlg = UxFileDialog()
            dlg.arguments = Args(ListType.ALL, Purpose.SELECT_FILE, extensions, initialDir, title).pack()
            dlg.show(activity, tag)
        }
    }

}

