package com.michael.video

import java.io.File

object AmvSettings {
    private var initialized: Boolean = false
    internal lateinit var workDirectory: File
    var maxBitRate = 705 // k bps
        private set(v) { field = v }

    @JvmStatic
    fun initialize(cacheRootPath: File, bitrate:Int) {
        if (initialized) {
            return
        }
        initialized = true
        maxBitRate = bitrate
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