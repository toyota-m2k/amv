/**
 * Cache Manager
 *
 * @author M.TOYOTA 2018.07.27 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
@file:Suppress("unused")

package com.michael.video

import android.annotation.SuppressLint
import android.net.Uri
import com.michael.utils.Funcies2
import com.michael.utils.Funcy2
import com.michael.utils.IFuncy2
import com.michael.utils.UtLogger
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

object AmvCacheManager {
    private val MAX_CACHE_SIZE = if(BuildConfig.DEBUG) 20L * 1000 * 1000 else 1L * 1000*1000*1000   // 20MB(Debug) / 1GB(Release)
    private const val MAX_CACHE_COUNT = 200

    private const val DEFAULT_MAX_CACHE_COUNT = 10

//    private var mMaxCacheCount = DEFAULT_MAX_CACHE_COUNT
    private var mLock = Object()
    private lateinit var mCacheFolder : File
    private val mCacheList = HashMap<String,IAmvCache>(MAX_CACHE_COUNT)
    private var mSweeping = false

    /**
     * シングルトンの初期化
     * 他の機能を利用する前に1度だけ実行しておく
     */
    @JvmStatic
    fun initialize(folder:File) {
        mCacheFolder = folder
        if(!folder.exists()) {
            folder.mkdir()
        }
    }

    /**
     * キャッシュフォルダを取得
     */
