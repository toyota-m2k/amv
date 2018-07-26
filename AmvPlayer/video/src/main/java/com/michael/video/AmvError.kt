package com.michael.video

class AmvError {
    var mMessage: String? = null
    var mException: Throwable? = null

    val hasError: Boolean
        get() = (mMessage != null || mException != null)

    val message : String
        get() = mMessage ?: mException?.message ?: ""


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

    fun copyFrom(e:AmvError) {
        if (!hasError) {
            mException = e.mException;
            mMessage = e.mMessage;
        }
    }

    override fun toString(): String {
        return if(null!=mException) {
            mException.toString()
        } else if(null!=mMessage) {
            message
        } else {
            "No error."
        }
    }
}