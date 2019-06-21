/**
 * ビデオプレーヤーのソースクラス
 * File / Uri (with CacheManager)をまとめて扱えるようにするクラス
 * ExoPlayer、m4mなどが、それぞれ異なるソースクラスを持っており、１つの型にまとめられないので、必要最小限のソースクラスとして実装しておく。
 *
 * 複数のオブジェクトから共有する場合は、addRef()を呼んで、キャッシュの解放を防ぐか、
 * clone()を呼んで、新しいインスタンスとしてキャッシュを保持する。
 * いずれの場合も、不要になれば release()を呼んで、キャッシュを解放すること。
 *
 * @author M.TOYOTA 2018.07.27 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */

package com.michael.video

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import java.io.File

interface IAmvSource : Parcelable {
    val error:AmvError

    suspend fun getUriAsync() : Uri?
    suspend fun getFileAsync() : File?

    fun addRef()
    fun release()

    fun invalidate()    // キャッシュを無効化する
}

class AmvFileSource(private val file:File?) : IAmvSource {

    override val error = AmvError()

    override fun equals(other: Any?): Boolean {
        if(super.equals(other)) {
            return true
        }
        if(other is AmvFileSource && other.file==file) {
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        return file?.hashCode() ?: 0
    }

    override suspend fun getUriAsync(): Uri? {
        return Uri.fromFile(file)
    }

    override suspend fun getFileAsync(): File? {
        return file
    }

    override fun addRef() {
        // nothing to do
    }

    override fun release() {
        // nothing to do
    }

    override fun invalidate() {
        // nothing to do
    }

    // region Parcelable
    constructor(parcel: Parcel) : this(parcel.readSerializable() as? File)

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeSerializable(file)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AmvFileSource> {
        override fun createFromParcel(parcel: Parcel): AmvFileSource {
            return AmvFileSource(parcel)
        }

        override fun newArray(size: Int): Array<AmvFileSource?> {
            return arrayOfNulls(size)
        }
    }
    // endregion
}

//@Suppress("unused")
//class AmvSource {
//    val key: String?
//
//    val file: File?
//        get() = mFile
//    val uri: Uri?
//        get() = mCache?.uri
//    val handler = Handler()
//
//    private var mFile:File? = null
//    private var mCache:IAmvCache? = null
//
//    init {
//        if(!Looper.getMainLooper().isCurrentThread) {
//            throw IllegalThreadStateException("AmvCache must be created in main thread.")
//        }
//    }
//    constructor(file:File) {
//        this.mFile = file
//        this.key = null
//    }
//
//    constructor(uri: Uri, key:String?=null) {
//        this.key = key
//        mCache = AmvCacheManager.getCache(uri, key)
//        getFileInternal()
//    }
//
//    private constructor(src:AmvSource) {
//        mFile = src.file
//        key = src.key
//        mCache = src.mCache
//        mCache?.addRef()
//        getFileInternal()
//    }
//
//    private constructor(cache:IAmvCache, key:String) {
//        this.key = key
//        mCache = cache
//        getFileInternal()
//    }
//
//    companion object {
//        fun peekCache(key:String) : AmvSource? {
//            val cache = AmvCacheManager.peekCache(key)
//            return if(null!=cache) {
//                AmvSource(cache, key)
//            } else {
//                null
//            }
//        }
//    }
//
//    private fun getFileInternal(callback:((File?, error:AmvError?)->Unit)?=null) {
//        mCache?.getFile { cache, file ->
//            handler.post {
//                if (null != file) {
//                    mFile = file
//                    callback?.invoke(file, null)
//                } else {
//                    callback?.invoke(null, cache.error.clone())
//                }
//            }
//        }
//    }
//
//    /**
//     * ソースからファイルを取り出す
//     */
//    fun retrieve(callback:(File?,AmvError?)->Unit) {
//        val f = file
//        if(null!=f) {
//            callback(f,null)
//        } else if(null!=uri&&null!=mCache){
//            getFileInternal(callback)
//        } else {
//            throw AmvException("no source")
//        }
//    }
//
//    /**
//     * キャッシュの参照カウンタを上げる
//     */
//    fun addRef() {
//        mCache?.addRef()
//    }
//
//    /**
//     * キャッシュの参照カウンタを下げる
//     */
//    fun release() {
//        mCache?.apply {
//            if(release()<=0) {
//                mCache = null
//            }
//        }
//    }
//
//    /**
//     * 同じキャッシュを参照するインスタンスを複製
//     */
//    fun clone() : AmvSource {
//        return AmvSource(this)
//    }
//
//}