---
name: uikit-hnvisual（三方应用接入通透效果）
version: 2.1.0
description: 荣耀 MagicOS 通透视觉效果快速集成指南。当开发者需要实现磨砂玻璃/液态玻璃/渐变模糊/磨砂效果、背景自适应取色、通透容器时触发。涵盖 API 用法、参数选型、性能优化与兼容性处理。
---
# 荣耀 MagicOS 通透效果快速集成

## 何时使用

当需要为弹窗、面板、卡片等容器添加**毛玻璃模糊**、**渐变磨砂**、**背景取色适配**等通透视觉效果时，使用本 Skill。 用户有以下关键词时使用该 skill ："接入通透效果"，“接入玻璃效果”，“接入液态玻璃”，“接入磨砂玻璃”


## 何时不使用

- 纯纯色背景（无需模糊/玻璃效果）
- 纯 Jetpack Compose 项目
- 低端机型且不需要兼容通透效果

---

## 接入流程（交互式）

当用户提出「帮我把 xxx 接入通透能力」「给 xxx 加毛玻璃效果」等需求时，按以下流程交互：

### 阶段零：确认 aar 依赖（前置检查）

正式接入前，先确认用户工程已正确依赖通透能力 aar（如 `hnvisualeffect-fat-x.x.x.x.aar`）。否则后续代码无法编译（`HnVisualEffectLayout`/`HnGlassEffect` 等类找不到）。

**检查方式**：扫描工程 `app/libs/` 目录或 `build.gradle` 是否有通透相关 aar 依赖。

- **已依赖** → 进入阶段一
- **未依赖** → 提示用户：
  > 当前工程未检测到通透能力 aar 依赖，需先将 `hnvisualeffect-fat-x.x.x.x.aar` 放入 `app/libs/`，并在 `build.gradle` 添加依赖（如 `implementation files('libs/hnvisualeffect-fat-11.0.0.125.aar')`）。依赖完成后再继续接入。

### 阶段一：定位目标控件

**1. 询问用户提供信息**：
> 请提供要生效通透效果的具体页面和控件 ID（如 XML 中的 `@+id/xxx` 或 Java/Kotlin 中的变量名）。如果知道页面所在的 Activity/Fragment/Dialog 类名也请一并提供。

**2. 用户无法提供时 — 自动推断**：
- 扫描用户工程，搜索包含容器/面板/弹窗/卡片特征的 XML 布局和 Java/Kotlin 代码
- 结合用户之前的描述（如"设置页的那个列表卡片"、"弹窗的底部面板"）推测对应的控件
- 将推测结果列给用户确认：

> 根据描述，我推测您需要为以下控件接入通透效果：
> - 页面：`XxxActivity` → 布局 `activity_xxx.xml`
> - 目标控件：`<LinearLayout android:id="@+id/xxx_panel" ...>`（第 N 行）
>
> 是否正确？如有偏差请指出。

**3. 用户确认后进入阶段二。**

### 阶段二：细化接入规格

明确控件后，依次与用户确认以下四项：

**布局形态建议（接入前评估）**

若目标控件当前是**通栏贴边直角布局**（如贴底全宽、贴顶全宽、四角直角、无外边距），建议改为**悬浮带圆角布局**——四周留外边距浮起、四角设非零圆角。原因：

- 通栏贴边时四角直角、控件与屏幕边缘无缝衔接，通透材质的圆角和外阴影无施展空间，玻璃质感被削弱
- 悬浮布局让四角圆角完整呈现、外阴影可见、控件与底层内容之间形成"浮起"层次，毛玻璃与折射感更突出，通透效果最佳

> 推荐改法：容器加 `layout_margin`（如 8dp）浮离边缘 + `setRadius()` 设非零圆角（如 27dp）。若用户接受，在生成代码时一并调整布局；若需保持原贴边直角布局，通透感会受限，应向用户说明。

**Q1. 是否跟随系统设置？**

> 通透效果是否跟随系统的通透开关、通透等级、材质设置？
> - **跟随系统**（推荐）：效果会随系统设置动态调整模糊强度和透明度
> - **固定参数**：使用固定参数，不受系统设置影响

