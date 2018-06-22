package com.michael.amvplayer.utils

/**
 * Push/Pop/Peek をサポートするシンプルなスタッククラス
 */
class Stack<T>() {

    val items: MutableList<T> = ArrayList<T>(16);

    /**
     * アイテム数
     */
    val count:Int
        get()=items.count()

    /**
     * スタックは空か？
     */
    val isEmpty:Boolean
        get()=items.isEmpty()

    override fun toString() = items.toString()

    /**
     * アイテム追加
     */
    fun push(element:T) {
        val position = count
        this.items.add(position, element)
    }

    /**
     * アイテムを取り出す
     */
    fun pop():T? {
        if (isEmpty) {
            return null
        } else {
            return items.removeAt(count - 1)
        }
    }

    /**
     * アイテムを取り出すが、削除しない
     */
    fun peek():T? {
        if (isEmpty) {
            return null
        } else {
            return items[count - 1]
        }
    }

}