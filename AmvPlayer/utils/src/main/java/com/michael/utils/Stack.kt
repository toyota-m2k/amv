/**
 * Push/Pop/Peek をサポートするシンプルなスタッククラス
 *
 * @author M.TOYOTA 2018.07.06 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */

package com.michael.utils

@Suppress("unused")
/**
 * Push/Pop/Peek をサポートするシンプルなスタッククラス
 */
class Stack<T> {

    private val items: MutableList<T> = ArrayList(16)

    /**
     * アイテム数
     */
    @Suppress("MemberVisibilityCanBePrivate")
    val count:Int
        get()=items.count()

    /**
     * スタックは空か？
     */
    @Suppress("MemberVisibilityCanBePrivate")
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
        return if (isEmpty) {
            null
        } else {
            items.removeAt(count - 1)
        }
    }

    /**
     * アイテムを取り出すが、削除しない
     */
    @Suppress("unused")
    fun peek():T? {
        return if (isEmpty) {
            null
        } else {
            items[count - 1]
        }
    }
}