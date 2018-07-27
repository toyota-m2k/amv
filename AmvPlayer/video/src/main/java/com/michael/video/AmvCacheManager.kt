package com.michael.video

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

object AmvCacheManager {

    private const val DEFAULT_MAX_CACHE_COUNT = 10
    private var mMaxCacheCount = DEFAULT_MAX_CACHE_COUNT
    private var mLock = java.lang.Object()
    private lateinit var mCacheFolder : File
    private val mCacheList = HashMap<String,IAmvCache>(DEFAULT_MAX_CACHE_COUNT+5)
    private var mSweeping = false

    /**
     * シングルトンの初期化
     * 他の機能を利用する前に1度だけ実行しておく
     */
    @JvmOverloads
    fun initialize(folder:File, maxCache:Int=DEFAULT_MAX_CACHE_COUNT) {
        mMaxCacheCount = maxCache
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
                    touch(file)
                    AmvCache(key, uri, file)
                } else {
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
            return cache ?: newCache(uri,key)
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

    /**
     * 古いキャッシュを削除
     */
    private fun sweep() {
        // 再入防止
        if (mSweeping) {
            return
        }
        mSweeping = true

        try {
            // キャッシュファイル列挙
            val list = mCacheFolder.listFiles()
            if (list.size < mMaxCacheCount) {
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
                for (i in mMaxCacheCount until list.size) {
                        val file = list[i]
                        val key = file.name
                        val cache = mCacheList[key]
                        if(null!=cache && cache.refCount<=0) {
                            mCacheList.remove(key)
                        }
                        file.delete()
                    }
                }
        } catch (e:Throwable) {
            UtLogger.error( "AmvCacheManager.Sweep\n${e.stackTrace}")
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
        val list = mCacheFolder.listFiles()
        if(null!=list) {
            for(f in list) {
                f.delete()
            }
        }
    }
}

/**
 * キャッシュファイルの内部表現
 */
private class AmvCache(private val key:String, override val uri:Uri, existsFile:File?) : IAmvCache{
    private var mFile: File? = null
    private var mInvalidFile: File? = null
    private var mRefCount: Int = 0
    private var mLock = java.lang.Object()
    private var mDownloading : Boolean = false
    private var mDownloadedListener = Funcies2<IAmvCache, File?, Unit>()

    init {
        if(null!=existsFile) {
            mFile = existsFile
        } else {
            download()
        }
    }

    /**
     * ファイルのダウンロードを開始する
     */
    private fun download() {
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
                val inStream = response?.body()?.byteStream()
                if(null==inStream) {
                    onFailure(call, IOException("no body data."))
                    return
                }
                val file = AmvCacheManager.getFileForKey(key)
                val outStream = FileOutputStream(file, false)
                inStream.copyTo(outStream, 128*1024) // buffer size 128KB
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

    /**
     * ファイルをダウンロードして取得
     * ダウンロードが終わったら、callbackする
     */
    fun getFile(callback: IFuncy2<IAmvCache, File?, Unit>) {
        synchronized (mLock) {
            if (null == mFile) {
                if(!mDownloading) {
                    download()  // コンストラクタでダウロードが開始されるので、ここには入ってこないはず
                }
                mDownloadedListener.add(null, callback)
                return
            }
        }
        // ファイルはキャッシュされている
        UtLogger.debug("AmvCache.GetFile: Cache File Available")
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
    override fun release() {
        synchronized (mLock) {
            mRefCount--
            if(mRefCount==0 && mFile==null && !mDownloading && mInvalidFile!=null) {
                // キャッシュが無効化されていて、参照がなくなった場合・・・キャッシュファイルを削除する
                try {
                    mInvalidFile?.delete()
                    mInvalidFile = null
                }
                catch(e:Throwable) {
                    UtLogger.stackTrace( e,"AmvCache.Release (deleting file).")
                }
            }
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
            mInvalidFile = mFile
            mFile = null
        }
    }

    override val cacheFile: File?
        get() = mFile
}
