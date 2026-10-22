# 📱 菠萝蜜小说（BoluoMiNovel）应用架构与需求规格说明书

---

## 1. 产品定位与核心设计哲学

### 1.1 核心定位
**菠萝蜜小说（BoluoMiNovel）** 是一款面向菠萝包轻小说与漫画的 Android 阅读器，融合账号书架、章节阅读、间贴互动，以及 BYOK 多模型 AI 伴读、续写和剧情分支。

### 1.2 商业与部署模式（BYOK 模式）
- **BYOK (Bring Your Own Key)**：客户端作为纯本地运行的强交互载体，不设中心化转发收费中继服务。
- 用户可自主填入第三方大模型提供商的 API Key（兼容 OpenAI 规范的各类中转站、开源部署、商业闭源模型）。
- 隐私完全归属于用户本地端，无平台跑路或账户充值扣点顾虑。

### 1.3 设计语言：iOS Human Interface Guidelines 风格
- **拟物与玻璃拟态（Materials & Blur）**：大圆角矩形（Squircle）、高斯模糊透明毛玻璃（Acrylic/Frosted Glass 背景）、沉浸式沉底标题栏（Large Title）。
- **动效体系**：类 iOS 弹性物理回弹（Spring & OverScroll）、右滑手势平滑过渡返回、微触觉震动反馈（Haptic Feedback）。
- **排版系统**：精致的衬线体与非衬线体切换支持、高舒适度阅读行间距与呼吸感边距。

---

## 2. 总体系统架构设计

```text
 ┌─────────────────────────────────────────────────────────────────────────────┐
 │                       表现层 (Jetpack Compose UI)                           │
 │     书架视图  │  沉浸阅读器  │  AI伴读悬浮窗  │  剧情分支树  │  设置与BYOK配置      │
 └──────────────────────────────────────┬──────────────────────────────────────┘
                                        │ (State / Intent)
 ┌──────────────────────────────────────┴──────────────────────────────────────┐
 │                      业务逻辑层 (ViewModel / UseCases)                      │
 ├──────────────────┬──────────────────┬───────────────────┬───────────────────┤
 │   书架管理用例   │   阅读排版引擎   │    AI 伴读与解析  │   AI 续写与分支   │
 └────────┬─────────┴────────┬─────────┴─────────┬─────────┴─────────┬─────────┘
          │                  │                   │                   │
 ┌────────┴────────┐ ┌───────┴─────────┐ ┌───────┴─────────┐ ┌───────┴─────────┐
 │   本地数据仓储  │ │ 文件解析与转换  │ │ 本地 RAG / 检索 │ │ 抓取器协议适配  │
 │ Room + DataStore│ │ TXT/EPUB/MD/PDF │ │ Chunking+Vector │ │ 番茄/菠萝包原生 │
 └────────┬────────┘ └─────────────────┘ └───────┬─────────┘ └─────────────────┘
          │                                      │
 ┌────────┴──────────────────────────────────────┴─────────────────────────────┐
 │                      BYOK 外部通信与安全模型服务层                          │
 │  Android Keystore 加密 · SSE 流式网络连接 · OpenAI/Claude 协议自适应转换    │
 └─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 系统详细功能模块设计

### 模块 1：BYOK 自定义 API 与多模型管理系统
1. **多服务商预设与零门槛接入**：
   - 预设厂商模版：OpenAI、Anthropic Claude、DeepSeek、Moonshot (Kimi)、SiliconFlow (硅基流动)、Ollama (本地/局域网服务) 等。
   - **完全自定义兼容（OpenAI API 协议规范）**：
     - **Base URL**：支持任意第三方中转站/聚合平台（如 OneAPI、NewAPI），默认 `https://api.openai.com/v1`。
     - **API Key**：支持密文遮罩、一键粘贴与复制验证。
     - **Model Name**：支持手动填入或通过 `/v1/models` 端点一键拉取模型列表。
2. **多场景模型分流配置**：
   - 允许用户为不同任务指定不同模型以兼顾成本与效果：
     - **续写场景**：倾向于长上下文拟合与文风模仿能力强的模型（如 Claude 3.5 Sonnet、DeepSeek-V2.5）。
     - **速览与伴读场景**：倾向响应速度极快、成本极低的模型（如 GPT-4o-mini、DeepSeek-V2-Lite）。
3. **连接诊断与用量统计**：
   - 提供“连通性一键测试（Ping）”按钮，即时验证网络代理与 Key 的有效性。
   - 客户端统计历史调用花费 Token 估算。

