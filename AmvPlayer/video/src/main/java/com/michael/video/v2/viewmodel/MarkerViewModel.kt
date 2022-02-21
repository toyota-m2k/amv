package com.michael.video.v2.viewmodel

import com.michael.utils.SortedList
import com.michael.video.AmvSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import kotlin.math.absoluteValue

class MarkerViewModel(val scope:CoroutineScope) : Closeable {
    companion object {
        val logger = AmvSettings.logger
        const val MinMarkerSpan : Long = 100L     // マーカー設定間隔の最小値（100ms）
    }
    private val markers = SortedList<Long>(32, { o0, o1-> if(o0==o1) 0 else if(o0<o1) -1 else 1 }, false)
    private val listPos = SortedList.Position()
    val markerList : List<Long>
        get() = markers

    data class MarkerOperation(val action:Action, val marker:Long, val markers:Iterable<Long>?, val clientData:Any?) {
        enum class Action {
            ADD,        // １個追加
            REMOVE,     // １個削除
            SET,        // 一括セット
            CLEAR,      // 全件クリア
        }
    }

    val markerCommand = MutableSharedFlow<MarkerOperation>(replay = 0, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
    val markerEvent = MutableSharedFlow<MarkerOperation>(replay = 0, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)

//    val highlightMarker = MutableStateFlow<Long>(-1L)
//    fun flashMarker(marker:Long, duration:Long = 300L /*ms*/) {
//        highlightMarker.value = marker
//        scope.launch {
//            delay(duration)
//            highlightMarker.value = -1
//        }
//    }
//    fun setHighlight(marker:Long) {
//        highlightMarker.value = marker
//    }
//    fun resetHighLight() {
//        highlightMarker.value = -1
//    }
//    inline fun withHighlightMarker(marker:Long, fn:()->Unit) {
//        highlightMarker.value = marker
//        fn()
//        highlightMarker.value = -1
//    }

    suspend fun setMarkers(markers:Iterable<Long>, clientData: Any?=null) {
        markerCommand.emit(MarkerOperation(MarkerOperation.Action.REMOVE, -1, markers, clientData))
    }
    suspend fun addMarker(marker:Long, clientData: Any?=null) {
        markerCommand.emit(MarkerOperation(MarkerOperation.Action.ADD, marker, null, clientData))
    }
    suspend fun removeMarker(marker:Long, clientData: Any?=null) {
        markerCommand.emit(MarkerOperation(MarkerOperation.Action.REMOVE, marker, null, clientData))
    }
    suspend fun clearMarker(marker:Long, clientData: Any?=null) {
        markerCommand.emit(MarkerOperation(MarkerOperation.Action.CLEAR, -1, null, clientData))
    }


    private suspend fun setMarkers(cmd:MarkerOperation) {
        assert(cmd.action == MarkerOperation.Action.SET)
        markers.clear()
        val v = cmd.markers ?: return
        val pos = SortedList.Position()
        for(e in v) {
            markers.addCore(e, pos)
        }
        markerEvent.emit(cmd)
    }

    private fun canAddMarker(marker:Long) : Boolean {
        markers.find(marker, listPos)
        if(listPos.hit>=0) {
            return false
        } else if(listPos.prev>=0) {
            if((markers[listPos.prev] - marker).absoluteValue < MinMarkerSpan) {
                return false
            }
        } else if(listPos.next>=0) {
            if((markers[listPos.next] - marker).absoluteValue < MinMarkerSpan) {
                return false
            }
        }
        return true
    }

    private suspend fun addMarker(cmd:MarkerOperation) {
        assert(cmd.action == MarkerOperation.Action.ADD && cmd.marker>=0)
        if(!canAddMarker(cmd.marker)) {
            return
        }
        if(markers.add(cmd.marker)) {
            markerEvent.emit(cmd)
        }
    }

    private suspend fun removeMarker(cmd:MarkerOperation) {
        assert(cmd.action == MarkerOperation.Action.REMOVE && cmd.marker>=0)
        if(markers.remove(cmd.marker)) {
            markerEvent.emit(cmd)
        }
    }

    fun nextMark(current:Long) : Long {
        markers.find(current, listPos)
        if (listPos.next < 0) {
            return -1
        }
        return markers[listPos.next]
    }

    fun prevMark(current:Long) : Long {
        markers.find(current, listPos)
        if (listPos.prev < 0) {
            return 0L   // これ以上前にマーカーがなければ先頭にシークする
        }
        return markers[listPos.prev]
    }

    fun find(pos:Long, ref:SortedList.Position?=null):Int {
        return markers.find(pos, ref?:listPos)
    }
    operator fun get(index:Int) : Long {
        if(index<0||markers.size<=index) return -1L
        return markers[index]
    }


//    fun <T> handleMarker(fn:(SortedList<Long>, SortedList.Position)->T):T {
//        return fn(markers, listPos)
//    }

    override fun close() {
        TODO("Not yet implemented")
    }
}