package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAutoConsolidatorTest {

    @Test
    fun parsePlanMergesOnlyGroupsOfAtLeastTwoValidLines() {
        val text = "MERGE 2,5: 用户是一名AI产品经理\n" +
            "MERGE 1: 单行应被忽略\n" +
            "MERGE 99,3: 越界行组应忽略整组"
        val plan = MemoryAutoConsolidator.parsePlan(text, existingLineCount = 5)
        assertEquals(1, plan.merges.size)
        assertEquals(listOf(2, 5), plan.merges.single().sourceLines)
        assertEquals("用户是一名AI产品经理", plan.merges.single().canonical)
    }

    @Test
    fun parsePlanHandlesNoMerges() {
        val plan = MemoryAutoConsolidator.parsePlan("没有重复\nMERGE 1,2:", 5)
        assertEquals(0, plan.merges.size)
    }

    @Test
    fun buildPromptListsLinesAndRequiresMinTwo() {
        val prompt = MemoryAutoConsolidator.buildPrompt(listOf("- [沉淀] 用户住在上海", "- [沉淀] 用户住在北京"))
        assertTrue(prompt.contains("1: 用户住在上海"))
        assertTrue(prompt.contains("2: 用户住在北京"))
        assertTrue(prompt.contains("至少 2 行"))
    }
}
