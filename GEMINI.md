# 菠萝蜜小说 (BoluoMiNovel) · 项目开发全局记忆与工程规范

> 本文件是本项目的全局记忆核心，在云端与本地环境通用。每次开发任务开始时必读，开发任务结束时遵循规范闭环。

---

## 📌 核心准则：版本号管理规范（强制执行）

**每次进行完功能开发、Bug 修复或体验优化，并验证测试通过后，必须同步递增版本号：**

1. **更新 `app/build.gradle.kts`**：
   - `versionCode`：必须递增 +1（例如 `42` -> `43`）。
   - `versionName`：按语义化版本规则更新（例如 Bug 修复小版本 `0.32.6` -> `0.32.7`；新特性大版本递增次版本号如 `0.33.0`）。
2. **Git Commit 规范**：
   - 提交信息必须体现当前版本号，例如：
     - `fix(reader): 修复深色模式控制菜单与文本对比度 (v0.32.7)`
     - `feat(ai): 增加任务分流模型推荐与写小说技能 (v0.33.0)`
3. **打包与归档**：
   - 产物归档时必须包含版本号命名，例如 `BoluoMiNovel-v{versionName}-release.apk` 与 `BoluoMiNovel-v{versionName}-debug.apk`。

---

## 🛠️ 项目架构与技术规范

1. **核心技术栈**：
   - **语言与平台**：Kotlin 2.0+ / Android SDK 36 (minSdk 26, targetSdk 36) / JDK 17/21。
   - **UI 框架**：Jetpack Compose + Material 3 + Compose Navigation。
   - **架构设计**：标准 Google MVVM + Hilt 依赖注入 + Kotlin Coroutines & Flow (StateFlow / collectAsStateWithLifecycle)。
   - **持久化**：Room Database（书籍、章节、聊天记录）+ Preferences DataStore（阅读偏好、主题设置、听书配置）。
   - **安全凭据**：Android Keystore 本地加密存储（用户 BYOK 密钥、平台 Session）。

2. **UI 视觉与设计语言**：
   - **液态玻璃（Liquid Glass）**：通透中性透镜物理折射与磨砂质感，去除高耗能彩色动态眩光。
   - **低饱和度风格**：中性近黑近白表面配合精细点缀色（iOS 靛蓝 `#5856D6`、薄荷绿、琥珀色、樱粉色）。
   - **深浅色联动**：
     - 设置界面切换「深色」时，阅读器背景自动同步暗夜色，避免界面变暗但阅读器刺眼大白的问题；
     - 控制栏与徽标文字必须通过当前背景的明度动态计算对比度（浅底用暗字，暗底用高亮浅字 `#F2F2F7`），严防“黑底黑字”不可读缺陷。

3. **隐私与 BYOK 准则**：
   - 严格遵循 Bring Your Own Key（自带密钥）原则。
   - 所有模型 API Key 仅保存在本机 Android Keystore 中，请求直连用户配置的服务商（OpenAI / Anthropic / Gemini / DeepSeek / 兼容网关），绝对不添加任何第三方私有中继或上传个人服务器。

---

## 🤖 AI 协议与交互规范

1. **多协议适配与防踩坑（Anthropic / OpenAI / Gemini）**：
   - **Anthropic 协议规范**：
     - Messages 数组严格要求 `user` 与 `assistant` 角色交替，首条消息必须为 `user`，且不能传空数组。
     - 必须使用 `AiProtocol.sanitizeAnthropicTurns` 自动合并连续同角色消息。
     - 遇到错误时，必须从 SSE 负载或响应 Body 中解析真实的错误描述（`message` 字段），禁止吞没错误抛出模糊的通用提示。
   - **模型选择与任务分流**：
     - 提供主流商用模型预设快捷点选（DeepSeek、Claude、GPT-4o、Gemini、Kimi、GLM、Qwen）。
     - 支持在线动态拉取 `/models`，并支持模糊搜索过滤。

2. **AI 技能库（AiSkills）**：
   - `NOVEL_WORKSHOP`：网文黄金技法（动作展示而非讲述、潜台词对话、章末悬念钩子、去 AI 味套话）。
   - `NOVEL_SERIAL_EPIC`：长篇爽点工坊（欲扬先抑、信息差张力、阶段性高潮爆点、力透纸背的金句）。
   - `COMMENT_SMART_REPLY`：间贴神评与高情商互动（神级读者吐槽、二次元热梗反转、意难平深度共鸣、伏笔推敲）。

---

## 📦 跨平台与云端开发环境准则

1. **保持跨平台配置清洁**：
   - `gradle.properties` 不得包含任何操作系统专用的绝对路径（如硬编码 Windows `C:/Program Files/Java` 或云端 Linux 临时目录）。
   - 保持通用参数：`org.gradle.jvmargs=-Xmx4096m -XX:+UseParallelGC`。
2. **构建与测试验证流程**：
   - 代码修改后必须执行单元测试：`./gradlew :app:testDebugUnitTest`。
   - 交付或发版前必须执行编译打包：`./gradlew :app:assembleRelease` 和 `:app:assembleDebug`。
