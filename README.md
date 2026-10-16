# 📱 AI 智阅小说 (NovelAI Assistant)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-purple.svg?style=flat-square" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-blue.svg?style=flat-square" alt="UI" />
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean%20Architecture-orange.svg?style=flat-square" alt="Architecture" />
  <img src="https://img.shields.io/badge/BYOK-Privacy%20First-red.svg?style=flat-square" alt="BYOK" />
  <img src="https://img.shields.io/badge/Latest%20Version-v0.16.0-success.svg?style=flat-square" alt="Version" />
</p>

---

## 📖 项目简介

### v0.16.0：菠萝包专页与四协议模型设置

- 底栏「菠萝包」以封面书架为主，点书直接继续阅读；账号为紧凑入口，书号／链接在页头加号，番茄、下载任务及账号缓存管理在右上角「更多」。书籍菜单提供独立目录、下载与导出。
- 原生 Kotlin 支持 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages、Google Gemini，分别处理请求、SSE 正文、取消、错误和累计用量。
- 提供 DeepSeek、Anthropic、OpenAI、Google、智谱 AI（Z.ai）、阿里云百炼、Moonshot、xAI、腾讯混元九家预设；添加时明确选择平台／地域，也可配置自定义和本地服务。协议和地址均可编辑。
- 「单次最大输出 Token」可自动识别或手动覆盖，优先当前接口元数据，再用精确匹配的内置官方资料；未知时保留当前值。上下文容量单独显示，识别结果标明来源与时间，不使用长篇生成探测上限。
- 旧配置保留此前实际使用的 Chat 协议及输出参数；书籍数据库保持非破坏性迁移。具体测试与限制见 [v0.16.0 验证记录](docs/v0.16.0-verification.md)。

### v0.15.0：菠萝包账号书架与在线阅读

- 在「发现 → 菠萝包 SF」使用账号/手机号和密码登录。App 会话通过 Android Keystore 加密保存；密码只用于本次登录，不落盘。原网页 Cookie 输入入口已移除。
- 登录后读取账号书架及分类；点选作品或输入官方书籍链接打开目录，进入原生阅读器按章加载。只读平台数据，阅读进度保存在本应用。
- 免费和账号已有购买权限的章节可在线阅读、听书、缓存及下载。遇到安全验证（1148），请先在官方菠萝包 App 完成，再回来重新登录。应用不会购买章节。
- 阅读器右上角「更多 → 下载与导出」支持本章、范围和全书下载，以及 TXT / Markdown / EPUB 导出。缺章时列出未购和失败章节，明确确认后才能导出不完整版本；文件附有缺章说明。
- 任务可暂停、继续或重试；成功正文持久化保存，重新打开应用可继续上次任务。任务属于应用进程，系统结束进程后需要手动继续。
- 自动缓存已读正文；发现页可查看及清理缓存，清理时保留主动下载。退出账号清除会话和远端书架信息，保留本地正文和进度；可在本地书架删除书籍。
- Room 1 → 2 使用非破坏性迁移，并清理旧版被误存为正文的抓取错误提示。AI 伴读检索只使用本地已有正文，不自动下载全书。

验证命令与协议来源见 [菠萝包接入说明](docs/sfacg-integration.md)。实际账号登录、私有书架与已购 VIP 验收需要由账号持有人在应用内完成；匿名公开接口验证不能替代这一验收。

**AI 智阅小说（NovelAI Assistant）** 是一款面向数字阅读爱好者的现代全格式电子书阅读器，深度融合了**上下文长文本 AI 伴读助手（RAG/Agent）**与**沉浸式 AI 连续续写及平行世界分支推演**能力。

应用坚持 **BYOK (Bring Your Own Key)** 原则，作为纯本地客户端运行，无需第三方中继服务器。用户自主填入大模型 API Key（兼容所有 OpenAI 规范接口），敏感数据使用 **Android Keystore 加密存储于本地**，杜绝数据外泄与账号跑路风险。

整体视觉遵循 **iOS Human Interface Guidelines (HIG)** 设计美学，融合 Squircle 大圆角矩形、实时高斯模糊毛玻璃（Frosted Glass）、弹性物理动效与深度护眼的全深色模式。

---

## ✨ 核心特性矩阵

### 1. 📚 沉浸式阅读引擎 (Reader Engine)
- **多格式导入支持**：无缝支持本地 `.txt`、`.epub`、`.md` 等电子书解析与阅读。
- **双阅读排版模式**：
  - **仿真左右翻页（Paged，默认）**：重写高精度换行与分页算法，杜绝多段落死锁排版故障；支持屏幕左右点击翻页、跨章平滑过渡与首末页手势阻尼。
  - **无缝上下滚动（Scroll）**：平滑垂直连续长卷阅览。
- **高自由度排版定制**：亮度、字号（A-/A+）、行距、边距、段距自由微调；内置衬线体（Serif）与无衬线体一键切换。
- **5 套经典阅读主题**：白天纯白、羊皮纸米黄、豆沙绿护眼、暗夜深蓝、OLED 极暗纯黑。
- **目录抽屉与长按章节管理**：
  - 智能正则提取目录与原生 EPUB 目录映射；
  - 支持 **[全部] / [仅原作] / [AI衍生] / [分支树]** 四维目录筛选；
  - 目录内**长按任意章节**呼出操作菜单，支持章节**重命名**与**安全永久删除**。

