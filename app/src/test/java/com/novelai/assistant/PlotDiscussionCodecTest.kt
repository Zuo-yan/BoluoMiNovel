package com.novelai.assistant

import com.novelai.assistant.domain.PlotDiscussionCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotDiscussionCodecTest {

    @Test
    fun `parse extracts directions and both outline blocks cleanly`() {
        val raw = """
            接下来主角面临身份暴露的风险，你希望他优先隐忍还是借势反击？
            
            [[DIRECTIONS]]
            - 借拍卖会设局反向栽赃大长老
            1. 主动向女主角摊牌并结成同盟
            2、暂时假死脱身，潜入北境收集证据
            [[/DIRECTIONS]]
            
            [[MASTER_OUTLINE]]
            主线围绕古玉真相展开，主角需在三宗大比前集齐三枚残片。
            [[/MASTER_OUTLINE]]
            
            [[UPCOMING_PLAN]]
            下一章：拍卖会现场暗流涌动；下两章：密道交锋留下反派线索。
            [[/UPCOMING_PLAN]]
        """.trimIndent()

        val parsed = PlotDiscussionCodec.parse(raw)
        assertTrue(parsed.displayContent.startsWith("接下来主角面临身份暴露的风险"))
        assertTrue(!parsed.displayContent.contains("[[DIRECTIONS]]"))
        assertTrue(!parsed.displayContent.contains("[[MASTER_OUTLINE]]"))
        assertEquals(3, parsed.directions.size)
        assertEquals("借拍卖会设局反向栽赃大长老", parsed.directions[0])
        assertEquals("主动向女主角摊牌并结成同盟", parsed.directions[1])
        assertEquals("暂时假死脱身，潜入北境收集证据", parsed.directions[2])
        assertEquals("主线围绕古玉真相展开，主角需在三宗大比前集齐三枚残片。", parsed.proposedMasterOutline)
        assertEquals("下一章：拍卖会现场暗流涌动；下两章：密道交锋留下反派线索。", parsed.proposedUpcomingPlan)
    }

    @Test
    fun `parse degrades gracefully for plain text without protocol tags`() {
        val raw = "我认为这里可以切回反派视角先铺垫伏笔，你觉得呢？"
        val parsed = PlotDiscussionCodec.parse(raw)
        assertEquals(raw, parsed.displayContent)
        assertTrue(parsed.directions.isEmpty())
        assertNull(parsed.proposedMasterOutline)
        assertNull(parsed.proposedUpcomingPlan)
    }

    @Test
    fun `stripStreamingTags hides incomplete trailing marker mid-stream`() {
        val partial = "这里有两个关键选择：\n\n[[DIREC"
        assertEquals("这里有两个关键选择：", PlotDiscussionCodec.stripStreamingTags(partial))

        val opened = "这里有两个关键选择：\n\n[[DIRECTIONS]]\n- 方向一"
        assertEquals("这里有两个关键选择：", PlotDiscussionCodec.stripStreamingTags(opened))

        val partialTitleTag = "拟定完成：\n\n[[TIT"
        assertEquals("拟定完成：", PlotDiscussionCodec.stripStreamingTags(partialTitleTag))

        val openedTitles = "拟定完成：\n\n[[TITLES]]\n- 雨夜茶楼"
        assertEquals("拟定完成：", PlotDiscussionCodec.stripStreamingTags(openedTitles))

        val closedTitles = "拟定完成：\n\n[[TITLES]]\n- 雨夜茶楼\n[[/TITLES]]"
        assertEquals("拟定完成：", PlotDiscussionCodec.stripStreamingTags(closedTitles))
    }
}
