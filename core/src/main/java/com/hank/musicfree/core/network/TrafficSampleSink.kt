package com.hank.musicfree.core.network

interface TrafficSampleSink {
    fun offer(sample: TrafficSample)
}