- 用户选"跟随系统" → `setFollowSystemSettings(true)`（默认行为，无需额外配置）
- 用户选"固定参数" → `setFollowSystemSettings(false)` + 手动设置 `setTransparencyLevel()`

**Q2. 材质参数推荐**

材质类型（磨砂/液态）默认跟随系统通透设置，**不建议显式 `setMaterialType()`**（会触发 `mIsStyleTypeFollowSystemSettings=false`，导致材质不再跟随系统）。仅确认控件尺寸和场景类型：

| 控件类型 | 推荐尺寸 + 场景 | 说明 |
|---------|---------|------|
| 弹窗/面板/大卡片 | `BIG` + `GENERAL` | 磨砂感强，背景较实，文字可读性好 |
| 全屏面板（图库/阅读） | `BIG` + `IMMERSIVE` | 最通透，内容主体突出 |
| 导航栏/工具栏 | `SMALL` + `GENERAL` | 轻量模糊，不遮挡内容 |
| 悬浮按钮/徽标 | `SMALL` + `IMMERSIVE` | 极轻模糊，几乎全透明 |
| 按钮/开关/小型控件 | `SMALL` + `GENERAL` | 液态玻璃流动感 |

> 推荐参数：尺寸=`BIG`，场景=`GENERAL`，材质跟随系统
> 效果（高端机磨砂玻璃·亮色）：模糊半径 24dp，背景 alpha [191, 102]，外阴影 r=60/dy=8
>
> 是否使用该推荐？或指定其他尺寸/场景组合。

**Q3. 是否开启容器动效？**

> 是否需要开启容器交互动效（点击微缩放、拖拽形变、光效跟随）？
> - **开启**（推荐用于弹窗/面板）：`setInteractive(true)`
> - **关闭**（静态卡片/工具栏）：`setInteractive(false)`

**Q4. 底板颜色是否跟随背景内容深浅切换？**

> 底板颜色是否跟随底层内容明暗自动切换（深色内容上底板偏暗、浅色内容上底板偏亮）？
> - **不跟随**（建议）：底板颜色固定，使用场景预设的明暗参数，视觉更稳定可预期
> - **跟随**：底板颜色随背景内容明暗自动切换；并可注册 `IColorChangedReceiver` 回调联动文字颜色

- 用户选"不跟随" → `effect.setAdaptToBackground(false)`
- 用户选"跟随" → 默认行为（`setAdaptToBackground(true)`，无需额外配置）；如需文字颜色联动，注册 `addColorChangedReceiver(receiver)`
- **关联陷阱**：`setAdaptToBackground(false)` 会抑制 `colorChanged` 回调。若后续需要文字颜色联动，必须保持"跟随"

### 阶段三：生成代码

根据用户确认的参数，生成 Java/Kotlin 代码配置通透效果，并跑自检清单。

> **重要**：通透效果参数必须通过代码 `setVisualEffect(HnGlassEffect)` 配置。`HnVisualEffectLayout` 的构造函数**不解析任何 `app:hnXxx` XML 属性**（无 styleable，字节码验证），XML 中配置 `hnViewStyleType`/`hnWindowBLurType`/`hnBlurDistribution`/`hnWidgetSizeType`/`hnDisplayPattern` 等属性全部静默失效。XML 仅用于声明容器本身和布局属性。

---

## Step 0: 确认设备通透等级

通透效果由系统自动判定设备档位（`TransparencyFeatureConfig`）后分级渲染：

| 档位 | 常量 | 能力 |
|------|------|------|
| 高端 | `HIGH_LEVEL_TRANSPARENT_MATERIAL` | 完整磨砂玻璃 + 液态玻璃效果 |
| 中端 | `MIDDLE_LEVEL_TRANSPARENT_MATERIAL` | 渐变模糊降级为纯色渐变 |
| 低端 | `LOW_LEVEL_TRANSPARENT_MATERIAL` | 纯色填充 + 描边 + 外阴影，无模糊 |