### 模块 2：书架与分类管理 (Bookshelf Module)
1. **书籍展示与排序**：
   - 瀑布流、双列网格、列表等多种视图自由切换。
   - 包含封面展示、阅读进度百分比及上次阅读时间等元数据。
2. **章节分类与标识体系**：
   - **标识规则**：每章在目录列表与阅读器顶部均有清晰元数据标签：
     - `[原]` 原作章节（官方章节）
     - `[AI]` AI 续写章节
     - `[分]` AI 分支衍生章节（Parallel Universe）
   - **分类视图**：目录页支持筛选切换——“全部章节”、“仅原作”、“仅 AI 衍生”、“分支剧情树”。
3. **书单标签化管理**：
   - 支持新建自定义书单/分类标签（如：奇幻、已读完、追更中、AI 推演中）。

### 模块 3：阅读器核心引擎 (Reader Engine)
1. **主流格式支持**：
   - 本地导入格式：`.txt`、`.epub`、`.md`（Markdown）、`.pdf`。
2. **阅读体验定制**：
   - 翻页模式（仿真翻页、平滑覆盖、无缝上下滚动）。
   - 字号、行距、边距自适应调节；暗黑模式、护眼羊皮纸底色、白天高对比度。
3. **目录与书签**：
   - 智能正则匹配自动提取 TXT 章节目录；EPUB 原生目录映射。

### 模块 4：小说抓取器工具集成 (Downloader Integration)
项目集成两款开源工具的逆向抓取能力：
- **番茄小说下载器**：
  - HTTP 仓库：`https://github.com/ying-ck/fanqienovel-downloader.git`
  - CLI: `gh repo clone ying-ck/fanqienovel-downloader`
- **菠萝包轻小说下载器**：
  - HTTP 仓库：`https://github.com/XueHua-s/sfacgSaveForMarkdown.git`
  - CLI: `gh repo clone XueHua-s/sfacgSaveForMarkdown`

#### Agent 落地实现建议方案：
1. **方案 A（纯客户端原生移植 - 推荐）**：由 Agent 分析开源仓库中的核心逆向逻辑（API 路由、签名算法、Token 机制、解密过程），将其移植为 Kotlin 原生网络请求模块，直接输出规范的 Markdown/EPUB 文件。
2. **方案 B（本地嵌入式运行环境/微服务）**：若逆向算法复杂，在 Termux 环境打包或配置局域网/私有部署 API 服务作为代理中继。

### 模块 5：AI 深度伴读与解析 (RAG & Character Analysis)
1. **长上下文/分块向量化（RAG）**：
   - 本地对导入的小说进行章节/段落切分（Chunking），提取关键实体（人物、地点、事件、时间线）。
   - 结合多轮对话，让 AI 做到“不剧透伴读”或“全书回忆模式”。
2. **核心伴读功能**：
   - **情节回忆**：如“帮我回忆一下第 120 章主角拿到的神秘戒指是什么来历？”
   - **人物图谱/关系分析**：选择角色，AI 动态生成该角色的生平脉络、战力阶段、与其他角色的人际关系。
   - **划线即问**：长按小说某段文本，呼出浮动菜单：“AI 解析深意”、“AI 情绪总结”、“AI 吐槽”。

### 模块 6：AI 续写与剧情分支 (AI Continuation & Branching)
1. **多模式续写**：
   - **单章续写**：基于当前章节末尾自动延展下一章。
   - **分支假设（What-if）**：用户在任意段落点击“剧情走向重构”（例如：“如果这里主角没有选择救女二，剧情会怎样？”），AI 派生出平行宇宙分支章节。
   - **风格拟合（Prompt Engineering）**：读取前 3-5 章的行文风格、语气词、修辞习惯，作为 Few-Shot Prompt，保持原汁原味。
2. **版本控制与合并**：
   - 续写章节可由用户微调编辑，保存为正式新章节，或导出为独立的 Markdown/TXT 文件。

### 模块 7：引导与交互系统 (User Onboarding & Tutorial)
1. **iOS 风格新手引导 (Welcome Sheet)**：
   - 首次启动展示卡片式交互教程：包含如何获取并填入自己的 API Key 指南、如何导入书籍、如何使用下载工具、如何唤醒 AI 伴读气泡。
2. **内置功能指南与 FAQ**：设置页提供图文交互教程和快捷键/手势操作提示。

---

## 4. 技术栈选型方案

