package com.michael.utils

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * セマフォを使った Windows Event風クラス
 * 待ち受け側スレッド(waitOne()を呼び出すスレッド）は１つに限定。
 * （WindowsのEventクラスのように、複数のスレッドで同時に待ち合わせるような使い方はできない）。
 *
 * @author M.TOYOTA 13/10/21 Created.
 * @author Copyright (C) 2013 MetaMoJi Corp. All Rights Reserved.
 */
class UtResetableEvent(initialSignaled: Boolean, private val mAutoReset: Boolean) {
    private val mSemaphore: Semaphore

    init {
        mSemaphore = Semaphore(1, false)
        if (!initialSignaled) {
            mSemaphore.drainPermits()
        }
    }

    fun set() {
        mSemaphore.release(1)
    }

    fun reset() {
        mSemaphore.drainPermits()
    }

    @Throws(InterruptedException::class)
    fun waitOne() {
        mSemaphore.acquire(1)
        // mSemaphore.release();
        if (mAutoReset) {
            mSemaphore.drainPermits()
        } else {
            mSemaphore.release()
        }
    }

    @Throws(InterruptedException::class)
    fun waitOne(ms: Long): Boolean {
        if (!mSemaphore.tryAcquire(1, ms, TimeUnit.MILLISECONDS)) {
            return false
        }
        // mSemaphore.release();
        if (mAutoReset) {
            mSemaphore.drainPermits()
        } else {
            mSemaphore.release()
        }
        return true
    }

    fun <T> withLock(action: () -> T): T {
        waitOne()
        return action()
    }

}
