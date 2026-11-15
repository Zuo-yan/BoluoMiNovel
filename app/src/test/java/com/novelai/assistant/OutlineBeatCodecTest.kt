package com.novelai.assistant

import com.novelai.assistant.domain.OutlineBeat
import com.novelai.assistant.domain.OutlineBeatCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineBeatCodecTest {

    @Test
    fun `parseBeats handles standard single-line beats and checkbox variants`() {
        val plan = """
            - [ ] 1. 《雨夜截杀》 | 核心剧情：茶楼识破假使臣，借暴雨脱身 | 章末钩子：巷口遗落半枚青铜玉佩
            - [✓] 2. 《旧约余烬》 | 核心剧情：沿密道追踪刺客至废弃染坊 | 章末钩子：二楼突然亮起灯火
            - [x] 3. 《钟楼密信》 | 核心剧情：从师兄旧物中翻出二十年前名册
            * [√] 4. 《北境风雪》｜剧情：连夜出关前往北境大营｜钩子：关门外响起号角
        """.trimIndent()

        val beats = OutlineBeatCodec.parseBeats(plan)
        assertEquals(4, beats.size)

        assertEquals(0, beats[0].index)
        assertEquals(1, beats[0].displayNumber)
        assertEquals("雨夜截杀", beats[0].chapterTitle)
        assertEquals("茶楼识破假使臣，借暴雨脱身", beats[0].summary)
        assertEquals("巷口遗落半枚青铜玉佩", beats[0].hook)
        assertFalse(beats[0].isCompleted)

        assertTrue(beats[1].isCompleted)
        assertEquals("旧约余烬", beats[1].chapterTitle)
        assertEquals("二楼突然亮起灯火", beats[1].hook)

        assertTrue(beats[2].isCompleted)
        assertEquals("", beats[2].hook)

        assertTrue(beats[3].isCompleted)
        assertEquals("北境风雪", beats[3].chapterTitle)
        assertEquals("连夜出关前往北境大营", beats[3].summary)
        assertEquals("关门外响起号角", beats[3].hook)
    }

    @Test
    fun `parseBeats handles numbered bullets without checkboxes and markdown heading blocks`() {
        val mixedPlan = """
            ## 近期章节细纲
            1. 《初探染坊》：在染坊暗格找到旧令旗，却惊动巡夜暗哨。
            • 第3章《月下追逃》 - 沿屋檐甩开追兵，与女主在钟楼汇合

            ### 第4节：《残卷密码》
            主角对照半枚玉佩破译残卷上的地名。
            - 钩子：地名指向的竟是皇城禁地。

            ### 第5步 夜闯禁库
            - 剧情：借祭典混乱潜入禁库外围。
            - 章末钩子：石门后传出熟悉的声音。
        """.trimIndent()

        val beats = OutlineBeatCodec.parseBeats(mixedPlan)
        assertEquals(4, beats.size)

        assertEquals("初探染坊", beats[0].chapterTitle)
        assertEquals("在染坊暗格找到旧令旗，却惊动巡夜暗哨。", beats[0].summary)

        assertEquals(3, beats[1].displayNumber)
        assertEquals("月下追逃", beats[1].chapterTitle)
        assertEquals("沿屋檐甩开追兵，与女主在钟楼汇合", beats[1].summary)

        assertEquals(4, beats[2].displayNumber)
        assertEquals("残卷密码", beats[2].chapterTitle)
        assertEquals("主角对照半枚玉佩破译残卷上的地名。", beats[2].summary)
        assertEquals("地名指向的竟是皇城禁地。", beats[2].hook)

        assertEquals(5, beats[3].displayNumber)
        assertEquals("夜闯禁库", beats[3].chapterTitle)
        assertEquals("借祭典混乱潜入禁库外围。", beats[3].summary)
        assertEquals("石门后传出熟悉的声音。", beats[3].hook)
    }

    @Test
    fun `parseBeats falls back cleanly on legacy plain text and matches normalized existing titles`() {
        val legacyPlan = "下一章：拍卖会现场暗流涌动，主角借势反向试探大长老；下两章：密道交锋留下反派线索。"
        val beats = OutlineBeatCodec.parseBeats(
            upcomingPlan = legacyPlan,
            existingChapterTitles = setOf("AI 续写 · 第15章 拍卖会现场暗流涌动")
        )

        assertEquals(2, beats.size)
        assertTrue(beats[0].isCompleted)
        assertFalse(beats[1].isCompleted)
        assertTrue(beats[1].summary.contains("密道交锋留下反派线索"))

        val titledPlan = "- [ ] 1. 《雨夜截杀》 | 核心剧情：茶楼遇伏 | 章末钩子：半枚玉佩"
        val matched = OutlineBeatCodec.parseBeats(
            upcomingPlan = titledPlan,
            existingChapterTitles = setOf("AI 续写 · 第12章 《 雨夜 截杀 》")
        )
        assertEquals(1, matched.size)
        assertTrue(matched[0].isCompleted)
    }

    @Test
    fun `toContinuationInstruction formatBeats markBeatCompleted and appendBeats work cohesively`() {
        val beat = OutlineBeat(
            index = 0,
            displayNumber = 1,
            chapterTitle = "雨夜截杀",
            summary = "主角在茶楼识破假使臣并借暴雨脱身",
            hook = "巷口出现半枚玉佩"
        )
        val instruction = beat.toContinuationInstruction()
        assertTrue(instruction.contains("本章目标：《雨夜截杀》"))
        assertTrue(instruction.contains("剧情推进：主角在茶楼识破假使臣并借暴雨脱身"))
        assertTrue(instruction.contains("章末钩子：巷口出现半枚玉佩"))

        val formatted = OutlineBeatCodec.formatBeats(listOf(beat))
        assertEquals(
            "- [ ] 1. 《雨夜截杀》 | 核心剧情：主角在茶楼识破假使臣并借暴雨脱身 | 章末钩子：巷口出现半枚玉佩",
            formatted
        )

        val marked = OutlineBeatCodec.markBeatCompleted(formatted, beatIndex = 0, completed = true)
        assertTrue(marked.startsWith("- [✓] 1. 《雨夜截杀》"))

        val additional = """
            - [ ] 1. 《雨夜截杀》 | 核心剧情：重复节拍不应覆盖完成态
            - [ ] 2. 《旧约余烬》 | 核心剧情：潜入废弃染坊查探徽记 | 章末钩子：二楼亮起灯火
        """.trimIndent()
        val appended = OutlineBeatCodec.appendBeats(marked, additional)
        val parsedAppended = OutlineBeatCodec.parseBeats(appended)
        assertEquals(2, parsedAppended.size)
        assertEquals("雨夜截杀", parsedAppended[0].chapterTitle)
        assertTrue(parsedAppended[0].isCompleted)
        assertEquals("旧约余烬", parsedAppended[1].chapterTitle)
        assertEquals(2, parsedAppended[1].displayNumber)
        assertFalse(parsedAppended[1].isCompleted)
    }

    @Test
    fun `parseTitleSuggestions and deriveFallbackTitles extract clean candidates`() {
        val rawAiOutput = """
            以下是为您拟定的 4 个章节标题：
            [[TITLES]]
            - 《雨夜茶楼》
            1. 假使臣的破绽
            2、“半枚玉佩”
            3）暴雨脱身
            [[/TITLES]]
        """.trimIndent()

        val titles = OutlineBeatCodec.parseTitleSuggestions(rawAiOutput)
        assertEquals(listOf("雨夜茶楼", "假使臣的破绽", "半枚玉佩", "暴雨脱身"), titles)

        val fallback = OutlineBeatCodec.deriveFallbackTitles(
            draftText = "茶楼二层檐水连成珠帘，林墨指尖敲了敲桌角。对面的使臣笑意未减，袖口却滑出一截淬蓝短刃。",
            activeBeat = OutlineBeat(
                index = 0,
                displayNumber = 1,
                chapterTitle = "雨夜试探",
                summary = "茶楼识破伪装",
                hook = "门外马蹄声起"
            ),
            parentTitle = "AI 续写 · 第10章 长街夜雨"
        )
        assertTrue(fallback.isNotEmpty())
        assertEquals("雨夜试探", fallback.first())
        assertTrue(fallback.size >= 2)
    }

    @Test
    fun `parseBeats splits multi-chapter bolded paragraphs and skips section wrapper header`() {
        val realWorldPlan = """
            ### 6 章推进细纲（第198章 ~ 第203章）
            **第198章：医嘱与女仆赌约的清算** 承接第197章客厅喝茶。温妮莎用圣光替艾茜菲丝顺气，定下明日请客诺言。 **第199章：好兄弟今天怎么香喷喷的？** 温妮莎一身便装赴约牌佬社，淡香让弗迪大脑宕机。
            **第200章：全校公敌！这碗软饭凭什么他吃？！** 温妮莎豪气包下高档餐厅请客护短。
            **第201章：女仆勇者的破防时刻** 艾茜菲丝愿赌服输换上女仆装。
        """.trimIndent()

        val beats = OutlineBeatCodec.parseBeats(realWorldPlan)
        assertEquals(4, beats.size)
        assertEquals("医嘱与女仆赌约的清算", beats[0].chapterTitle)
        assertEquals(198, beats[0].displayNumber)
        assertTrue(beats[0].summary.contains("承接第197章客厅喝茶"))
        assertEquals("好兄弟今天怎么香喷喷的？", beats[1].chapterTitle)
        assertEquals(199, beats[1].displayNumber)
        assertEquals("全校公敌！这碗软饭凭什么他吃？！", beats[2].chapterTitle)
        assertEquals("女仆勇者的破防时刻", beats[3].chapterTitle)

        // 验证即使整段曾被压成单条大纲，也能拆出首章第198章并跳过总览头
        val wrappedPlan = "- [ ] 1. 《6 章推进细纲（第198章 ~ 第203章）》 | 核心剧情：**第198章：医嘱与女仆赌约的清算** 承接第197章客厅喝茶。 **第199章：好兄弟今天怎么香喷喷的？** 便装赴约牌佬社。"
        val unwrappedBeats = OutlineBeatCodec.parseBeats(wrappedPlan)
        assertEquals(2, unwrappedBeats.size)
        assertEquals("医嘱与女仆赌约的清算", unwrappedBeats[0].chapterTitle)
        assertEquals(198, unwrappedBeats[0].displayNumber)
        assertEquals("好兄弟今天怎么香喷喷的？", unwrappedBeats[1].chapterTitle)
        assertEquals(199, unwrappedBeats[1].displayNumber)
    }
}
