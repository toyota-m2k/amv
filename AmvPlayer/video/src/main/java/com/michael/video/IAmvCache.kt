package com.michael.video

import android.net.Uri
import com.michael.utils.IFuncy2
import java.io.File

/**
 * キャッシュマネージャが管理するキャッシュクラスのi/f定義
 */
interface IAmvCache
{
    /**
     * キャッシュファイルを取得する。
     * @param callback  結果を返すコールバック fn(sender, file)    エラー発生時はfile==null / sender.error でエラー情報を取得
     */
    // for Kotlin
    fun getFile(callback: (IAmvCache,File?)->Unit)

    // for Java i/f
    interface IGotFileCallback {
        fun onGotFile(cache:IAmvCache, file:File?)
    }
    fun getFile(callback: IGotFileCallback)

    /**
     * エラー情報
     */
    val error: AmvError

    /**
     * 参照カウンタを下げる・・・キャッシュを解放（CacheManagerによる削除）可能な状態にする
     */
    fun release()

    /**
     * 参照カウンタを上げる
     */
    fun addRef()

    /**
     * 参照カウンタの値を取得
     */
    val refCount: Int

    /**
     * キャッシュを無効化する
     */
    fun invalidate()

    /**
     * 呼び出し時点で取得しているキャッシュファイルを取得
     * ダウンロード中、または、Invalidateされているときは、nullを返す。
     */
    val cacheFile : File?

    /**
     * ターゲットの URI を取得
     */
    val uri : Uri
}