//    val cacheFolder : File
//        get() = mCacheFolder

    /**
     * キーに対応するファイルオブジェクトを取得
     */
    fun getFileForKey(key:String) : File {
        return File(mCacheFolder, key)
    }

    /**
     * URIからキーを作成
     */
    private fun keyFromUri(uri:String) : String {
        val src = uri.toByteArray(Charset.forName("UTF-8"))
        val digMD5 = MessageDigest.getInstance("MD5")
        digMD5.update(src)
        val digSHA1 = MessageDigest.getInstance("SHA1")
        digSHA1.update(digMD5.digest())
        digSHA1.update(src)

        val dig = digSHA1.digest()
        val sb = StringBuilder()
        val formatter = Formatter(sb)
        for (v in dig) {
            formatter.format("%02x", v)
        }
        formatter.close()
        return sb.toString()
    }

    /**
     * ファイルの最終更新日時を現在の日時に変更する
     */
    private fun touch(file:File) {
        file.setLastModified(System.currentTimeMillis())
    }

    /**
     * 新しいキャッシュオブジェクトを作成
     */
    private fun newCache(uri:Uri, key:String) : IAmvCache {
        val file = getFileForKey(key)
        val newCache =
                if(file.exists()) {
                    UtLogger.debug("AmvCacheManager: $key: reuse existing file")
                    touch(file)
                    AmvCache(key, uri, file)
                } else {
                    UtLogger.debug("AmvCacheManager: $key: new")
                    AmvCache(key, uri, null)
                }
        mCacheList[key] = newCache
        return newCache
    }

    /**
     * キャッシュオブジェクトを取得（なければ作成）
     */
    private fun getOrCreateCache(uri:Uri, optionalKey:String?) : IAmvCache {
        synchronized(mLock) {
            val key = optionalKey ?: keyFromUri(uri.toString())
            val cache = mCacheList[key]
            return if(null!=cache) {
                UtLogger.debug("AmvCacheManager: $key: from cache list")
                cache
            } else {
                newCache(uri,key)
            }
        }
    }

    /**
     * キャッシュオブジェクトを取得
     */
    fun getCache(uri:Uri, optionalKey:String?) : IAmvCache {
        val cache = getOrCreateCache(uri, optionalKey)
        cache.addRef()
        sweep()
        return cache
    }

    fun peekCache(key:String) : IAmvCache? {
        synchronized(mLock) {
            return mCacheList[key]?.apply {
                addRef()
            }
        }
    }

    fun putCache(key:String, existingFile:File, preferToMove:Boolean) : Boolean {
            val targetFile = synchronized(mLock) {
                val cache = mCacheList[key]
                if (null != cache) {
                    return false
                }
                val targetFile = File(mCacheFolder, key)
                if (targetFile.exists()) {
                    return false
                }
                try {
                    if (preferToMove) {
                        existingFile.renameTo(targetFile)
                    } else {
                        existingFile.copyTo(targetFile)
                    }
                } catch(e:Throwable) {
                    return false
                }
                targetFile
            }
            val cache = AmvCache(key, null, targetFile)
            touch(targetFile)
            mCacheList[key] = cache
            return true
    }

    data class DiskCapacity(val capacity:Long, val freeSpace:Long)
    private val diskCapacity :DiskCapacity
        @SuppressLint("UsableSpace")
        get() = DiskCapacity(mCacheFolder.totalSpace, min(mCacheFolder.freeSpace, mCacheFolder.usableSpace))

    private fun maxCacheSize(currentTotal:Long):Long {
        val capa = diskCapacity
        var maxCacheSize = MAX_CACHE_SIZE
        maxCacheSize = min(maxCacheSize, capa.capacity*5/100)
        maxCacheSize = min(maxCacheSize, (capa.freeSpace+currentTotal)/10)
        return maxCacheSize
    }

    data class Statistics(val count:Int, val totalSize:Long)
    val cacheStatistics:Statistics
        get() {
            val list = mCacheFolder.listFiles()
            val totalSize = if(list.isNotEmpty()) list.map { it.length() }.reduce { acc, v -> acc + v } else 0
            return Statistics(list.size, totalSize)
        }


    /**
     * 古いキャッシュを削除
     */
    fun sweep(maxCount:Int = MAX_CACHE_COUNT) {
        // 再入防止
        if (mSweeping) {
            return
        }
        mSweeping = true

        try {
            // キャッシュファイル列挙
            val list = mCacheFolder.listFiles()

            // トータルファイルサイズの計算
            var count = list.size
            var totalSize = list.map {it.length()}.reduce {acc, v -> acc+v }
            val maxCacheSize = maxCacheSize(totalSize)
            if (count < maxCount && totalSize < maxCacheSize) {
                return
            }

            // 更新日時(BasicProperty.ItemData）順（昇順）にソート
            list.sortWith( Comparator { left, right ->
                val c = right.lastModified()-left.lastModified()
                when {
                    c<0L->-1
                    c>0L -> 1
                    else -> 0
                }
            })

            // 古いファイルから削除
            synchronized(mLock) {
                for(i in list.size-1 downTo 0) {
                    val file = list[i]
                    val key = file.name
                    val cache = mCacheList[key]
                    if(null==cache || cache.refCount<=0) {
                        UtLogger.debug("AmvCacheManager: $key: remove cache")
                        totalSize -= file.length()
                        count--
                        mCacheList.remove(key)
                        file.delete()
                        if (count < maxCount && totalSize < maxCacheSize) {
                            break
                        }
                    }
                }
            }
        } catch (e:Throwable) {
            UtLogger.error( "AmvCacheManager.sweep\n${e.stackTrace}")
        }
        finally {
            mSweeping = false
        }
    }

    /**
     * キャッシュされているか？
     * (Unitテスト用）
     */
    fun hasCache(uri:Uri, optionKey:String?) : Boolean {
        return synchronized(mLock) {
            val key = optionKey ?: keyFromUri(uri.toString())
            if(mCacheList.containsKey(key)) {
                true
            } else {
                val file = File(mCacheFolder, key)
                file.exists()
            }
        }
    }

    /**
     * キャッシュ数を取得
     * (Unitテスト用）
     */
    val cacheCount:Int
        get() {
            return mCacheFolder.listFiles()?.size ?: 0
        }

    /**
     * すべてのキャッシュを削除
     * 参照カウンタとかお構いなしにやるので、initialize()のタイミング以外で呼び出してはいけない
     * (Unitテスト用）
     */
    fun clearAllCache() {
        try {
            synchronized(mLock) {
                mCacheList.clear()
                val list = mCacheFolder.listFiles()
                if (null != list) {
                    for (f in list) {
                        f.delete()
                    }
                }
            }
        } catch (e:Throwable) {
            UtLogger.error( "AmvCacheManager.clearAllCache\n${e.stackTrace}")
        }
    }

    internal fun removeCache(cache:IAmvCache) {
        try {
            synchronized(mLock) {
                mCacheList.remove(cache.key)
                cache.cacheFile?.delete()
            }
        } catch (e:Throwable) {
            UtLogger.error( "AmvCacheManager.removeCache\n${e.stackTrace}")
        }
    }
}

