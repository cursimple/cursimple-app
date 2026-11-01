package com.x500x.cursimple.feature.plugin.home

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Why a home query failed, so the page can say whose problem it is. */
internal enum class HomeFailureKind {
    /** The school system or its gateway did not answer: down, in maintenance, or returned a 5xx. */
    SchoolUnreachable,
    Other,
}

private val unreachableMessage = Regex(
    "打不开|组件运行超时|net::ERR_|ERR_(?:CONNECTION|NAME_NOT_RESOLVED|TIMED_OUT|INTERNET|ADDRESS|SSL|EMPTY_RESPONSE)|" +
        "HTTP\\s*5\\d\\d|timed out|timeout|unable to resolve|failed to connect|connection (?:refused|reset)|" +
        "unreachable|maintenance|维护|停机|暂停服务",
    RegexOption.IGNORE_CASE,
)

internal fun classifyHomeFailure(error: Throwable): HomeFailureKind {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is UnknownHostException || cause is ConnectException || cause is SocketTimeoutException ||
            cause is NoRouteToHostException || cause is SSLException || cause is SocketException
        ) return HomeFailureKind.SchoolUnreachable
        cause = cause.cause?.takeIf { it !== cause }
    }
    return if (unreachableMessage.containsMatchIn(error.message.orEmpty())) HomeFailureKind.SchoolUnreachable
    else HomeFailureKind.Other
}