### 2. 🎧 智能语音听书与系统媒体控制 (TTS & Media Controls)
- **系统级媒体卡片组件 (System Media Controls)**：
  - 接入 Android 原生 **MediaSession** 与 **MediaStyle** 前台媒体服务（`foregroundServiceType="mediaPlayback"`）；
  - 系统下拉通知中心、锁屏大号胶囊及各类定制系统（MIUI/HyperOS、ColorOS、OriginOS、OneUI）原生展示精美媒体播放器卡片；
  - **后台与息屏保活**：切出应用、灭屏锁屏时像听音乐一样稳定持续播放，支持通知栏与蓝牙耳机直接进行**播放/暂停、上一章、下一章、停止**；
  - 点击通知栏卡片一键平滑跳转并恢复当前书籍阅读器。
- **听书定时停止 (Sleep Timer)**：
  - 支持 **15 / 20 / 30 / 45 / 60 / 90 分钟** 倒计时自动停止；
  - 支持睡前专属**「听完本章停止」**模式；
  - 支持**「不设置（听到全书完）」**，持续自动跨章连播直至全书终章。
- **智能选段与段落高亮跟随**：
  - 支持类似番茄小说的**从当前屏幕所在页首段直接起播**；
  - 正文长按任意文字片段呼出浮层，支持**「从此处朗读」**；
  - 听书朗读期间对应段落实时以**柔和紫色微光高亮**跟随，自动驱动仿真翻页连播。
- **听书控制胶囊**：底部常驻播放/暂停切换、停止按钮、**实时倒计时胶囊**与 **0.75x ~ 2.0x 无级语速调节**。

### 3. 🤖 AI 深度伴读与划线即问 (RAG & Chat)
- **划线即问浮动胶囊**：阅读过程中长按任意文字片段，即时呼出操作胶囊：
  - **「解析深意」**：剖析选段伏笔、行文手法与隐喻；
  - **「情绪总结」**：解析段落情感基调与人物内心；
  - **「AI 吐槽」**：以幽默辛辣口吻即兴吐槽。
- **独立 AI 伴读助手主页**：
  - 支持**情节回忆**（回忆前文线索）、**人物图谱分析**（人物关系与生平）与**剧情深读**；
  - **原生文本自由选择**：采用原生 `SelectionContainer`，长按自由框选与复制，杜绝粗暴自动复制整段；
  - **即时交互视觉反馈**：发送后展现思考动画气泡、输入框顶部线性进度条与流式输出动态光标；
  - 提供**清空聊天记录**功能（含二次确认对话框）。

### 4. ⚡ AI 连续续写与平行世界分支 (Continuation & Branching)
- **全书最新章追踪**：续写默认开启「⚡ 全书最新章」直接追踪模式，无需在长篇目录中反复寻找末尾章节；亦可自由切换指定章节。
- **连续续写模式**：新章生成后支持一键「保存并续写下一章」，自动将新章作为下一轮创作前驱，顶部配有创作链路进度条。
- **因果世界线追溯算法（`getChapterLineage`）**：分支推演严格向上溯源至该分支归属的历史世界线祖先节点，剔除无关主线噪音，保证设定连贯。
- **剧情分支树（Branch Tree）**：树状可视化展现主线与平行宇宙衍生分支，节点支持一键「续写此分支」或「创建新分支」。
- **章节身份标识**：`[原]` 原作章节、`[AI]` AI 续写章节、`[分]` 平行分支章节。

### 5. 🔑 BYOK 多模型中心与任务分流 (Model Routing)
- **九家厂商预设与平台选择**：DeepSeek、Anthropic、OpenAI、Google、智谱、阿里云百炼、Moonshot、xAI、腾讯混元；保留自定义入口，硅基流动、Ollama 及中转站可填写各自地址。
- **四种独立 API 协议**：Chat Completions、Responses、Anthropic Messages、Gemini，支持自定义 Base URL、Key、模型 ID 和请求头。
- **真实生效的多场景分流**：
  - 为**续写场景**指定长文本拟合能力强的高阶模型（如 Claude 3.5 Sonnet / DeepSeek-V2.5）；
  - 为**速览伴读场景**指定响应极速的轻量模型（如 GPT-4o-mini / DeepSeek-Lite）。
- **实用配置支持**：横向平滑滚动服务商卡片、一键设为默认、连通性 Ping 测试、自动获取模型列表、顶部常驻使用文档指南、清空用量统计防误触二次确认。

### 6. 🎨 深度全深色模式与主题适配 (Dark Mode)
- **完善的 Material 3 暗色色彩规范**：补全完整 `surfaceContainer*` 容器色系，消除弹出浅色底板的晃眼隐患。
- **双重深色模式感知**：无论是**系统开启深色模式**还是**阅读器选择暗夜/极暗背景**，弹出的阅读设置面板、长按章节菜单、重命名/删除弹窗、划线选段操作条均自动无缝适配为舒适暗黑质感。