/**
 * キャッシュファイルの内部表現
 */
private class AmvCache(override val key:String, override val uri:Uri?, existsFile:File?) : IAmvCache{
    private var mFile: File? = null
    private var mInvalidated = false
    private var mRefCount: Int = 0
    private var mLock = Object()
    private var mDownloading : Boolean = false
    private var mDownloadedListener = Funcies2<IAmvCache, File?, Unit>()

    init {
        if(null!=existsFile) {
            mFile = existsFile
        } else if(uri!=null){
            download(uri)
        }
    }

    /**
     * ファイルのダウンロードを開始する
     */
    private fun download(uri:Uri) {
        if(mDownloading) {
            throw AmvException("internal error: download twice")
        }
        mDownloading = true
        mFile = null

        val request = Request.Builder()
                .url(uri.toString())
                .get()
                .build()

        OkHttpClient().newCall(request).enqueue(object : Callback{
            override fun onResponse(call: Call?, response: Response?) {
                error.reset()

                val file = response?.use { res->
                    res.body()?.use { body->
                        body.byteStream().use { inStream ->
                            AmvCacheManager.getFileForKey(key).apply {
                                FileOutputStream(this, false).use { outStream ->
                                    inStream.copyTo(outStream, 128 * 1024) // buffer size 128KB
                                    UtLogger.debug("AmvCacheManager: $key: file created")
                                }
                            }
                        }
                    }
                }
                if(null==file) {
                    onFailure(call, IOException("no body data."))
                    return
                }
                synchronized(mLock) {
                    mDownloading = false
                    mFile = file
                    mDownloadedListener.invoke(this@AmvCache, file)
                    mDownloadedListener.clear()
                }
            }
            override fun onFailure(call: Call?, e: IOException?) {
                if(e!=null) {
                    error.setError(e)
                } else {
                    error.setError("generic error")
                }

                synchronized(mLock) {
                    mDownloading = false
                    mFile = null
                    mDownloadedListener.invoke(this@AmvCache, null)
                    mDownloadedListener.clear()
                }
            }
        })
    }

    override fun getFile(callback: (IAmvCache, File?) -> Unit) {
        getFile(Funcy2(callback))
    }

    override fun getFile(callback: IAmvCache.IGotFileCallback) {
        getFile(Funcy2(callback::onGotFile))
    }

    override suspend fun getFileAsync() : File? {
        return suspendCoroutine {cont->
            getFile {_, file ->
                cont.resume(file)
            }
        }
    }

    /**
     * ファイルをダウンロードして取得
     * ダウンロードが終わったら、callbackする
     */
    fun getFile(callback: IFuncy2<IAmvCache, File?, Unit>) {
        synchronized (mLock) {
            if(mInvalidated) {
                error.setError("cache has been invalidated.")
                callback.invoke(this, null)
                return
            }
            if (null == mFile) {
                if(!mDownloading) {
                    // コンストラクタでダウロードが開始されるので、ここには入ってこないはず
                    if(uri==null) {
                        error.setError("no uri to download.")
                        callback.invoke(this, null)
                        return
                    } else {
                        download(uri)
                    }
                }
                mDownloadedListener.add(null, callback)
                return
            }
        }
        // ファイルはキャッシュされている
        UtLogger.debug("AmvCacheManager: $key: file available")
        callback.invoke(this, mFile)
    }

    /**
     * DLに失敗した場合に、エラー情報を取得
     */
    override val error = AmvError()

    /**
     * キャッシュの参照を開始
     */
    override fun addRef() {
        synchronized (mLock) {
            mRefCount++
            if (mRefCount <= 0) {
                mRefCount = 1   // あってはならないこと
            }
        }
    }

    /**
     * キャッシュの参照を終了
     */
    override fun release() : Int {
        synchronized (mLock) {
            mRefCount--
            return mRefCount
        }
    }

    override val refCount: Int
        get() {
            synchronized(mLock) {
                return mRefCount
            }
        }

    override fun invalidate() {
        synchronized(mLock) {
            mInvalidated = true
        }
        AmvCacheManager.removeCache(this)
    }

    override val cacheFile: File?
        get() = mFile
}
