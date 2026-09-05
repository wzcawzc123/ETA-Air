package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStateTest {

    @Test
    fun rendersCompactText() {
        val s = SessionState(
            objective = "排查某 bug",
            completed = listOf("查看日志"),
            pending = listOf("修改代码"),
            decisions = listOf("采用 X 方案"),
        )
        val r = SessionStateCodec.render(s)
        assertTrue(r.contains("目标：排查某 bug"))
        assertTrue(r.contains("已完成：查看日志"))
        assertTrue(r.contains("待办：修改代码"))
        assertTrue(r.contains("决定：采用 X 方案"))
    }

    @Test
    fun roundTrips() {
        val s = SessionState("o", listOf("c1", "c2"), listOf("p1"), listOf("d1", "d2"))
        assertEquals(s, SessionStateCodec.parse(SessionStateCodec.render(s)))
    }

    @Test
    fun renderIsBounded() {
        val s = SessionState(
            objective = "x".repeat(2000),
            completed = (1..50).map { "任务$it" },
        )
        val r = SessionStateCodec.render(s)
        assertTrue(r.length <= SessionStateCodec.MAX_TOTAL_CHARS)
    }
}
