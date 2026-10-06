package com.x500x.cursimple.core.plugin.market.github

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registry-schema fixture verifies schools-to-schoolAliases parsing. */
class RegistrySchoolsTest {

    private val liveJson = """
        {"repositories":[{"name":"cursimple/example_school_plugin","repo":"example_school_plugin",
        "owner":"cursimple","avatar":"https://avatars.githubusercontent.com/u/283925439?s=80&v=4",
        "description":"示例大学教务系统课表插件 · Example University (示大) course plugin for cursimple",
        "star":0,"language":"JavaScript","url":"https://github.com/cursimple/example_school_plugin",
        "schools":["示例大学","示大","example-university","example-university","exampleu","ExampleU","Example University"]}]}
    """.trimIndent()

    @Test
    fun `schools from the registry land in schoolAliases`() = runBlocking {
        val repository = GitHubRegistryRepository(fetchText = { liveJson })

        val summaries = repository.fetchRegistry("cursimple/cursimple-plugins")

        assertEquals(1, summaries.size)
        val summary = summaries.single()
        assertTrue(
            "schoolAliases 为空，schools 没被解析进来：${summary.schoolAliases}",
            summary.schoolAliases.contains("示例大学"),
        )
        assertTrue(summary.description.contains("示例大学"))
    }
}
