package com.michael.video

class AmvError {
    private var mMessage: String? = null
    private var mException: Throwable? = null

    @Suppress("MemberVisibilityCanBePrivate")
    val hasError: Boolean
        get() = (mMessage != null || mException != null)

    val message : String
        get() = mMessage ?: mException?.message ?: ""


    @Suppress("unused")
    fun reset() {
        mException = null
        mMessage = null
    }

    fun setError(e:Throwable) {
        if (null == mException) {
            mException = e
        }
    }

    fun setError(message:String) {
        if (null == mMessage) {
            mMessage = message
        }
    }

    @Suppress("unused")
    fun copyFrom(e:AmvError) {
        if (!hasError) {
            mException = e.mException
            mMessage = e.mMessage
        }
    }

    override fun toString(): String {
        return when {
            null!=mException -> mException.toString()
            null!=mMessage -> message
            else -> "No error."
        }
    }
}