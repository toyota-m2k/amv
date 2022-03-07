package com.michael.video

import android.net.Uri
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.michael.video.v2.cache.AmvCacheManager
import junit.framework.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AmvCacheManagerTest {
    private val uri1: Uri = Uri.parse("https://video.twimg.com/ext_tw_video/1003088826422603776/pu/vid/1280x720/u7R7uUhgWjPalQ0F.mp4?tag=3")
    private val uri2: Uri = Uri.parse("https://video.twimg.com/ext_tw_video/1002595428049645570/pu/vid/720x1280/WNxL-rxdrGlM9wu_.mp4?tag=3")
    private val uri3: Uri = Uri.parse("https://video.twimg.com/ext_tw_video/1021604619271557120/pu/vid/720x1280/2ffcbsSgNcZyXtMx.mp4?tag=3")
    private val uri4: Uri = Uri.parse("https://video.twimg.com/ext_tw_video/1021376906610978816/pu/vid/720x1280/MiOHJiJJcMXBdUoq.mp4?tag=3")

    class Watcher(private val count:Int) {
        private val list = ArrayList<Uri>()
        private val lock = java.lang.Object()
        fun add(uri:Uri, fn:()->Unit) {
            return synchronized(lock) {
                list.add(uri)
                if(list.size == count) {
                    fn()
                }
            }
        }
    }

    @Test
    fun cacheManagerTest() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()
        assertEquals("com.michael.video.test", appContext.packageName)

        val cacheFolder = File(appContext.cacheDir, "cacheTest")
        AmvCacheManager.initialize(cacheFolder, 3)
        AmvCacheManager.clearAllCache()

        val watcher = Watcher(3)
        val downloaded = {
            assertEquals(AmvCacheManager.cacheCount, 3)
            assertTrue(AmvCacheManager.hasCache(uri1,null))
            assertTrue(AmvCacheManager.hasCache(uri2,null))
            assertTrue(AmvCacheManager.hasCache(uri3,null))
            val c4 = AmvCacheManager.getCache(uri4, null)
            c4.getFile { _, _ ->
                assertEquals(AmvCacheManager.cacheCount, 3)
            }
            assertTrue(AmvCacheManager.hasCache(uri4, null))
        }

        val c1 = AmvCacheManager.getCache(uri1,null)
        val c11 = AmvCacheManager.getCache(uri1, null)
        assertSame(c1, c11)

        c1.addRef()
        assertEquals(c1.refCount,3)
        assertEquals(c11.refCount,3)
        assertTrue(AmvCacheManager.hasCache(uri1,null))
        c1.release()
        assertEquals(c1.refCount,2)
        c1.release()
        assertEquals(c1.refCount,1)

        c1.getFile { _, file ->
            assertNotNull(file)
            if(null!=file) {
                assertTrue(file.exists())
                assertTrue(file.isFile)
            }
            c1.release()
            watcher.add(uri1, downloaded)
        }

        val c2 = AmvCacheManager.getCache(uri2, null)
        c2.getFile { _, file->
            assertNotNull(file)
            if(null!=file) {
                assertTrue(file.exists())
                assertTrue(file.isFile)
            }
            c2.release()
            watcher.add(uri2, downloaded)
        }

        val c3 = AmvCacheManager.getCache(uri3, null)
        c3.getFile { _, file->
            assertNotNull(file)
            if(null!=file) {
                assertTrue(file.exists())
                assertTrue(file.isFile)
            }
            c3.release()
            watcher.add(uri3, downloaded)
        }
    }
}