| 层次 | 推荐技术方案 | 选型理由 |
| :--- | :--- | :--- |
| **语言** | Kotlin (100%) | 官方现代首选语言，协程与 Flow 处理异步流更强 |
| **UI 架构** | Jetpack Compose + Material 3 | 便于通过自定义 Shape、Blur、Animation 实现高品质 iOS 拟态 |
| **架构模式** | MVVM / MVI + Clean Architecture | 逻辑分层明确，非常适合 AI Agent 分模块分工生成代码 |
| **本地数据库** | Room DB + Paging 3 | 管理书架书籍、章节列表、阅读记录、AI 生成标记及分支树 |
| **敏感信息与配置** | **EncryptedSharedPreferences** / Keystore | **安全加密存储用户的 API Key**，防止明文泄露 |
| **偏好存储** | Jetpack DataStore | 保存用户阅读偏好、UI 设置、非敏感模型参数 |
| **文件解析** | FolioReader-Android / epublib / 自定义正则 | 负责解析 EPUB、TXT、Markdown |
| **AI 网络通讯** | Ktor Client / Retrofit + OkHttp SSE | 实现通用的 OpenAI 协议反向代理请求与流式响应（Server-Sent Events） |
| **本地向量/检索** | ObjectBox / Sqlite-vec / Chroma Client | 支撑长文切片检索与上下文召回 |

---

## 5. 核心数据库与模型设计 (Data Schema)

```kotlin
// 1. 模型提供商与 API 配置模型 (BYOK)
data class ApiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val providerName: String,          // 例如 "OpenAI", "DeepSeek", "Custom"
    val baseUrl: String,               // 接口地址，例如 "https://api.deepseek.com/v1"
    val apiKey: String,                // 密钥 (写入/读取需走 Keystore 加密)
    val selectedModel: String,         // 模型名，例如 "deepseek-chat"
    val customHeaders: Map<String, String> = emptyMap(),
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val isEnabled: Boolean = true
)

// 2. 书籍实体
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String,
    val coverUri: String?,
    val sourcePath: String,
    val sourceType: BookSourceType,    // LOCAL_TXT, LOCAL_EPUB, FANQIE, BOLUOBAO
    val totalChapters: Int,
    val currentReadingChapterIndex: Int,
    val readingProgressPercent: Float,
    val category: String = "默认",
    val createdAt: Long = System.currentTimeMillis()
)

// 3. 章节实体（包含原著与 AI 分类）
@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val content: String,
    val originType: ChapterOriginType, // ORIGINAL(原作), AI_CONTINUATION(续写), AI_BRANCH(分支)
    val parentChapterId: String? = null, // 若是分支章节，记录分支来源父节点
    val promptUsed: String? = null,       // AI 续写时使用的指令与设定
    val isFavorite: Boolean = false
)

// 4. AI 伴读对话记录
@Entity(tableName = "ai_chat_records")
data class AiChatRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bookId: String,
    val chapterId: String?,
    val role: String,                   // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## 6. Agent 实施路线图 (Step-by-Step Roadmap)

- **Phase 1: 基础框架与 UI 骨架**
  - 搭建 Android 单 Activity + Compose 架构，配置 iOS 风格主题与配色系统。
  - 实现底部导航栏（书架、发现/下载、AI 助手、设置）及页面切换动画。
- **Phase 2: BYOK 配置中心实现**
  - 实现 iOS 样式的“模型与 API 设置”页面，接入 `EncryptedSharedPreferences` 安全保存 Key。
  - 编写通用的 OpenAI API SSE 客户端，完成连通性测试（Ping）逻辑。
- **Phase 3: 书架与阅读器引擎**
  - 实现本地文件选择器，支持 TXT/EPUB 解析入库。
  - 完成阅读器界面：分页排版、字号间距调整、章节目录侧滑抽屉。
- **Phase 4: 章节分类与 AI 续写模块**
  - 在数据库中落地 `ChapterOriginType` 分类标识。
  - 基于用户配置的 API Key 调用流式响应，实现“一键续写”与“分支剧情”创建。
- **Phase 5: 小说下载集成与解析**
  - 分析番茄小说与菠萝包下载工具的协议/接口逻辑，编写 Kotlin 抓取器模块，将返回内容直接转换为章节库数据。
- **Phase 6: RAG 长文解析与人物记忆系统**
  - 实现章节内容的分块（Chunking）与上下文窗口管理，提供“角色生平查询”与“前文剧情问答”功能。
- **Phase 7: 新手教程引导与细节打磨**
  - 添加首次进入的卡片式引导页（含如何获取和配置 API Key 教程）。
  - 全局添加弹性回弹（OverScroll）、触觉反馈（Haptic Feedback）等拟 iOS 交互细节。
