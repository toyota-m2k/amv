package com.michael.amvplayer.dialog

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

import com.michael.amvplayer.R

import java.io.File
import java.io.FileFilter
import java.util.ArrayList

/**
 * @author M.TOYOTA 13/02/19 Created.
 * @author Copyright (C) 2012 MetaMoJi Corp. All Rights Reserved.
 */
class UxFileListView : ListView {

    // Fields
    /**
     * 対象ディレクトリを取得する。
     * @return  File
     */

    /**
     * ファイル列挙対象ディレクトリを指定する。
     */
    var baseDir :File? = null
        set(fileDirectory) {
            field = fileDirectory
            if (null != mFilter) {
                updateList()
            }
        }                       // リスト対象ディレクトリ
    private var mFilter: ExtFilter? = null                   // 拡張子によるファイルフィルター
    private var mSelectListener: IOnFileSelected? = null     // ファイル選択監視リスナー
    private var mShowParentDir = true

    /**
     * 選択されているファイルを取得
     * @return
     */
    val selectedFile: File?
        get() {
            val info = this.selectedItem as? FileInfo
            return info?.file
        }

    constructor(context: Context) : super(context) {
        init()
    }

    @JvmOverloads constructor(context: Context, attrs: AttributeSet, defStyle: Int = 0) : super(context, attrs, defStyle) {
        init()
    }

    /**
     * ファイル選択通知用リスナー
     */
    interface IOnFileSelected {

        /**
         * ファイルまたはディレクトリが選択された
         * @param file  選択されたファイルまたはディレクトリ
         * @return  true: 処理継続 / false: ファイル選択を決定して終了するので継続処理不要（ディレクトリの場合、中に入るかどうかの選択）
         */
        fun onFileSelected(file: File?): Boolean
    }

    /**
     * ファイルリストのタイプ
     */
    object Type {
        const val FILE = 1       // ファイル選択用（ディレクトリの変更禁止）
        const val DIRECTORY = 2  // ディレクトリ選択用
        const val ALL = 3        // ファイル選択用（ディレクトリの変更可能）
        const val ALL_DIR = 7    // ディレクトリ選択用（ファイルも単色表示表示）
    }


    /**
     * コンストラクタから実行される共通の初期化
     */
    private fun init() {
        isScrollingCacheEnabled = false
        itemsCanFocus = true
        onItemClickListener = OnItemClickListener { parent, view, position, id ->
            // 選択ファイルを取得
            val file = getFileAt(position)

            // ファイル選択リスナーを呼びだす
            if (null != mSelectListener) {
                if (!mSelectListener!!.onFileSelected(file)) {
                    return@OnItemClickListener
                }
            }

            // 選択されたのがディレクトリなら、中に入る
            if (null != file) {
                if (file.isDirectory) {
                    baseDir = file
                }
            }
        }
    }

    fun showParentDir(show: Boolean) {
        mShowParentDir = show
        if (null != mFilter && null != baseDir) {
            updateList()
        }
    }

    /**
     * ファイル列挙フィルターをセットする
     * @param extensions 拡張子リスト（nullならすべて）
     * @param type  Type.FILE: ファイルのみ / Type.DIRECTORY: ディレクトリのみ / Type.ALL: ファイルとディレクトリ
     */
    fun setFilter(extensions: Array<String>, type: Int) {
        mFilter = ExtFilter(extensions, type)
        if (null != baseDir) {
            updateList()
        }
    }

    fun setFileSelectedListener(listener: IOnFileSelected) {
        mSelectListener = listener
    }

    /**
     * ファイルリストを更新する
     */
    private fun updateList() {
        // ファイルリスト
        val files = baseDir!!.listFiles(mFilter)
        val listFileInfo = ArrayList<FileInfo>(files?.size ?: 1)
        if (null != files) {
            var i = 0
            val ci = files.size
            while (i < ci) {
                val file = files[i]
                listFileInfo.add(FileInfo(file))
                i++
            }
            listFileInfo.sort()
        }
        // ソート後に親フォルダに戻るパスを先頭に追加
        if (mShowParentDir && mFilter!!.isDirectoryEnabled && null != baseDir!!.parent) {

            /*
             * getExternalStorageDirectoryで取得したパスより上への移動を止める（#212） → 止めない（#391）
             * https://trac.metamoji.net/trac/shirasagi/ticket/212
             * https://trac.metamoji.net/trac/shirasagi/ticket/391
			 * /sdcard
			 * /mnt/sdcard
			 * /mnt/sdcard/external_sd
			 * /storage/sdcard0
			 */
            /*
            File esd = Environment.getExternalStorageDirectory();
        	if (!esd.getPath().equals(mBaseDir.getPath())) {
                listFileInfo.add(0, new FileInfo(new File(mBaseDir.getParent()), true));
        	}
        	*/
            listFileInfo.add(0, FileInfo(File(baseDir!!.parent), true))
        }

        val adapter = FileInfoArrayAdapter(context, listFileInfo)
        this.adapter = adapter
    }

    /**
     * 指定インデックスのファイルを取得
     */
    fun getFileAt(index: Int): File? {
        val info = this.getItemAtPosition(index) as? FileInfo
        return info?.file

    }

