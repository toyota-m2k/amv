/**
 * Video Controller i/f
 *
 * @author M.TOYOTA 2018.07.04 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */


package com.michael.video

import android.view.View

/**
 * Videoコントロールパネルのi/f
 */
interface IAmvVideoController {

    /**
     * ビデオプレーヤーにコントローラーを接続する
     */
    fun setVideoPlayer(player:IAmvVideoPlayer)

    /**
     * リードオンリーモードの設定/変更
     * （マーク追加ボタンがグレーアウトするだけ）
     */
    var isReadOnly : Boolean

    /**
     * スライダーを操作中（シーク中）か？
     */
    val isSeekingBySlider : Boolean

    fun dispose()
}

/**
 * Markerの設定をサポートするVideoコントロールパネルのi/f
 */
interface IAmvMarkerEditableController : IAmvVideoController {
    interface IMarkerEditor {
        fun setMarkerListener(listener: IMarkerListener?)
        fun setMarkers(markers: Collection<Double>)
        fun addMarker(position: Double, clientData: Any?)
        fun removeMarker(position: Double, clientData: Any?)
        fun setHighLightMarker(position:Double?)
        val view: View
    }

    interface IMarkerListener {
        fun onMarkerRemoved(sender:IMarkerEditor, marker:Double, clientData:Any?)
        fun onMarkerAdded(sender:IMarkerEditor, marker:Double, clientData:Any?)
        fun onMarkerContextMenu(sender:IMarkerEditor, marker:Double, x:Float, clientData: Any?)
    }

    /**
     * Marker編集用i/f
     */
    val markerEditor: IMarkerEditor
}

