/**
 * Video Controller i/f
 *
 * @author M.TOYOTA 2018.07.04 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */


package com.michael.video

import com.michael.utils.Funcies2
import java.io.File

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

//    /**
//     * フレームリストのコンテント幅（スクローラー内コンテントの幅）が確定したときにコールバックされる
//     */
//    val contentWidthChanged: ContentWidthChanged
//
//    class ContentWidthChanged : Funcies2<IAmvVideoController, Int, Unit>() {
//        interface IHandler {    // for Java
//            fun contentWidthChanged(vc:IAmvVideoController, width: Int)
//        }
//        @JvmOverloads
//        fun add(listener:IHandler, name:String?=null) = super.add(name, listener::contentWidthChanged)
//        fun remove(listener:IHandler) = this.remove(listener::contentWidthChanged)
//    }

}