    /**
     * ファイル列挙用のフィルタリング
     */
    private inner class ExtFilter
    /**
     * コンストラクタ
     * @param mExtensions 拡張子リスト（nullならすべて）
     * @param mType  Type.FILE: ファイルのみ / Type.DIRECTORY: ディレクトリのみ / Type.ALL: ファイルとディレクトリ
     */
    internal constructor(internal var mExtensions: Array<String>?   // 拡張子リスト
                         , internal var mType: Int) : FileFilter {

        /**
         * ディレクトリは列挙対象か？
         * @return  true/false
         */
        val isDirectoryEnabled: Boolean
            get() = 0 != mType and Type.DIRECTORY

        /**
         * ファイルは列挙対象か？
         * @return  true/false
         */
        val isFileEnabled: Boolean
            get() = 0 != mType and Type.FILE

        /**
         * 列挙対象かどうかを判断
         * @param file  検査するファイルオブジェクト
         * @return  true:列挙する / false:無視する
         */
        override fun accept(file: File): Boolean {
            if (file.isDirectory) {
                return if (file.isHidden) {
                    false
                } else 0 != mType and Type.DIRECTORY
            } else if (0 != mType and Type.FILE) {
                if (null == mExtensions) {
                    return true
                }
                var i = 0
                val ci = mExtensions!!.size
                while (i < ci) {
                    if (file.name.endsWith(mExtensions!![i])) {
                        return true
                    }
                    i++
                }
            }
            return false
        }
    }

    /**
     * ファイル情報クラス
     */
    private inner class FileInfo
    /**
     * コンストラクタ
     * @param file      ファイル
     * @param mParentDirectory   親ディレクトリか（..と表示するか）
     */
    @JvmOverloads constructor(
            /**
             * ファイルオブジェクトを取得
             * @return  ファイル名。ディレクトリの場合は"/"で終わることを保証
             */
            val file: File    // ファイルオブジェクト
            , internal var mParentDirectory: Boolean = false   // ".."を特別扱いするためのフラグ
    ) : Comparable<FileInfo> {

        /**
         * ファイル名を取得
         * @return  ファイル名。ディレクトリの場合は"/"で終わることを保証
         */
        //return ".. (" + mFile.getName() + "/)";
        val name: String
            get() = if (mParentDirectory) {
                resources.getString(R.string.dir_move_parent)
            } else {
                if (file.isDirectory) {
                    file.name + "/"
                } else {
                    file.name
                }
            }

        /**
         * ソートのための比較
         * @param other   比較先
         * @return  int
         */
        override fun compareTo(other: FileInfo): Int {
            if (file.isDirectory && !other.file.isDirectory) {
                return -1
            }
            if (!file.isDirectory && other.file.isDirectory) {
                return 1
            }

            // 大文字・小文字は区別しないで比較する
            var result = file.name.compareTo(other.file.name, ignoreCase = true)
            if (result == 0) {
                // 大文字・小文字を区別しないで一致した場合は、区別して比較する。
                result = file.name.compareTo(other.file.name)
            }
            return result
        }
    }


    /**
     * データソース用アダプタクラス
     */
    private inner class FileInfoArrayAdapter// コンストラクタ
    (context: Context, private val mListFileInfo: ArrayList<FileInfo> // ファイル情報リスト
    ) : ArrayAdapter<FileInfo>(context, -1, mListFileInfo) {

        /**
         * 指定されたFileInfoを取得
         */
        override fun getItem(position: Int): FileInfo? {
            return mListFileInfo[position]
        }

        /**
         * リストの要素（ファイル名）を表示するためのビューを生成して返す
         */
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var result = convertView
            // レイアウトの生成
            if (null == result) {
                val context = context
                // レイアウト
                val layout = LinearLayout(context)
                layout.tag = "layout"
                layout.orientation = LinearLayout.VERTICAL
                layout.setBackgroundResource(0)
                result = layout
                // レイアウト（アイコン＆テキスト）
                val layoutIconText = LinearLayout(context)
                layoutIconText.orientation = LinearLayout.HORIZONTAL
                layoutIconText.setPadding(10, 10, 10, 10)
                layoutIconText.setBackgroundResource(0)
                layout.addView(layoutIconText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f))
                // アイコン
                val imageview = ImageView(context)
                imageview.tag = "icon"
                imageview.setBackgroundResource(0)
                layoutIconText.addView(imageview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
                // テキスト
                val textview = TextView(context)
                textview.tag = "text"
                textview.setBackgroundResource(0)
                textview.setPadding(10, 10, 10, 10)
                //layout.addView(textview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                layoutIconText.addView(textview, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                // 区切り線
                val line = FrameLayout(context)
                line.setBackgroundColor(Color.LTGRAY)
                layout.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
            }

            // 値の指定
            val fileinfo = mListFileInfo[position]
            // アイコン
            val imageview = result.findViewWithTag<View>("icon") as ImageView
            if (!fileinfo.file.isDirectory) {
                imageview.setImageResource(R.mipmap.folder_blank)
            } else if (!fileinfo.mParentDirectory) {
                imageview.setImageResource(R.mipmap.folder)
            } else {
                imageview.setImageResource(R.mipmap.folder_up)
            }
            // テキスト
            val textview = result.findViewWithTag<View>("text") as TextView
            textview.text = fileinfo.name
            if (mFilter!!.mType == Type.ALL_DIR && !fileinfo.file.isDirectory) {
                textview.setTextColor(Color.GRAY)
            } else {
                textview.setTextColor(Color.BLACK)
            }
            return result
        }

        override fun isEnabled(position: Int): Boolean {
            return true
        }
    }

}
