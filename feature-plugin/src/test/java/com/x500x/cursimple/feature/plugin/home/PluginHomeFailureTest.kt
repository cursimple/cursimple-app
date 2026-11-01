package com.x500x.cursimple.feature.plugin.home

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginHomeFailureTest {
    private fun kind(message: String) = classifyHomeFailure(IllegalStateException(message))

    @Test fun unreachableSchoolSystemIsReportedAsTheSchoolsProblem() {
        listOf(
            "打不开 https://jwc3.example.edu.cn/eams/home.action：net::ERR_CONNECTION_TIMED_OUT",
            "组件运行超时（90 秒）",
            "教务系统返回 HTTP 503",
            "The academic system returned HTTP 502",
            "net::ERR_NAME_NOT_RESOLVED",
            "系统维护中，请稍后访问",
        ).forEach { assertEquals(it, HomeFailureKind.SchoolUnreachable, kind(it)) }
    }

    @Test fun networkExceptionsCountEvenWhenWrapped() {
        assertEquals(HomeFailureKind.SchoolUnreachable, classifyHomeFailure(UnknownHostException("host")))
        assertEquals(HomeFailureKind.SchoolUnreachable, classifyHomeFailure(RuntimeException("wrapped", SocketTimeoutException())))
    }

    @Test fun otherFailuresAreNotBlamedOnTheSchool() {
        listOf(
            "教务系统暂时限制了连续查询，请稍后刷新。",
            "返回的数据不完整，本次没有替换缓存。",
            "教务系统返回 HTTP 404",
            "组件脚本出错",
        ).forEach { assertEquals(it, HomeFailureKind.Other, kind(it)) }
        assertEquals(HomeFailureKind.Other, classifyHomeFailure(IOException("disk full")))
    }
}