## Step 1: 选择场景组合（选型决策）

通透效果由 **三个维度** 交叉决定最终视觉效果：

```
材质类型 (HnMaterialType)     ×   控件尺寸 (HnWidgetSize)   ×   场景类型 (HnSceneType)
FROSTED_GLASS (磨砂玻璃)           BIG (大尺寸)                GENERAL (常规式)
LIQUID_GLASS (液态玻璃)            SMALL (小尺寸)              IMMERSIVE (沉浸式)
```

选型决策表：

| 业务场景 | 推荐组合 | 原因 |
|---------|---------|------|
| 全屏浏览类页面（照片详情、阅读） | BIG + IMMERSIVE | 控件半透明隐身，内容主体突出 |
| 导航栏/工具栏 | SMALL + GENERAL | 轻量模糊 + 适度透明，不遮挡内容又保持可读 |
| 弹窗/对话框 | BIG + GENERAL | 较重模糊提供内容区隔，背景 alpha 较高确保文字可读 |
| 悬浮按钮/徽标 | SMALL + IMMERSIVE | 极轻模糊，几乎全透明，融入背景 |
| 低端机型兼容 | Compat 模式 | 无模糊效果，仅用纯色填充 + 描边 |

### 大尺寸 vs 小尺寸 核心差异

| 参数维度 | 大尺寸（BIG） | 小尺寸（SMALL） |
|---------|-------------|----------------|
| 模糊半径 | 更大（24-60dp） | 更小（1.5-12dp） |
| 背景 Alpha | 较高（更实） | 较低（更透） |
| 外阴影 | radius=60, dy=8 | radius=30, dy=4 |
| 饱和度 | 1.0-1.2 | 1.1-1.5（防色彩变淡） |

### 沉浸式 vs 常规式 核心差异

| 参数维度 | 常规式（GENERAL） | 沉浸式（IMMERSIVE） |
|---------|------------------|-------------------|
| 背景 Alpha（亮色） | 较高（如 191/102） | **更低**（如 115/26） |
| 背景 Alpha（暗色） | 较高（如 191/77） | **更低**（如 38/102） |
| 高光 Alpha | 248 | 230（更柔和） |
| 模糊半径 | 较小 | 更大（磨砂大沉浸达 60dp） |

**核心规律**：沉浸式场景背景更通透（alpha 更低），模糊更重，让底层内容穿透；小尺寸控件模糊更轻，防止过小区域模糊后视觉浑浊。

### 四种典型组合参数（磨砂玻璃·高端机·通透开启·亮色模式）

| 组合 | 模糊半径 | 背景色 + alpha | 阴影 |
|------|---------|---------------|------|
| 大+常规 | 24dp | #F2F2F2/#FCFCFC [191, 102] | r=60, dy=8 |
| 大+沉浸 | 60dp | #F7F7F7/#FFFFFF [115, 26] | r=60, dy=8 |
| 小+常规 | 12dp | #FFFFFF/#FCFCFC [153, 102] | r=30, dy=4 |
| 小+沉浸 | 8dp | #F7F7F7/#FFFFFF [102, 26] | r=30, dy=4 |

### 液态玻璃 vs 磨砂玻璃

| 参数维度 | 磨砂玻璃（FROSTED_GLASS） | 液态玻璃（LIQUID_GLASS） |
|---------|-------------------------|------------------------|
| 模糊半径（大+常规） | 24dp | 8dp |
| 模糊半径（大+沉浸） | 60dp | 32dp |
| 折射率 | 无 | 0.3 |
| 厚度 | 无 | 0.62 |
| 适用场景 | 面板/卡片/弹窗 | 按钮/开关/小型控件 |

---

## Quick Start（最小集成步骤）

```java
// 1. 创建容器
HnVisualEffectLayout container = new HnVisualEffectLayout(context);

// 2. 创建效果
HnGlassEffect glassEffect = new HnGlassEffect();
glassEffect.setWidgetSize(HnWidgetSize.SMALL);     // 控件尺寸
glassEffect.setSceneType(HnSceneType.GENERAL);     // 场景类型
glassEffect.setRadius(30);                          // 圆角半径（px）
glassEffect.setInteractive(false);                  // 容器动效开关

// 3. 应用效果
container.setVisualEffect(glassEffect);
```

