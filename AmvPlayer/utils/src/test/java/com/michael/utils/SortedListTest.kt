package com.michael.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SortedListTest {
    @Test
    fun AddLittleTest() {
        val list = SortedList<Long>(16, { i -> 0L }, { i0, i1 -> (i0 - i1).toInt() }, false)

        list.add(1000)
        assertEquals(list[0], 1000)

        list.add(500)
        assertEquals(list[0], 500)
        assertEquals(list[1], 1000)

        list.add(2000)
        assertEquals(list[0], 500)
        assertEquals(list[1], 1000)
        assertEquals(list[2], 2000)

        list.add(300)
        assertEquals(list[0], 300)
        assertEquals(list[1], 500)
        assertEquals(list[2], 1000)
        assertEquals(list[3], 2000)
    }

    @Test
    fun AddLargeTest() {
        val list = SortedList<Long>(16, { i -> 0L }, { i0, i1 -> (i0 - i1).toInt() }, false)

        list.add(1000)
        assertEquals(list[0], 1000)

        list.add(2000)
        assertEquals(list[0], 1000)
        assertEquals(list[1], 2000)

        list.add(500)
        assertEquals(list[0], 500)
        assertEquals(list[1], 1000)
        assertEquals(list[2], 2000)

        list.add(3000)
        assertEquals(list[0], 500)
        assertEquals(list[1], 1000)
        assertEquals(list[2], 2000)
        assertEquals(list[3], 3000)
    }

    @Test
    fun AddMidTest() {
        val list = SortedList<Long>(16, { i -> 0L }, { i0, i1 -> (i0 - i1).toInt() }, false)

        var r:Boolean

        list.add(1000)
        assertEquals(list[0], 1000)

        list.add(500)
        assertEquals(list[0], 500)
        assertEquals(list[1], 1000)

        r = list.add(700)
        assertEquals(r, true)

        assertEquals(list[0], 500)
        assertEquals(list[1], 700)
        assertEquals(list[2], 1000)

        r = list.add(900)
        assertEquals(list[0], 500)
        assertEquals(list[1], 700)
        assertEquals(list[2], 900)
        assertEquals(list[3], 1000)

        r = list.add(700)
        assertEquals(r, false)
        assertEquals(list.size, 4)

        var pos = list.find(500)
        assertEquals(pos.hit, 0)
        assertEquals(pos.prev, -1)
        assertEquals(pos.next, 1)

        pos = list.find(700)
        assertEquals(pos.hit, 1)
        assertEquals(pos.prev, 0)
        assertEquals(pos.next, 2)

        pos = list.find(900)
        assertEquals(pos.hit, 2)
        assertEquals(pos.prev, 1)
        assertEquals(pos.next, 3)

        pos = list.find(1000)
        assertEquals(pos.hit, 3)
        assertEquals(pos.prev, 2)
        assertEquals(pos.next, -1)

        pos = list.find(400)
        assertEquals(pos.hit, -1)
        assertEquals(pos.prev, -1)
        assertEquals(pos.next, 0)

        pos = list.find(950)
        assertEquals(pos.hit, -1)
        assertEquals(pos.prev, 2)
        assertEquals(pos.next, 3)

        pos = list.find(2000)
        assertEquals(pos.hit, -1)
        assertEquals(pos.prev, 3)
        assertEquals(pos.next, -1)

    }
}