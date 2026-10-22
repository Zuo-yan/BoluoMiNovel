# 📱 菠萝蜜小说（BoluoMiNovel）

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg?style=flat-square" alt="Platform" />
  <img src="https://img.shields.io/badge/Content-菠萝包轻小说-pink.svg?style=flat-square" alt="菠萝包轻小说" />
  <img src="https://img.shields.io/badge/AI-BYOK-purple.svg?style=flat-square" alt="BYOK AI" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%203-blue.svg?style=flat-square" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Latest-v0.30.4-success.svg?style=flat-square" alt="v0.30.4" />
</p>

菠萝蜜小说是一款围绕 **菠萝包轻小说** 设计的 Android 阅读器，将账号书架、作品发现、原生阅读、漫画、读者书评与间贴整合到一个应用里，并提供基于用户自有模型 API Key 的 AI 伴读、续写和平行剧情分支。TXT、Markdown、EPUB 等本地书籍可作为补充阅读来源。

本项目是第三方客户端，不隶属于菠萝包官方。只读取公开内容及当前账号有权访问的内容，不提供购买、充值或绕过权限的能力。

## 主要能力

### 菠萝包阅读

- 使用账号或手机号与密码登录，本机加密保存 App 会话，不保存密码。
- 查看账号云端书架，点书直接续读；首次阅读从第一章开始。
- 搜索作品、按分类与多个标签组合筛选，支持继续分页和匿名浏览。
- 原生阅读器支持字号、行距、边距、翻页／滚动模式、主题和听书。
- 免费章节与账号已购章节按当前权限读取；未购、失效、限流和网络错误分别提示。
- 已读章节自动缓存，主动下载内容与应用缓存分开管理，可导出 TXT、Markdown 和 EPUB。
- 书籍详情读取完整历史书评，支持继续加载、跨页去重和失败重试。

### 书评、间贴与作者作品

- 有讨论的正文段落显示常驻间贴气泡与数量，无需先长按才能发现。
- 使用服务器 `row` 和 `count` 定位段落，保留正文行号，避免章节内容与吐槽错位。
- 间贴支持最热／最新排序、继续分页与错误重试。
- 吐槽下的楼中楼回复可按需展开，显示真实昵称、头像、角色、正文和回复时间。
- 主吐槽与楼中楼均读取嵌套头像字段；排序使用紧凑的“最热／最新”胶囊切换。
- 官方接口仅随首屏返回部分回复时，界面会明确提示已返回数量，不将部分数据标成全部。
- 从书籍详情进入作者作品集，浏览同一作者的其他作品。

### 漫画

- 支持菠萝包漫画分类、关键词搜索、作品目录和原生纵向图片阅读。
- 目录滚动到底会继续加载后续页，章末下滑可继续下一话。
- 漫画使用独立的公开 Web 接口，不读取或复用小说账号会话。

### AI 伴读与创作

- 支持 OpenAI Chat Completions、OpenAI Responses、Anthropic Messages 和 Google Gemini 四种独立协议。
- 提供 DeepSeek、Anthropic、OpenAI、Google、智谱 AI（Z.ai）、阿里云百炼、月之暗面、xAI、腾讯混元等厂商预设，并可按平台／地域选择入口。
- 可配置单次最大输出 Token；已知模型优先使用接口元数据或内置资料，未知时保留手动值并提示来源。
- 长按正文进行“划线即问”、情节回忆、人物整理和 AI 吐槽。
- 支持章节续写与 What-if 平行剧情分支，保留原作、AI 续写和分支章节标识。

## 安全与隐私

- BYOK：API Key 由 Android Keystore 保护的本地加密存储保存，不经过项目自建服务器。
- 菠萝包登录会话仅保存在本机；退出账号会清除会话和远端书架缓存，已下载正文与阅读进度由用户单独管理。
- 匿名免费请求、已登录请求和付费章节请求采用不同重试策略；遇到签名拒绝、限流或平台安全验证立即停止后续请求。
- 下载任务提供请求间隔与风控暂停机制，尽量降低账号风险；这不能承诺完全避免平台策略变化。
- 项目没有中心化账户、广告 SDK 或自建内容转发服务。

## 技术栈

| 层 | 实现 |
| --- | --- |
| 客户端 | Kotlin、Jetpack Compose、Material 3 |
| 架构 | MVVM、Hilt、Coroutines、Flow |
| 本地数据 | Room、DataStore、Android Keystore |
| 网络 | Ktor、OkHttp、SSE 流式响应 |
| 文件 | TXT、Markdown、EPUB，应用内离线正文 |
| 构建 | Gradle Kotlin DSL、Android SDK 36、minSdk 26 |

## 快速开始

### 环境要求

- Android Studio 或 Android SDK 36
- JDK 17
- Android 8.0（API 26）或更高版本的设备

### 构建

```powershell
git clone https://github.com/Zuo-yan/BoluoMiNovel.git
cd BoluoMiNovel
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

生成的安装包位于：

```text
app\build\outputs\apk\release\app-release.apk
```

GitHub Releases 中的 APK 与该版本源码对应。覆盖安装新版本不会改变应用包名，已有本地数据可继续使用。

## 使用提示

1. 在「设置 → 添加提供商」中配置自己的模型 API Key，先执行连接测试。
2. 在底部「菠萝包」页面登录账号，或直接打开书号／链接浏览公开作品。
3. 点书架中的作品续读；目录、下载、导出和作品信息可从详情页或阅读器进入。
4. 阅读中点击段落右侧的间贴气泡查看吐槽，点击“查看回复”展开楼中楼。
5. 长按正文可调用划线问答、听书、续写或其他 AI 操作。

## 版本与验证

当前版本为 **v0.30.4**，主要变更是将项目与应用统一更名为“菠萝蜜小说”。详细实现与接口验证记录见 [`docs/`](docs/)。

## 免责声明

本项目用于个人学习、阅读辅助和本机数据管理。使用者应遵守菠萝包用户协议、内容版权要求与当地法律。项目不绕过付费、不代理购买、不提供盗版内容，也不保证第三方接口长期稳定。第三方接口、账号策略和内容权限发生变化时，部分功能可能暂停或需要更新。

## 许可证

仓库现有代码按项目中的许可证文件发布；第三方内容、接口数据和用户账号数据不随本项目授权。
