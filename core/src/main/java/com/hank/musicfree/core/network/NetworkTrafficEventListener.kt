package com.hank.musicfree.core.network

import com.hank.musicfree.core.util.Clock
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class NetworkTrafficEventListener internal constructor(
    private val sink: TrafficSampleSink,
    private val networkTypeDetector: NetworkTypeDetector,
    private val clock: Clock,
) : EventListener() {

    private var snapshotType: NetworkType = NetworkType.OTHER
    private var bytesSent: Long = 0
    private var bytesReceived: Long = 0

    override fun callStart(call: Call) {
        snapshotType = networkTypeDetector.current()
        bytesSent = 0
        bytesReceived = 0
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        bytesSent += byteCount
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        bytesReceived += byteCount
    }

    override fun callEnd(call: Call) {
        flush()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        flush()
    }

    private fun flush() {
        if (bytesSent == 0L && bytesReceived == 0L) return
        sink.offer(
            TrafficSample(
                localDate = LocalDate.now(ZoneId.systemDefault()),
                networkType = snapshotType,
                bytesReceived = bytesReceived,
                bytesSent = bytesSent,
                timestampMs = clock.now(),
            )
        )
    }

    class Factory @Inject constructor(
        private val sink: TrafficSampleSink,
        private val detector: NetworkTypeDetector,
        private val clock: Clock,
    ) : EventListener.Factory {
        override fun create(call: Call): EventListener =
            NetworkTrafficEventListener(sink, detector, clock)
    }
}
