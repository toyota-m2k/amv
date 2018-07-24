package com.michael.utils

import android.os.Parcel

/**
 * addしたときに必ずソートされる配列クラス
 */
class SortedList<T>(capacity:Int, init:(index:Int)->T, private val comparator:(T,T)->Int, private val allowDuplication:Boolean) : List<T>, MutableCollection<T> {

    private val mList : ArrayList<T> = ArrayList<T>(capacity)

    override val size: Int
        get() = mList.size

    override fun contains(element: T): Boolean {
        return mList.contains(element)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return mList.containsAll(elements)
    }

    override fun get(index: Int): T {
        return mList[index]
    }

    override fun indexOf(element: T): Int {
        return mList.indexOf(element)
    }

    override fun isEmpty(): Boolean {
        return mList.isEmpty()
    }

    override fun iterator(): MutableIterator<T> {
        return mList.iterator()
    }

    override fun lastIndexOf(element: T): Int {
        return mList.lastIndexOf(element)
    }

    override fun listIterator(): ListIterator<T> {
        return mList.listIterator()
    }

    override fun listIterator(index: Int): ListIterator<T> {
        return mList.listIterator(index)
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<T> {
        return mList.subList(fromIndex, toIndex)
    }

    private fun addCore(element: T, pos:Position) : Boolean {
        if(find(element, pos)>=0 && !allowDuplication) {
            return false
        }

        if(pos.next<0) {
            mList.add(element)
        } else {
            mList.add(pos.next, element)
        }
        return true
    }

    override fun add(element: T): Boolean {
        return addCore(element, Position())
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val pos = Position()
        for(e in elements) {
            addCore(e, pos)
        }
        return true
    }

    override fun clear() {
        mList.clear()
    }

    fun removeAt(index:Int) : T {
        return mList.removeAt(index)
    }

    override fun remove(element: T): Boolean {
        val index = find(element, null)
        return if (index >= 0) {
            mList.removeAt(index)
            true
        } else {
            false
        }
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        return mList.removeAll(elements)
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        return mList.retainAll(elements)
    }


    data class Position(var hit:Int, var prev:Int, var next:Int){
        constructor() : this(-1,-1,-1)
        fun reset() {
            hit=-1
            prev=-1
            next=-1
        }
    }

    fun find(element: T, result:Position?) : Int {
        result?.reset()

        val count = mList.size
        var s = 0
        var e = count - 1
        var m: Int
        if (e < 0) {
            // 要素が空
            return -1
        }

        if (comparator(mList[e], element)<0) {
            // 最後の要素より後ろ
            result?.apply {
                prev = e
            }
            return -1
        }

        while (s <= e) {
            m = (s + e) / 2
            val v = mList[m]
            val cmp =comparator(v,element)
            if (cmp==0) {
                result?.apply {
                    hit = m
                    prev = m - 1
                    if (m < count - 1) {
                        next = m + 1
                    }
                }
                return m     // 一致する要素が見つかった
            } else if (cmp<0) {
                s = m + 1
            } else {
                e = m - 1
            }
        }
        result?.apply {
            next = s
            prev = s - 1
        }
        return -1
    }

    fun find(element: T) : Position {
        return Position().apply {
            find(element, this)
        }
    }

    /**
     * Parcelに出力できるように。。。
     */
    val asArrayList
        get() = ArrayList<T>(mList)
}