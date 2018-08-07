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
import android.os.Handler
import java.io.File

@Suppress("unused")
class AmvSource {
    val key: String?

    val file: File?
        get() = mFile
    val uri: Uri?
        get() = mCache?.uri

    private var mFile:File? = null
    private var mCache:IAmvCache? = null

    constructor(file:File) {
        this.mFile = file
        this.key = null
    }

    constructor(uri: Uri, key:String?=null) {
        this.key = key
        mCache = AmvCacheManager.getCache(uri, key)
        getFileInternal()
    }

    private constructor(src:AmvSource) {
        mFile = src.file
        key = src.key
        mCache = src.mCache
        mCache?.addRef()
        getFileInternal()
    }

    private fun getFileInternal(callback:((File?, error:AmvError?)->Unit)?=null) {
        mCache?.getFile { cache, file ->
            Handler().post {
                if (null != file) {
                    mFile = file
                    callback?.invoke(file, null)
                } else {
                    callback?.invoke(null, cache.error.clone())
                }
            }
        }
    }

    /**
     * ソースからファイルを取り出す
     */
    fun retrieve(callback:(File?,AmvError?)->Unit) {
        val f = file
        if(null!=f) {
            callback(f,null)
        } else if(null!=uri&&null!=mCache){
            getFileInternal(callback)
        } else {
            throw AmvException("no source")
        }
    }

    /**
     * キャッシュの参照カウンタを上げる
     */
    fun addRef() {
        mCache?.addRef()
    }

    /**
     * キャッシュの参照カウンタを下げる
     */
    fun release() {
        mCache?.apply {
            if(release()<=0) {
                mCache = null
            }
        }
    }

    /**
     * 同じキャッシュを参照するインスタンスを複製
     */
    fun clone() : AmvSource {
        return AmvSource(this)
    }

}