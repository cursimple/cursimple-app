package com.x500x.cursimple.core.plugin.market.github

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用线上注册表的真实 JSON 走一遍解析，确认 schools 真的落进 schoolAliases。 */
class LiveRegistrySchoolsTest {

    private val liveJson = """
        {"repositories":[{"name":"cursimple/YangtzU_course_plugin","repo":"YangtzU_course_plugin",
        "owner":"cursimple","avatar":"https://avatars.githubusercontent.com/u/283925439?s=80&v=4",
        "description":"长江大学教务系统课表插件 · Yangtze University (长大) course plugin for cursimple",
        "star":0,"language":"JavaScript","url":"https://github.com/cursimple/YangtzU_course_plugin",
        "schools":["长江大学","长大","changjiangdaxue","changjiang","cjdx","YangtzU","Yangtze University"]}]}
    """.trimIndent()

    @Test
    fun `schools from the registry land in schoolAliases`() = runBlocking {
        val repository = GitHubRegistryRepository(fetchText = { liveJson })

        val summaries = repository.fetchRegistry("cursimple/cursimple-plugins")

        assertEquals(1, summaries.size)
        val summary = summaries.single()
        assertTrue(
            "schoolAliases 为空，schools 没被解析进来：${summary.schoolAliases}",
            summary.schoolAliases.contains("长江大学"),
        )
        assertTrue(summary.description.contains("长江大学"))
    }
}
