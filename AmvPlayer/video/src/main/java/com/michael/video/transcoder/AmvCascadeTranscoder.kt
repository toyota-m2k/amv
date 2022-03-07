package com.michael.video.transcoder

import android.content.Context
import android.os.Build
import com.michael.video.v2.common.AmvSettings
import com.michael.video.v2.util.AmvClipping
import com.michael.video.v2.util.AmvFile
import com.michael.video.v2.util.AmvTempFile
import com.michael.video.v2.util.withTempFile
import com.mihcael.video.transcoder.AmvAmpTranscoder
import com.mihcael.video.transcoder.AmvExoTranscoder
import com.mihcael.video.transcoder.AmvM4mTranscoder
import com.mihcael.video.transcoder.IAmvTranscoder
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ３種類のTranscoder（Amp/m4m/Exo)を組み合わせて頑張るトランスコーダークラス
 */
class AmvCascadeTranscoder
    @JvmOverloads constructor(
        private val sourceFile: AmvFile,
        val context: Context,
        override var progress:MutableStateFlow<Int>?,
        processMode: Mode = Mode.AUTO,
        useAmp:Boolean=true)
    : IAmvTranscoder {
    val logger = AmvSettings.logger

    /**
     * 動作モード
     */
    enum class Mode {
        REPAIR,         // exoのみ
        SINGLE,         // amp(or m4m) によるエンコードのみ
        CASCADE,        // exoでエンコードしてから、amp(or m4m)でエンコード
        AUTO,           // ampでエラーになったら、exo --> amp(or m4m) でエンコード（初段は必ずampを使う）
    }

    override val remainingTime:Long
        get() = transcoder?.remainingTime ?: -1L

    /**
     * 内部transcoder
     */
    private var transcoder: IAmvTranscoder? = null
    private val useAmp:Boolean
    private val processMode: Mode

    init {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.useAmp = useAmp
            this.processMode = processMode
        } else {
            this.useAmp = false
            this.processMode = Mode.SINGLE
        }
    }

    private suspend fun processCascade(src:AmvFile, dst: AmvFile, clipping: AmvClipping):AmvResult {
        val transcoder = AmvExoTranscoder(src, context, progress)
        return AmvTempFile().withTempFile { tmp->
            val intermediate = AmvFile(tmp)
            val result = transcoder.transcode(intermediate, clipping)
            if(!result.succeeded) {
                result
            } else {
                processSingle(intermediate, dst, AmvClipping.empty)
            }
        }
    }

    private suspend fun processAuto(src:AmvFile, dst:AmvFile, clipping: AmvClipping):AmvResult {
        val result = processSingle(src, dst, clipping)
        return if(result.succeeded) {
            result
        } else {
            processCascade(src, dst, clipping)
        }
    }

    private suspend fun processSingle(src:AmvFile, dst:AmvFile, clipping: AmvClipping) : AmvResult {
        return (if(useAmp) AmvAmpTranscoder(src, progress) else AmvM4mTranscoder(src, context, progress)).transcode(dst, clipping)
    }

    private suspend fun processRepair(src:AmvFile, dst:AmvFile, clipping:AmvClipping) : AmvResult {
        return AmvExoTranscoder(src, context, progress).transcode(dst, clipping)
    }

    /**
     * トランスコードを実行
     */
    override suspend fun transcode(distFile: AmvFile, clipping: AmvClipping): AmvResult {
        logger.debug()
        val result = try {
            when (processMode) {
                Mode.REPAIR -> processRepair(sourceFile, distFile, clipping)
                Mode.SINGLE -> processSingle(sourceFile, distFile, clipping)
                Mode.CASCADE -> processCascade(sourceFile, distFile, clipping)
                Mode.AUTO -> processAuto(sourceFile, distFile, clipping)
            }
        } catch(e:Throwable) {
            logger.stackTrace(e)
            AmvResult.failed(e)
        }
        if(!result.succeeded) {
            distFile.safeDelete()
        }
        close()
        return result
    }

    /**
     * キャンセル
     */
    override fun cancel() {
        logger.debug()
        transcoder?.cancel()
    }

    /**
     * リソース開放
     */
    override fun close() {
        logger.debug()
        transcoder?.close()
        transcoder = null
    }
}