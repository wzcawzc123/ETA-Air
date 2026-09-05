package io.github.mangi.eta.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMemorySearchTest {

    @Test
    fun queryTermsSplitsAndDropsShort() {
        assertEquals(listOf("支付", "密码"), AgentMemorySearch.queryTerms("支付 密码 嗯"))
        assertEquals(emptyList<String>(), AgentMemorySearch.queryTerms("啊"))
        assertEquals(emptyList<String>(), AgentMemorySearch.queryTerms("   "))
    }

    @Test
    fun scoreTextCountsTermLengths() {
        assertEquals(2, AgentMemorySearch.scoreText("提到密码", listOf("密码")))
        assertEquals(0, AgentMemorySearch.scoreText("无关内容", listOf("支付")))
        assertEquals(4, AgentMemorySearch.scoreText("支付密码", listOf("支付", "密码")))
    }

    @Test
    fun searchRanksAndBounds() {
        val summaries = listOf(
            "会话1" to "讨论了支付流程和密码重置",
            "会话2" to "关于周末出行安排",
        )
        val memoryContent = "# 核心记忆\n## 支付相关\n用户偏好用大额支付"
        val hits = AgentMemorySearch.search("支付 密码", summaries, memoryContent)
        assertTrue(hits.isNotEmpty())
        val top = hits.first()
        assertTrue(top.score >= 4)
        assertTrue(hits.size <= AgentMemorySearch.MAX_HITS)
        // 无匹配词
        assertEquals(emptyList<AgentMemorySearch.Hit>(), AgentMemorySearch.search("zzz不存在", summaries, memoryContent))
        // 空查询
        assertEquals(emptyList<AgentMemorySearch.Hit>(), AgentMemorySearch.search(" ", summaries, memoryContent))
    }

    @Test
    fun searchSearchesMemoryBodyNotOnlyHeadings() {
        val summaries = listOf("会话1" to "日常闲聊")
        val memoryContent = "# 核心记忆\n## 偏好\n用户密码设置为 abc123\n用户住在广州"
        // 正文（非标题）行可命中
        val bodyHits = AgentMemorySearch.search("密码", summaries, memoryContent)
        assertTrue(bodyHits.any { it.source == AgentMemorySearch.SOURCE_MEMORY && it.snippet == "用户密码设置为 abc123" })
        // 标题行检索仍保留
        assertTrue(AgentMemorySearch.search("偏好", summaries, memoryContent).any { it.snippet == "## 偏好" })
    }

    @Test
    fun searchIsCaseInsensitive() {
        val summaries = listOf("c1" to "Payment gateway discussion")
        assertTrue(AgentMemorySearch.search("payment", summaries, "").isNotEmpty())
    }

    @Test
    fun searchSummaryHitCarriesConversationLabel() {
        val summaries = listOf(
            "conv-abc" to "讨论了支付流程和密码重置",
            "conv-def" to "关于周末出行安排",
        )
        val hits = AgentMemorySearch.search("支付", summaries, "# 核心记忆")
        val hit = hits.first { it.source == AgentMemorySearch.SOURCE_SUMMARY && it.snippet.contains("支付") }
        assertEquals("conv-abc", hit.conversationLabel)
        // MEMORY 命中不含来源会话（conversationLabel 为 null）
        val memHit = AgentMemorySearch.search("支付", summaries, "# 核心记忆\n用户偏好用大额支付")
            .first { it.source == AgentMemorySearch.SOURCE_MEMORY }
        assertEquals(null, memHit.conversationLabel)
    }

    @Test
    fun queryTermsAddsNgramsForChineseLongTokens() {
        val terms = AgentMemorySearch.queryTerms("用户偏好在广州")
        assertTrue(terms.contains("用户偏好在广州"))
        assertTrue(terms.contains("用户"))
        assertTrue(terms.contains("广州"))
    }
}
