/**
 * Parceler を使って、オブジェクトのBundleを生成をサポートするユーティリティー
 *
 * Usage
 *
 * @Parcel
 * data class SomeData(val param1:Int, val param2:String) : Packing {   // (1)
 *    constructor() : this(0,"unknown")                     // (2)
 *    companion object : UnPacker<Args>("Key_SomeData")     // (3)
 *    fun pack(to:Bundle?=null) = pack(defKey, to)          // (4)
 * }
 *
 * 必須
 * (1) @Parcel アノテーションを付けた data class を　Packing から派生する
 * (2) 引数無しのデフォルトコンストラクタを定義
 *
 * ・ここまで実装すると、次のように書ける。
 *
 *      val a = SomeData(100, "hoge")
 *      val bundle = a.pack("Key_SomeData")
 *      val b = bundle.unpack("Key_SomeData")
 *
 * オプショナル
 * (3) company objectを、UnPackerから派生する（defKeyに適当なキー文字列を指定）
 * (4) pack(Bundle?) メソッドを定義
 *
 * ・ここまで実装すると、次のように書ける
 *
 *      val a = SomeData(100, "hoge")
 *      val bundle = a.pack()
 *      val b = SomeData.unpack(bundle)
 */

package com.michael.utils

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import org.parceler.Parcels

/**
 * Bundle から、Packing派生オブジェクトを取り出すための拡張関数
 */
fun <T: Packing> Bundle.unpack(key:String) : T {
    return Parcels.unwrap<T>(this.getParcelable(key))
}

/**
 * Packing派生クラスに、unpackメソッド（Bundleからオブジェクトを取り出す）を追加するためのヘルパクラス
 * companion object の基底クラスとして使用する。
 */
open class UnPacker<T: Packing>(val defKey:String) {
    open fun unpack(from:Bundle, key:String?=null) : T {
        return from.unpack(key?:defKey)
    }
}

/**
 * データクラスに、pack()メソッド（Bundleに保存する）を提供するための基底クラス。
 */
interface Packing {

    /**
     * バンドルに保存する
     * @param key   キー文字列
     * @param to    保存先バンドル（nullなら新規作成する）
     */
    fun pack(key:String, to:Bundle?) : Bundle {
        val parcel = Parcels.wrap(this)
        return (to ?: Bundle()).apply {
            this.putParcelable(key, parcel)
        }
    }
}

fun <T> Parcel.readParceler() : T? {
    val p = this.readParcelable<Parcelable>(Parcelable::class.java.classLoader)
    return if(null!=p) {
        Parcels.unwrap<T>(p)
    } else {
        null
    }
}

fun <T> Parcel.writeParceler(v:T?) {
    this.writeParcelable(Parcels.wrap(v), 0)
}
