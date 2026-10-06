package com.x500x.cursimple.app.download

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/** Share connections and dispatch between text races, file transfers and image prefetch. */
internal object SharedHttp {
    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            // Large transfers use inactivity timeouts rather than a total call deadline.
            .callTimeout(0, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 8 })
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** Small text calls fail quickly so stalled mirrors cannot delay the race. */
    val text: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    val transfer: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}
