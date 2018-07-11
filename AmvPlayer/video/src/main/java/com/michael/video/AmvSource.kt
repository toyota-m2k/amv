package com.michael.video

import org.m4m.Uri
import java.io.File

class AmvSource {
    private val mSource:Any

    constructor(uri: Uri) {
        mSource = uri
    }
    constructor(file: File) {
        mSource = file
    }

    enum class SourceType {
        NONE,
        FILE,
        URI,
    }

    val sourceType: SourceType
        get() = if(mSource is Uri) SourceType.URI else if(mSource is File) SourceType.FILE else SourceType.NONE
}