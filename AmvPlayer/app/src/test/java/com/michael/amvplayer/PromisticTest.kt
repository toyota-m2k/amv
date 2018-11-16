package com.michael.amvplayer

import com.michael.amvplayer.exp.Promistic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromisticTest {
    @Test
    fun sequentialTest() {
        var counter = 0
        Promistic.promise()
                .then {
                    counter++   // 1
                    assertEquals(it,null)
                    Promistic.resolved(1)
                }
                .then {
                    counter++   // 2
                    assertTrue(it is Int)
                    assertEquals(it,1)
                    Promistic.resolved(2)
                }
                .failed {
                    // not called
                    counter++;
                }
                .anyway {
                    counter++   // 3
                    assertTrue(it is Long)
                    assertEquals(it, 3L)
                    assertEquals(counter, 3)
                }

                .then {
                    counter++   // 4
                    assertTrue(it is Int)
                    assertEquals(it,2)
                    assertEquals(counter, 4)
                    Promistic.rejected(3L)
                }
                .then {
                    counter++   // not called
                    Promistic.resolved(4)
                }
                .anyway {
                    counter++   // 5
                    assertTrue(it is Long)
                    assertEquals(it, 3L)
                    assertEquals(counter, 6)
                }
                .failed {
                    counter++   // 6
                    assertTrue(it is Long)
                    assertEquals(it, 3L)
                    assertEquals(counter, 6)
                }
                .then {
                    counter++   // not called
                    Promistic.resolved(5)
                }
                .failed {
                    counter++   // 7
                    assertTrue(it is Long)
                    assertEquals(it, 3L)
                    assertEquals(counter, 7)
                }
                .anyway {
                    counter++   // 8
                    assertTrue(it is Long)
                    assertEquals(it, 3L)
                    assertEquals(counter, 8)
                }
                .ignite()

        assertEquals(counter, 8)
    }

    @Test
    fun subSequenceTest() {
        var counter = 0



        var pm1 = Promistic.promise()
                .then {
                    counter++   // 1
                    assertEquals(it,null)
                    assertEquals(counter, 1)

                    Promistic.resolved(1)
                }
                .then {
                    counter++   // 2
                    assertTrue(it is Int)
                    assertEquals(it,1)
                    assertEquals(counter, 2)
                    Promistic.resolved(2)
                }

        var pm2 = Promistic.promise()
                .then {
                    counter++   // 4
                    assertEquals(it,null)
                    assertEquals(counter, 4)
                    Promistic.resolved(3)
                }
                .then {
                    counter++   // 5
                    assertTrue(it is Int)
                    assertEquals(it,3)
                    Promistic.resolved(4)
                }

        Promistic.promise()
                .then {
                    assertEquals(it,null)
                    assertEquals(counter, 0)

                    pm1
                }
                .then {
                    counter++   // 3
                    assertEquals(it,2)
                    assertEquals(counter, 3)
                    pm2
                }
                .then {
                    counter++   // 6
                    assertEquals(it,4)
                    assertEquals(counter, 6)
                    Promistic.resolved(5)
                }
                .anyway {
                    counter++   // 7
                    assertEquals(it,5)
                    assertEquals(counter, 7)
                }
                .failed {
                    counter++   // not called
                }
                .then {
                    counter++   // 8
                    assertEquals(it,5)
                    assertEquals(counter, 8)
                    null
                }
                .ignite()

        assertEquals(counter, 8)

    }

    @Test
    fun parallelAllTest() {
        var count = 0
        val pm1 = Promistic.promise()
                .then {
                    count++;
                    Promistic.resolved(1)
                }
                .then {
                    count++;
                    assertEquals(it, 1)
                    Promistic.resolved(2)
                }
                .anyway {
                    count++;
                    assertEquals(it, 2)
                }
        val pm2 = Promistic.promise()
                .then {
                    count++;
                    Promistic.resolved(3)
                }
                .then {
                    count++;
                    assertEquals(it, 3)
                    Promistic.resolved(4)
                }
                .failed {
                    count++;
                }

        val pm3 = Promistic.promise()
                .then {
                    count++;
                    Promistic.resolved(5)
                }
                .then {
                    count++;
                    assertEquals(it, 5)
                    Promistic.resolved(6)
                }
                .failed {
                    count++;
                }
                .anyway {
                    count++;
                    assertEquals(it, 6)
                }

        Promistic.promise()
                .all(listOf(pm1, pm2, pm3))
                .then {
                    assertEquals(count, 8)
                    assertTrue(it is List<Any?>)
                    val r = it as? List<Any?>
                    assertTrue(r != null)
                    if(r!=null) {
                        assertEquals(r.size, 3)
                        assertEquals(r[0], 2)
                        assertEquals(r[1], 4)
                        assertEquals(r[2], 6)
                    }
                    count++;
                    Promistic.resolved()
                }.ignite()
        assertEquals(count, 9)
    }

    @Test
    fun parallelAllRejectTest() {
        var count = 0
        val pm1 = Promistic.promise()
                .then {
                    count++;
                    Promistic.resolved(1)
                }
                .then {
                    count++;
                    assertEquals(it, 1)
                    Promistic.resolved(2)
                }
                .anyway {
                    count++;
                    assertEquals(it, 2)
                }
        val pm2 = Promistic.promise()
                .then {
                    count++;
                    Promistic.rejected(100L)
                }
                .then {
                    count++;    // not called
                    assertEquals(it, 3)
                    Promistic.resolved(4)
                }
                .failed {
                    count++;    // called
                }

        val pm3 = Promistic.promise()
                .then {
                    count++;
                    Promistic.resolved(5)
                }
                .then {
                    count++;
                    assertEquals(it, 5)
                    Promistic.resolved(6)
                }
                .failed {
                    count++;
                }
                .anyway {
                    count++;
                    assertEquals(it, 6)
                }

        Promistic.promise()
                .all(listOf(pm1, pm2, pm3))
                .then {
                    assertTrue(false)
                    Promistic.resolved()
                }
                .failed {
                    assertEquals(count, 8)
                    assertTrue(it is List<Any?>)
                    val r = it as? List<Any?>
                    assertTrue(r != null)
                    if(r!=null) {
                        assertEquals(r.size, 3)
                        assertEquals(r[0], 2)
                        assertEquals(r[1], 100L)
                        assertEquals(r[2], 6)
                    }
                    count++;
                    Promistic.resolved()
                }.ignite()
        assertEquals(count, 9)
    }

}