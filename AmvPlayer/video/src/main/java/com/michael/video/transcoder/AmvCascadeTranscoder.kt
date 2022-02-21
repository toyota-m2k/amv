package com.mihcael.video.transcoder

import android.content.Context
import com.michael.utils.FuncyListener2
import com.michael.video.AmvError
import com.michael.video.AmvSettings
import java.io.File

/**
 * ３種類のTranscoder（Amp/m4m/Exo)を組み合わせて頑張るトランスコーダークラス
 */
class AmvCascadeTranscoder
    @JvmOverloads constructor(val sourceFile: File, val context: Context, private var mode: Mode = Mode.AUTO, private val useAmp:Boolean=true) : IAmvTranscoder {
    val logger = AmvSettings.logger

    override val completionListener = FuncyListener2<IAmvTranscoder, Boolean, Unit>()
    override val progressListener = FuncyListener2<IAmvTranscoder, Float, Unit>()
    override var error = AmvError()
        private set

    /**
     * 動作モード
     */
    enum class Mode {
        REPAIR,         // exoのみ
        SINGLE,         // amp(or m4m) によるエンコードのみ
        CASCADE,        // exoでエンコードしてから、amp(or m4m)でエンコード
        AUTO,           // ampでエラーになったら、exo --> amp(or m4m) でエンコード（初段は必ずampを使う）
    }

    /**
     * 内部transcoder
     */
    private var transcoder: IAmvTranscoder = when(mode) {
        Mode.REPAIR -> AmvExoTranscoder(sourceFile,context)
        Mode.SINGLE -> if(useAmp) AmvAmpTranscoder(sourceFile) else AmvM4mTranscoder(sourceFile,context)
        Mode.CASCADE -> AmvExoTranscoder(sourceFile,context)
        Mode.AUTO -> AmvAmpTranscoder(sourceFile)
    }.also { tc->
        tc.completionListener.set(this::internalCompleted)
        tc.progressListener.set(this::internalProgress)
    }

    /**
     * 出力ファイル
     */
    lateinit var distFile:File

    /**
     * トリミング情報
     * エラー発生時のリトライに必要なのでメンバーに覚えておく
     */
    var trimStart:Long = 0L
    var trimEnd:Long = 0L

    /**
     * CASCADEで使用する中間ファイル
     */
    var tempFile:File? = null

    /**
     * 内部transcoderの実行結果を処理するハンドラ
     */
    private fun internalCompleted(tr: IAmvTranscoder, result:Boolean) {
        logger.info("$mode ${tr.javaClass.simpleName} ($result) ${tr.error.message}")
        when(mode) {
            Mode.SINGLE, Mode.REPAIR -> {
                // 内部transcoderの結果をそのまま返す。
                error = tr.error
                completionListener.invoke(this, result)
            }
            Mode.CASCADE -> {
                if (!result || !(tr is AmvExoTranscoder) ) {
                    error = tr.error
                    completionListener.invoke(this, result)
                } else {
                    // 初段が成功したら、２段目へ
                    next()
                }
            }
            Mode.AUTO -> {
                if(result || !tr.error.hasError) {
                    error = tr.error
                    completionListener.invoke(this, result)
                } else {
                    // 初段が失敗したら、CASCADEモードでリトライ
                    mode = Mode.CASCADE
                    retry()
                }
            }
        }
    }

    /**
     * 内部transcoderの進捗イベントのハンドラ
     */
    private fun internalProgress(@Suppress("UNUSED_PARAMETER") tr: IAmvTranscoder, progress:Float) {
        progressListener.invoke(this, progress)
    }

    /**
     * AUTOモードで、初段（Amp|m4m)が失敗したとき、CASCADEモード（初段：Exo）でリトライする。
     */
    private fun retry() {
        logger.debug()
        logger.assert(mode== Mode.CASCADE && !(transcoder is AmvExoTranscoder), "exoTranscoder error can not be recovered.")
        transcoder.dispose()
        transcoder = AmvExoTranscoder(sourceFile, context).also { tc->
            tc.completionListener.set(this::internalCompleted)
            tc.progressListener.set(this::internalProgress)
        }
        if(trimStart==0L && trimEnd==0L) {
            transcoder.transcode(distFile)
        } else {
            transcoder.truncate(distFile, trimStart, trimEnd)
        }
    }

    /**
     * CASCADEモードで、初段(Exo)が成功したとき、２段目（Amp|m4m）に進む。
     */
    private fun next() {
        logger.debug()
        logger.assert(mode== Mode.CASCADE && transcoder is AmvExoTranscoder && !transcoder.error.hasError, "cascade is not necessary except for exoTranscoder.")
        transcoder.dispose()
        // １段目の出力ファイルを別名にリネーム
        val tempFile = File(distFile.parentFile, "tmp-${distFile.name}")
        try {
            distFile.renameTo(tempFile)
            this.tempFile = tempFile
        } catch(e:Throwable) {
            logger.stackTrace(e)
            error.setError(e)
            completionListener.invoke(this, false)
            return
        }
        // ２段目
        transcoder = (if(useAmp) AmvAmpTranscoder(tempFile) else AmvM4mTranscoder(tempFile, context)).also { tc->
            tc.completionListener.set(this::internalCompleted)
            tc.progressListener.set(this::internalProgress)
        }
        // 初段(Exo)で(もし要求されていれば)トリミングは実行済みなので、２段目は常にtranscodeでok（というかtruncateを呼ぶと確実に動作不正を起こす）。
        transcoder.transcode(distFile)
    }

    /**
     * トランスコードを実行
     */
    override fun transcode(distFile: File) {
        logger.debug()
        trimStart = 0L
        trimEnd = 0L
        this.distFile = distFile
        transcoder.transcode(distFile)
    }

    /**
     * トリミングを実行
     */
    override fun truncate(distFile: File, start: Long, end: Long) {
        logger.debug()
        trimStart = start
        trimEnd = end
        this.distFile = distFile
        transcoder.truncate(distFile, start, end)
    }

    /**
     * キャンセル
     */
    override fun cancel() {
        logger.debug()
        transcoder.cancel()
    }

    /**
     * 解放
     */
    override fun dispose() {
        logger.debug()
        transcoder.dispose()
        completionListener.reset()
        progressListener.reset()
    }
}