package com.michael.video

import android.os.Build
import java.io.File

object AmvSettings {
    private var initialized: Boolean = false
    private var allowPictureInPictureByCaller = false
    lateinit var workDirectory: File
    var maxBitRate = 705 // k bps
        private set(v) { field = v }
    val allowPictureInPicture
        get() = allowPictureInPictureByCaller && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @JvmStatic
    fun initialize(cacheRootPath: File, bitrate:Int, allowPinP:Boolean) {
        if (initialized) {
            return
        }
        initialized = true
        maxBitRate = bitrate
        allowPictureInPictureByCaller = allowPinP
        val videoCache = File(cacheRootPath, ".video-cache")
        AmvCacheManager.initialize(videoCache)

        workDirectory = File(cacheRootPath, ".video-tmp")
        if (!workDirectory.exists()) {
            workDirectory.mkdir()
        } else {
            for (file in workDirectory.listFiles())
                if (!file.isDirectory) {
                    file.delete()
                }
        }
    }
}