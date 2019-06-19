package com.michael.video

/**
 * videoライブラリのUIで、app側で定義した文字列(R.string.xxx)を使用するための小さな仕掛け
 */
object AmvStringPool {
    private val map = HashMap<Int, String>()

    @JvmStatic
    fun getString(id:Int) : String? {
        return map[id]
    }

    @JvmStatic
    fun setString(id:Int, str:String) {
        map[id] = str
    }
}