> 所有尺寸单位为 **px**，子 View 背景应设为透明（效果渲染在子 View 之下）。

> **XML 声明容器**：`HnVisualEffectLayout` 可在 XML 中声明布局，但 `app:hnXxx` 通透属性**不会被解析**，所有效果参数必须通过代码 `setVisualEffect()` 配置。

## 效果选择决策树

通透效果继承关系：
```
HnVisualEffect (abstract)
├── HnGlassEffect
│   └── HnColorPickEffect（仅取色，不模糊）
└── HnGradientBlurEffect（渐变模糊）
```

| 需求 | 使用 |
|---|---|
| 弹窗/面板需要毛玻璃磨砂背景 | `HnGlassEffect` |
| 导航栏/工具栏需要从上到下渐变模糊 | `HnGradientBlurEffect` |
| 需要根据页面背景亮度自动调整文字颜色 | `HnColorPickEffect` + `IColorChangedReceiver` |
| 父容器统一配色 + 子容器独立毛玻璃 | 父: `HnColorPickEffect` + 子: `HnGlassEffect(adaptToBackground=false)` |

## 三种效果快速上手

### 1. HnGlassEffect — 毛玻璃磨砂效果

最常用的通透效果，适用于弹窗、抽屉面板、卡片等。

```java
HnVisualEffectLayout panel = new HnVisualEffectLayout(context);

// 无参构造：材质类型不显式指定，跟随系统通透开关/等级/材质设置
HnGlassEffect effect = new HnGlassEffect();
effect.setWidgetSize(HnWidgetSize.BIG);          // 控件尺寸
effect.setSceneType(HnSceneType.IMMERSIVE);     // 沉浸式场景
effect.setTransparencyLevel(0.5f);               // 0.0 ~ 1.0
effect.setInteractive(true);                      // 开启触控交互动效
effect.setRadius(30);                             // 圆角半径（px）

// 注意：显式 setMaterialType() 会关闭跟随系统材质，仅在需要固定材质时使用
// effect.setMaterialType(HnMaterialType.LIQUID_GLASS);

panel.setVisualEffect(effect);
```

### 2. HnGradientBlurEffect — 渐变模糊效果

适用于顶部导航栏、底部工具栏等需要单向渐变模糊的场景。

```java
HnVisualEffectLayout navBar = new HnVisualEffectLayout(context);

HnGradientBlurEffect effect = new HnGradientBlurEffect(
    HnBlurGradientBlurDirection.TOP_TO_BOTTOM
);
// 可选：自定义渐变颜色数组（设置后不再随背景亮度自动切换明暗）
// effect.setColors(new int[]{0x80000000, 0x00000000});

navBar.setVisualEffect(effect);
```

### 3. HnColorPickEffect — 背景取色适配

不产生透明模糊效果，而是感知背景亮度变化，适用于文字颜色自适应。

```java
HnVisualEffectLayout container = new HnVisualEffectLayout(context);

HnColorPickEffect effect = new HnColorPickEffect();
effect.addColorChangedReceiver(new IColorChangedReceiver() {
    @Override
    public void colorChanged(int type, float fraction, boolean isDarkMode, Bundle bundle) {
        // isDarkMode: true=深色背景（用浅色文字），false=浅色背景（用深色文字）
        // fraction: 背景亮度等级 (0-100)
        int textColor = isDarkMode ? Color.WHITE : Color.BLACK;
        textView.setTextColor(textColor);
    }
});

container.setVisualEffect(effect);
```

## 关键参数配置

### 圆角半径 `setRadius(int)`

```java
glassEffect.setRadius(30);  // 单位 px
```

设置容器圆角半径（单位 **px**）。若容器背景是 `GradientDrawable`，会自动提取圆角值，无需手动调用。

