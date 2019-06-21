/**
 * m4m で取り出した動画情報を保持するクラス
 *
 * @author M.TOYOTA 2018.07.26 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */

package com.michael.video

import android.content.Context
import android.media.MediaCodecInfo
import android.util.Size
import org.m4m.*
import org.m4m.android.AndroidMediaObjectFactory
import org.m4m.android.AudioFormatAndroid
import org.m4m.android.VideoFormatAndroid
import java.io.File
import java.lang.Exception
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("MemberVisibilityCanBePrivate")
data class AmvMediaInfo(val mediaFileInfo: MediaFileInfo) {
    constructor(source: File, context: Context) : this(MediaFileInfo(AndroidMediaObjectFactory(context)).apply { uri = Uri(source.toURI().toString()) })

    val duration: Long
        get() = mediaFileInfo.durationInMicroSec
    val audioFormat: AudioFormat?
        get() = mediaFileInfo.audioFormat as? AudioFormat
    val videoFormat: VideoFormat?
        get() = mediaFileInfo.videoFormat as? VideoFormat

    val hasVideo: Boolean
        get() = videoFormat != null
    val hasAudio: Boolean
        get() = audioFormat != null

    val mimeType: String
        get() = videoFormat!!.mimeType
    val codec: String
        get() = videoFormat!!.videoCodec

    val bitRate: Int
        get() = ignoreErrorCall(-1) {videoFormat?.videoBitRateInKBytes ?: -1}
    val frameRate: Int
        get() = ignoreErrorCall(-1){videoFormat?.videoFrameRate ?: -1}
    val iFrameInterval: Int
        get() = ignoreErrorCall(-1){videoFormat?.videoIFrameInterval ?: -1 }
    val size: Size by lazy {
        val s = videoFormat?.videoFrameSize
        if(null!=s) {
            Size(s.width(), s.height())
        } else {
            Size(1,1)
        }
    }
    val rotation: Int
        get() = mediaFileInfo.rotation

    val summary: String
        get() {
            val sb = StringBuilder()
            try {
                sb.append("Video --------\n")
                sb.append("MimeType = $mimeType\n")
                sb.append("Codec = $codec\n")
                sb.append("Size= ${size.width}W x ${size.height}H\n")
                sb.append("FrameRate = $frameRate\n")
                sb.append("BitRate = $bitRate\n")
                sb.append("Rotation=$rotation")
                sb.append("iFrameInterval = $iFrameInterval\n")
                sb.append("Audio --------\n")
                sb.append("Codec = $audioCodec\n")
                sb.append("SamplingRate = $audioSamplingRate\n")
                sb.append("BitRate = $audioBitRate\n")
                sb.append("ChannelCount = $audioChannelCount\n")
                sb.append("audioProfile = $audioProfile\n")
            } catch (e: Exception) {
                sb.append(e.message)
            }
            return sb.toString()
        }

    val audioCodec :String
        get() = audioFormat?.audioCodec ?: ""
    val audioBitRate: Int
        get() = ignoreErrorCall(-1) {audioFormat?.audioBitrateInBytes ?: -1}
    val audioSamplingRate : Int
        get() = ignoreErrorCall(-1) {audioFormat?.audioSampleRateInHz ?: -1}
    val audioChannelCount : Int
        get() = ignoreErrorCall(-1) {audioFormat?.audioChannelCount ?: -1}
    val audioProfile
        get() = ignoreErrorCall(-1) {audioFormat?.audioProfile ?: -1}

    val cMaxBitRate : Int = AmvSettings.maxBitRate

    val hd720Size : Size
        get() = calcHD720Size(size.width, size.height)

    private fun applyVideoParameters(composer:MediaComposer) {
        val size = hd720Size
        val videoFormat = VideoFormatAndroid(VideoFormat.MIME_TYPE, size.width, size.height)

        // オリジナルのビットレートより大きいビットレートにならないように。
        val orgBitrate = if (bitRate > 0) bitRate else cMaxBitRate   // avi (divx/xvid) のときに、prop.Bitrate==0になっていて、トランスコードに失敗することがあった。#5812:2
        val maxBitrate = min(orgBitrate, cMaxBitRate) // 最大4M bpsとする #5812
        videoFormat.videoBitRateInKBytes = maxBitrate

        // フレームレートはソースに合わせる。
        videoFormat.videoFrameRate = frameRate
        videoFormat.videoIFrameInterval = if(iFrameInterval>0) iFrameInterval else 1
        composer.targetVideoFormat = videoFormat
    }

    private fun applyAudioParameters(composer:MediaComposer) {
        if(hasAudio && audioSamplingRate>0 && audioChannelCount>0) {
            val audioFormat = AudioFormatAndroid("audio/mp4a-latm", audioSamplingRate, audioChannelCount)
            audioFormat.audioBitrateInBytes = if(audioBitRate>0) audioBitRate else 96000        // ソースから情報が取れなくても、なにか値を入れておかないとエラーになるみたい。
            audioFormat.audioProfile = if(audioProfile>0) audioProfile else MediaCodecInfo.CodecProfileLevel.AACObjectLC
            composer.targetAudioFormat = audioFormat
        }
    }

    fun applyHD720TranscodeParameters(composer: MediaComposer, dstFile: File) {
        if(!hasVideo) {
            throw AmvException("no video info")
        }

        composer.addSourceFile(mediaFileInfo.uri)
        composer.setTargetFile(dstFile.path, rotation)

        applyVideoParameters(composer)
        applyAudioParameters(composer)
    }

    companion object {
        @JvmStatic
        fun calcHD720Size(width:Int, height:Int) : Size {
            var r = if (width > height) { // 横長
                min(1280f / width, 720f / height)
            } else { // 縦長
                min(720f / width, 1280f / height)
            }
            if (r > 1) { // 拡大はしない
                r = 1f
            }
            return Size((width * r).roundToInt(), (height * r).roundToInt())
        }
    }
}
