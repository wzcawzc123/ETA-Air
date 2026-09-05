package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryDistillRulesTest {

    @Test
    fun newFactsBecomeAdditions() {
        val plan = MemoryDistillRules.plan(
            extracted = listOf("用户偏好中文", "用户住在上海"),
            existingLines = listOf("- [沉淀] 用户偏好使用 VS Code"),
        )
        assertEquals(listOf("用户偏好中文", "用户住在上海"), plan.additions)
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.noop.isEmpty())
    }

    @Test
    fun duplicateFactBecomesNoop() {
        val plan = MemoryDistillRules.plan(
            extracted = listOf("用户偏好中文"),
            existingLines = listOf("- [沉淀] 用户偏好中文"),
        )
        assertTrue(plan.additions.isEmpty())
        assertEquals(listOf("用户偏好中文"), plan.noop)
    }

    @Test
    fun moreCompleteFactUpdatesExistingLine() {
        val plan = MemoryDistillRules.plan(
            extracted = listOf("用户正在开发一个游戏项目，用 Kotlin"),
            existingLines = listOf("- [沉淀] 用户正在开发一个游戏项目"),
        )
        assertTrue(plan.additions.isEmpty())
        assertEquals(1, plan.updates.size)
        assertEquals(1, plan.updates.first().startLine)
        assertEquals("用户正在开发一个游戏项目，用 Kotlin", plan.updates.first().content)
    }

    @Test
    fun lineIndicesAreOneBasedAndRespectExistingOrder() {
        // 新内容更长且包含旧行内容 → 覆盖旧的对应行；行号按 1-based 且尊重顺序
        val plan = MemoryDistillRules.plan(
            extracted = listOf("用户正在开发一个游戏项目，用 Kotlin"),
            existingLines = listOf("第1行", "- [沉淀] 用户正在开发一个游戏项目", "第3行"),
        )
        assertEquals(
            listOf(MemoryDistillRules.Update(2, "用户正在开发一个游戏项目，用 Kotlin")),
            plan.updates,
        )
    }
}
