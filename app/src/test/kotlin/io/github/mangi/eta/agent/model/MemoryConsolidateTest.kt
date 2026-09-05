package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryConsolidateTest {

    @Test
    fun apply_mergesLinesIntoCanonical() {
        val lines = listOf(
            "- [沉淀] 用户是陈嘉麟",
            "- [沉淀] 用户从事销售技术支持",
            "- [沉淀] 用户住在上海",
        )
        val plan = ConsolidatePlan(
            merges = listOf(
                ConsolidateMerge(listOf(1, 2), "- 用户是陈嘉麟，从事销售技术支持"),
            ),
        )
        assertEquals(
            listOf("- 用户是陈嘉麟，从事销售技术支持", "- [沉淀] 用户住在上海"),
            MemoryConsolidate.apply(plan, lines),
        )
    }

    @Test
    fun apply_isOrderStableWithMultipleGroups() {
        val lines = listOf("A", "B", "C", "D", "E")
        val plan = ConsolidatePlan(
            merges = listOf(
                ConsolidateMerge(listOf(1, 2), "AB"),
                ConsolidateMerge(listOf(4, 5), "DE"),
            ),
        )
        assertEquals(listOf("AB", "C", "DE"), MemoryConsolidate.apply(plan, lines))
    }

    @Test
    fun apply_skipsInvalidRanges() {
        val lines = listOf("A", "B")
        val plan = ConsolidatePlan(merges = listOf(ConsolidateMerge(listOf(0, 9), "Z")))
        assertEquals(listOf("A", "B"), MemoryConsolidate.apply(plan, lines))
    }

    @Test
    fun normalizeLine_stripsMarkers() {
        assertEquals("用户是陈嘉麟", MemoryConsolidate.normalizeLine("- [沉淀] 用户是陈嘉麟"))
    }
}