> 注：`setRadius` 与材质是否渲染之间的因果关系尚未定位确认，使用时以实际效果为准，不据此作"必需/非零才渲染"的断言。

### 模糊分布

通过代码选择效果类型决定：
- `HnGlassEffect` → 均匀模糊（uniformBlur），适用于弹窗/面板/卡片
- `HnGradientBlurEffect` → 渐变模糊（gradientBlur），适用于导航栏/工具栏

## 颜色回调一句话指南

通过 `effect.addColorChangedReceiver(receiver)` 注册回调，当背景亮度变化时自动通知。`isDarkMode` 表示背景明暗（非系统 UI 模式），用于决定文字颜色。注意：`setAdaptToBackground(false)` 会抑制回调。

---

## 自检清单

| 检查项 | 通过标准 |
|--------|---------|
| 效果应用方式 | 通过代码 `setVisualEffect()` 配置，不依赖 XML `app:hnXxx` 属性 |
| 圆角设置 | `setRadius()` 按视觉圆角设置（px），或容器背景为 `GradientDrawable` 自动提取 |
| 设备等级判定 | 高端机启用完整效果，中端机降级渐变，低端机仅纯色 |
| 尺寸匹配 | 大控件用 BIG，小控件用 SMALL |
| 场景匹配 | 全屏沉浸式页面用 IMMERSIVE，工具栏/导航用 GENERAL |
| 暗色模式 | 确保暗色模式下参数正确（alpha 和颜色不同） |
| 低端兼容 | 低端机至少提供纯色 + 描边兜底 |
| 子 View 背景 | 子 View 背景设为透明，不遮挡效果 |
| 背景自适应 | `setAdaptToBackground` 按需设置；关闭后会抑制 `colorChanged` 回调，需文字联动时勿关闭 |
| 离屏渲染 | 静态背景用 `setSkipRenderLayer(true)`，动态保持默认 |

---

## 阶段四：编译安装与设备验证

自检通过后，自动检测已连接的 Android 设备。若有则编译并安装最新 APK，便于即时验证通透效果；若无设备则跳过并提示。

**1. 检测已连接设备**

执行 `adb devices`，判断是否存在状态为 `device` 的设备：

- **无设备** → 提示用户并结束本阶段：
  > 未检测到已连接的 Android 设备，跳过自动安装验证。如需验证，请连接设备并开启 USB 调试后重新执行，或手动运行 `./gradlew installDebug`。
- **有设备** → 记下设备序列号；多台时取首台或请用户确认目标设备，进入下一步。

**2. 编译并安装**

在工程根目录执行 Gradle 安装任务（Windows 用 `gradlew.bat`，类 Unix 用 `./gradlew`）：

```bash
./gradlew installDebug
```

该任务会编译 Debug 变体并安装到已连接设备。

- 若项目配置了 `productFlavors`，任务名改为 `install<Flavor>Debug`
- 多设备连接且需指定单台时，改用两步：`./gradlew assembleDebug` 编译，再 `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`
- 安装失败若提示版本号冲突（versionCode/signing），用 `adb install -r <apk路径>` 覆盖安装

**3. 启动并引导验证**

安装成功后，提示用户打开目标页面检查效果：

> APK 已安装到设备。请打开 `<目标 Activity/页面>`，检查 `<目标控件>` 的通透效果：模糊强度、圆角、外阴影、动效、暗色模式表现是否符合预期。

效果异常时回到阶段二/三调整参数后重新执行本阶段。

**注意事项**

- 编译失败优先排查：阶段零 aar 依赖是否就绪、生成代码是否落到正确文件、`setVisualEffect()` 调用是否在控件 attach 到窗口之前
- 验证不同设备档位（高/中/低端）时通透效果会自动分级降级，属预期行为，非 bug
- 通透效果受系统通透开关/等级影响，验证前确认系统设置中通透已开启

---

## References

- 完整 API 参考：[api-reference.md](references/api-reference.md)
- 最佳实践与陷阱：[best-practices.md](references/best-practices.md)
- 完整参数矩阵：[param_matrix.md](references/param_matrix.md)