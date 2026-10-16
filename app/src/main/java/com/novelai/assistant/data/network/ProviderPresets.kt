package com.novelai.assistant.data.network

data class ProviderPlatform(val id: String, val label: String, val baseUrl: String, val kind: ProviderKind, val workspace: Boolean = false)
data class ProviderPreset(val id: String, val name: String, val platforms: List<ProviderPlatform>)

object ProviderPresets {
    val items = listOf(
        ProviderPreset("deepseek", "DeepSeek", listOf(ProviderPlatform("standard", "官方按量 API", "https://api.deepseek.com/v1", ProviderKind.OPENAI_COMPATIBLE))),
        ProviderPreset("anthropic", "Anthropic", listOf(ProviderPlatform("global", "Claude API · 全球统一入口", "https://api.anthropic.com/v1", ProviderKind.ANTHROPIC))),
        ProviderPreset("openai", "OpenAI", listOf(ProviderPlatform("global", "OpenAI API · 全球统一入口", "https://api.openai.com/v1", ProviderKind.OPENAI_RESPONSES))),
        ProviderPreset("google", "Google", listOf(ProviderPlatform("developer", "Gemini Developer API", "https://generativelanguage.googleapis.com/v1beta", ProviderKind.GOOGLE_GEMINI))),
        ProviderPreset("zai", "智谱 AI（Z.ai）", listOf(
            ProviderPlatform("cn", "智谱开放平台 · 国内", "https://open.bigmodel.cn/api/paas/v4", ProviderKind.OPENAI_COMPATIBLE),
            ProviderPlatform("global", "Z.ai · 国际", "https://api.z.ai/api/paas/v4", ProviderKind.OPENAI_COMPATIBLE))),
        ProviderPreset("aliyun", "阿里云百炼", listOf(
            ProviderPlatform("workspace-beijing", "Workspace · 北京", "https://{workspace}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1", ProviderKind.OPENAI_COMPATIBLE, true),
            ProviderPlatform("workspace-singapore", "Workspace · 新加坡", "https://{workspace}.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1", ProviderKind.OPENAI_COMPATIBLE, true),
            ProviderPlatform("workspace-us", "Workspace · 弗吉尼亚", "https://{workspace}.us-east-1.maas.aliyuncs.com/compatible-mode/v1", ProviderKind.OPENAI_COMPATIBLE, true),
            ProviderPlatform("dashscope-beijing", "DashScope · 北京（兼容入口）", "https://dashscope.aliyuncs.com/compatible-mode/v1", ProviderKind.OPENAI_COMPATIBLE),
            ProviderPlatform("dashscope-singapore", "DashScope · 新加坡（兼容入口）", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", ProviderKind.OPENAI_COMPATIBLE))),
        ProviderPreset("moonshot", "月之暗面（Moonshot）", listOf(
            ProviderPlatform("cn", "Kimi 开放平台 · 国内", "https://api.moonshot.cn/v1", ProviderKind.OPENAI_COMPATIBLE),
            ProviderPlatform("global", "Moonshot · 国际", "https://api.moonshot.ai/v1", ProviderKind.OPENAI_COMPATIBLE))),
        ProviderPreset("xai", "xAI", listOf(ProviderPlatform("global", "xAI API · 全球统一入口", "https://api.x.ai/v1", ProviderKind.OPENAI_RESPONSES))),
        ProviderPreset("hunyuan", "腾讯混元", listOf(
            ProviderPlatform("tokenhub-cn", "TokenHub · 广州", "https://tokenhub.tencentmaas.com/v1", ProviderKind.OPENAI_COMPATIBLE),
            ProviderPlatform("tokenhub-global", "TokenHub · 新加坡", "https://tokenhub-intl.tencentmaas.com/v1", ProviderKind.OPENAI_COMPATIBLE),
            ProviderPlatform("legacy", "混元大模型 · 原平台", "https://api.hunyuan.cloud.tencent.com/v1", ProviderKind.OPENAI_COMPATIBLE))),
        ProviderPreset("custom", "自定义 / 本地服务", listOf(ProviderPlatform("custom", "自行填写服务地址", "", ProviderKind.OPENAI_COMPATIBLE)))
    )
}