---

## 🛠️ 技术架构与选型

| 层次 | 技术选型 | 说明 |
| :--- | :--- | :--- |
| **语言** | Kotlin 100% | 现代 Android 官方首选语言，协程与 Flow 异步流支持 |
| **UI 框架** | Jetpack Compose + Material 3 | 声明式组件化，搭配自定义 Shape、Blur 实现 iOS 风格质感 |
| **架构模式** | MVVM / MVI + Clean Architecture | 表现层、业务逻辑层与数据持久层清晰解耦 |
| **依赖注入** | Hilt (Dagger) | 规范化依赖注入与生命周期绑定 |
| **本地数据库** | Room DB + Paging 3 | 书籍、章节目录、分支树、阅读进度与对话记录本地持久化 |
| **敏感加密存储** | Android Keystore | 安全加密存储各模型厂商 API Key，本地私密绝不外泄 |
| **用户偏好** | Jetpack DataStore Preferences | 存储阅读器排版、字号、语速与主题模式 |
| **毛玻璃与视觉** | Haze (dev.chrisbanes.haze) | 实时高性能高斯模糊毛玻璃背景穿透效果 |
| **网络通信** | OkHttp + SSE (Server-Sent Events) | 通用 OpenAI 兼容协议流式打字机交互响应 |

---

## 📂 项目结构概览

```text
app/src/main/java/com/novelai/assistant/
├── data/
│   ├── api/            # OpenAI 兼容协议 SSE 流式客户端与模型拉取
│   ├── db/             # Room 数据库实体 (Book, Chapter, AiChat) 与 DAO
│   ├── importer/       # TXT / EPUB / MD 本地文件解析器
│   ├── prefs/          # DataStore 阅读排版配置与主题持久化
│   ├── repository/     # 统一数据仓储层 (Book, Api, ReadingPreferences)
│   └── tts/            # Android 原生 TTS 听书播放管理器
├── domain/             # 业务用例与 Prompt 提示词工程构建器
├── ui/
│   ├── assistant/      # AI 伴读与解析主界面
│   ├── bookshelf/      # 书架、书单分类与书籍卡片
│   ├── components/     # 大标题、分段器、毛玻璃、Badge等公共组件
│   ├── continuation/   # AI 续写与世界线推演
│   ├── discover/       # 在线书源抓取 (番茄 / 菠萝包)
│   ├── novel/          # 应用路由宿主与全屏导航容器
│   ├── reader/         # 核心阅读器界面、分页引擎与设置面板
│   ├── settings/       # BYOK 模型中心、任务分流与提供商编辑
│   ├── theme/          # iOS HIG 调色板、排版、Shape 与全深色模式
│   └── tree/           # 平行世界剧情分支树可视化
└── MainActivity.kt     # 单 Activity 入口与 Edge-to-Edge 边到边沉浸式支持
```

---

## 🚀 快速上手与编译构建

### 环境要求
- **Android Studio**：Ladybug (2024.2.1) 或更高版本
- **JDK**：OpenJDK 17
- **Android SDK**：Compile SDK 34，Min SDK 26 (Android 8.0+)
- **Gradle**：8.7+

### 本地编译步骤
1. **克隆仓库**：
   ```bash
   git clone https://github.com/Zuo-yan/NovelAIAssistant.git
   cd NovelAIAssistant
   ```

2. **编译 Debug 版**：
   ```bash
   ./gradlew assembleDebug
   # 产物输出于：app/build/outputs/apk/debug/app-debug.apk
   ```

3. **编译 Release 版（安装包）**：
   ```bash
   ./gradlew assembleRelease
   # 产物输出于：app/build/outputs/apk/release/app-release.apk
   ```

4. **安装至连接的手机或安卓模拟器**：
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

---

## 📱 使用指南

1. **导入书籍**：
   - 首次进入主界面点击右上角「导入」按钮，选取本地 `.txt` 或 `.epub` 小说；
   - 系统将自动解析章节目录并加入书架。
2. **配置大模型 (BYOK)**：
   - 前往底部「设置」→「模型与 API」→ 点击「添加提供商」；
   - 填入您的 Base URL（如 `https://api.deepseek.com/v1`）与 API Key；
   - 点击右上角「📖 使用文档」可随时查阅各参数详细说明；
   - 在「任务分流」中为续写与伴读分别指定合适的模型。
3. **沉浸式阅读与听书**：
   - 点击书架书籍进入阅读器，点击屏幕左/右侧翻页，点击中间呼出菜单栏；
   - 点击底部「听书」即可从当前页首段自动语音朗读；
   - 长按任意文字片段呼出快捷浮层，一键「从此处朗读」或「解析深意」。
4. **AI 连续续写与平行分支**：
   - 点击底栏「AI 续写」自动定位最新章节，输入导向设定即可生成后续章节；
   - 生成后点击「保存并续写下一章」可无缝连续推演；
   - 点击顶部「分支树」可查看剧情分叉节点，随时推演全新世界线。

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源协议发布，欢迎 Fork、Star 与提交 PR！
