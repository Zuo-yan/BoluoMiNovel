# API 参考文档

## HnVisualEffectLayout

容器控件，继承自 `FrameLayout`，用于承载通透视觉效果。子 View 的背景应设为透明，效果渲染在子 View 之下。

### 构造函数

| 构造函数 | 说明 |
|---|---|
| `HnVisualEffectLayout(Context context)` | 代码创建 |
| `HnVisualEffectLayout(Context context, AttributeSet attrs)` | XML 创建 |
| `HnVisualEffectLayout(Context context, AttributeSet attrs, int defStyleAttr)` | XML 创建带默认样式 |

### 公开方法

| 方法 | 签名 | 说明 |
|---|---|---|
| `setVisualEffect` | `void setVisualEffect(HnVisualEffect effect)` | 设置视觉效果（如 `HnGlassEffect`、`HnGradientBlurEffect` 等）。传 `null` 可清除当前效果。 |
| `getVisualEffect` | `HnVisualEffect getVisualEffect()` | 获取当前设置的视觉效果，可能为 `null`。 |
| `setRadius` | `void setRadius(int radius)` | 设置容器圆角半径（单位：**px**）。 |
| `getRadius` | `int getRadius()` | 获取容器圆角半径。 |
| `setBound` | `void setBound(RectF bound)` | 设置效果渲染区域；传 `null` 表示与容器大小一致。 |
| `getBound` | `RectF getBound()` | 获取效果渲染区域，可能为 `null`。 |
| `setCornerRadiusWithClipChildren` | `void setCornerRadiusWithClipChildren(float radius, boolean clipChildren)` | 同时设置圆角和是否裁剪子 View。 |

---

## HnVisualEffect 继承关系总览

通透效果采用三层继承结构，`HnVisualEffect` 为抽象基类，定义核心接口：

```
HnVisualEffect (abstract)
├── HnGlassEffect
│   └── HnColorPickEffect
└── HnGradientBlurEffect
```

### 三子类对比

| 维度 | HnGlassEffect | HnGradientBlurEffect | HnColorPickEffect |
|------|--------------|---------------------|-------------------|
| **是否模糊** | 是（均匀模糊） | 是（渐变模糊） | **否** |
| **是否产生通透效果** | 是 | 是 | **否，仅颜色拾取** |
| **父类** | HnVisualEffect | HnVisualEffect | **HnGlassEffect** |
| **核心用途** | 弹窗/面板/卡片的毛玻璃背景 | 导航栏/工具栏的渐变模糊 | 文字颜色自适应背景明暗 |
| **底层效果类型** | BLUR + COLOR_PICK + OUTER_SHADOW 等 | GRADIENT_BLUR + COLOR_PICK | 仅 COLOR_PICK |
| **特有属性** | materialType / sceneType / widgetSize / isInteractive / transparencyLevel | direction / colors | 无（继承父类所有属性） |
| **业务语义** | "给我毛玻璃背景" | "给我导航栏渐变模糊" | "告诉我背景是明是暗" |

### 关键区别说明

**HnColorPickEffect**：继承自 `HnGlassEffect`，但重写了 `mapToScenes()` 方法返回 `SceneType.NoBlur`，因此在创建风格时不会添加模糊效果（`GRADIENT_BLUR` 和 `BLUR` 都会被移除），仅保留颜色拾取能力。

**HnGradientBlurEffect**：直接继承 `HnVisualEffect`，不使用 `materialType/sceneType/widgetSize` 等属性，内部固定使用 `FROSTED_GLASS_TRANSPARENCY` 风格和 `GradientBlur | General` 场景位掩码。

**HnGlassEffect**：是三个类中属性最丰富的，支持材质类型、场景类型、控件尺寸、交互开关、自定义通透度等完整配置。

---

## HnGlassEffect

毛玻璃/磨砂玻璃效果，适用于弹窗、面板、卡片等需要通透感的场景。

### 构造函数

| 构造函数 | 说明 |
|---|---|
| `HnGlassEffect()` | 使用默认参数：`LIQUID_GLASS` 材质，`IMMERSIVE` 场景 |
| `HnGlassEffect(HnMaterialType materialType, HnSceneType sceneType)` | 自定义材质和场景。`materialType` 非 `null` 时自动关闭跟随系统材质设置 |

### 公开方法

| 方法 | 签名 | 说明 |
|---|---|---|
| `setMaterialType` | `void setMaterialType(HnMaterialType materialType)` | 设置材质类型。设非 `null` 值后不再跟随系统材质设置 |
| `getMaterialType` | `HnMaterialType getMaterialType()` | 获取当前材质类型 |
| `setWidgetSize` | `void setWidgetSize(HnWidgetSize widgetSize)` | 设置控件尺寸类型（大面板/小按钮） |
| `setSceneType` | `void setSceneType(HnSceneType sceneType)` | 设置场景类型 |
| `getSceneType` | `HnSceneType getSceneType()` | 获取当前场景类型 |
| `setInteractive` | `void setInteractive(boolean interactive)` | 是否开启触控交互动效（缩放、形变、光效等） |
| `isInteractive` | `boolean isInteractive()` | 是否开启交互动效，默认 `true` |
| `setTransparencyLevel` | `void setTransparencyLevel(float transparencyLevel)` | 设置透明度等级，范围 `0.0 ~ 1.0`，`-1` 表示使用系统默认。设置后不再跟随系统透明度变化 |
| `getTransparencyLevel` | `float getTransparencyLevel()` | 获取透明度等级，返回 `-1` 表示使用系统默认 |

---

## HnGradientBlurEffect

渐变模糊效果，模糊强度沿指定方向递减，适用于导航栏、工具栏等。

### 构造函数

| 构造函数 | 说明 |
|---|---|
| `HnGradientBlurEffect()` | 使用默认方向 `TOP_TO_BOTTOM` |
| `HnGradientBlurEffect(HnBlurGradientBlurDirection direction)` | 自定义渐变方向，传 `null` 时默认为 `TOP_TO_BOTTOM` |

### 公开方法

| 方法 | 签名 | 说明 |
|---|---|---|
| `setGradientBlurDirection` | `void setGradientBlurDirection(HnBlurGradientBlurDirection direction)` | 设置渐变模糊方向 |
| `getGradientBlurDirection` | `HnBlurGradientBlurDirection getGradientBlurDirection()` | 获取当前渐变模糊方向 |
| `setColors` | `void setColors(int[] colors)` | 设置渐变颜色数组。**设置非空数组后会自动关闭 `adaptToBackground` 模式，不再随背景亮度自动切换明暗** |
| `getColors` | `int[] getColors()` | 获取渐变颜色数组，可能为 `null` |

---

## HnColorPickEffect

背景取色效果，继承自 `HnGlassEffect`。**不产生透明模糊效果**，用于感知背景亮度变化并通知回调。

### 实现原理

重写父类 `mapToScenes()` 方法返回 `SceneType.NoBlur.getMode()`，使底层风格引擎不会添加模糊效果，仅保留颜色拾取能力。

### 构造函数

| 构造函数 | 说明 |
|---|---|
| `HnColorPickEffect()` | 使用 `LIQUID_GLASS` 材质和 `IMMERSIVE` 场景 |
| `HnColorPickEffect(HnMaterialType materialType, HnSceneType sceneType)` | 自定义材质和场景 |

> 继承自 `HnGlassEffect` 的所有公开方法（`setMaterialType` / `setSceneType` / `setWidgetSize` / `setInteractive` 等）均可调用，但对 ColorPick 效果本身无实际影响。核心价值在于 `addColorChangedReceiver()` 回调机制。

---

## 枚举类型

### HnMaterialType — 材质类型

| 值 | 说明 |
|---|---|
| `LIQUID_GLASS` | 液态玻璃，流动感强，透明度高 |
| `FROSTED_GLASS` | 磨砂玻璃，哑光质感，磨砂感强 |

### HnSceneType — 场景类型

| 值 | 说明 |
|---|---|
| `IMMERSIVE` | 沉浸式场景，适用于图库、音乐、阅读等 |
| `GENERAL` | 通用场景，适用于资讯、通讯录、文档类等 |

### HnWidgetSize — 控件尺寸类型

| 值 | 说明 |
|---|---|
| `BIG` | 大尺寸，适用于弹窗、菜单、大面板 |
| `SMALL` | 小尺寸，适用于按钮、小控件 |

### HnBlurGradientBlurDirection — 渐变模糊方向

| 值 | 说明 |
|---|---|
| `TOP_TO_BOTTOM` | 模糊强度从上到下递减 |
| `BOTTOM_TO_TOP` | 模糊强度从下到上递减 |

---

## IColorChangedReceiver

颜色变化回调接口，用于接收背景亮度变化通知。

```java
public interface IColorChangedReceiver {
    void colorChanged(int type, float fraction, boolean isDarkMode, Bundle bundle);
}
```

### 回调参数说明

| 参数 | 类型 | 说明 |
|---|---|---|
| `type` | `int` | 回调类型：`0` = 容器，`1` = 控件/Widget |
| `fraction` | `float` | 背景亮度等级值（0-100） |
| `isDarkMode` | `boolean` | 是否为深色背景。`true` = 深色背景（应使用浅色文字），`false` = 浅色背景（应使用深色文字）。取值范围：0-60 为暗，60-100 为亮。**注意：此参数不等同于系统 UIMode** |
| `bundle` | `Bundle` | 扩展参数 |

---

## HnVisualEffect 公共 API

所有效果类的基类，以下方法在 `HnGlassEffect`、`HnGradientBlurEffect`、`HnColorPickEffect` 中均可使用。

| 方法 | 签名 | 说明 |
|---|---|---|
| `setFollowSystemSettings` | `void setFollowSystemSettings(boolean followSystemSettings)` | 是否跟随系统通透开关、等级、材质等设置。默认 `true` |
| `isFollowSystemSettings` | `boolean isFollowSystemSettings()` | 是否跟随系统设置，默认 `true` |
| `setAdaptToBackground` | `void setAdaptToBackground(boolean adaptToBackground)` | 是否自适应背景明暗。**设为 `false` 时会抑制 `colorChanged` 回调** |
| `isAdaptToBackground` | `boolean isAdaptToBackground()` | 是否自适应背景，默认 `true` |
| `addColorChangedReceiver` | `void addColorChangedReceiver(IColorChangedReceiver receiver)` | 注册颜色变化回调 |
| `removeColorChangedReceiver` | `void removeColorChangedReceiver(IColorChangedReceiver receiver)` | 移除指定回调 |
| `clearColorChangedReceiver` | `void clearColorChangedReceiver()` | 清除所有回调 |
| `notifyColorChanged` | `void notifyColorChanged(int type, float fraction, boolean isDarkMode, Bundle bundle)` | 主动通知所有已注册的回调 |
| `setRadius` | `void setRadius(int radius)` | 设置效果圆角半径（单位：px） |
| `setBound` | `void setBound(RectF bound)` | 设置效果渲染区域 |
| `setSkipRenderLayer` | `void setSkipRenderLayer(boolean disable)` | 控制离屏渲染。`true` = **关闭**离屏渲染（降低内存开销，但会关闭实时模糊更新并清除外阴影效果），仅建议静态背景使用；`false` = **启用**离屏渲染（默认）。可在 `applyTo()` 前后调用 |
| `isSkipRenderLayer` | `boolean isSkipRenderLayer()` | 是否关闭了离屏渲染 |
| `onChanged` | `void onChanged()` | 触发效果重绘（响应布局变化时调用） |
| `onConfigurationChanged` | `void onConfigurationChanged(Configuration newConfig)` | 响应配置变更（如横竖屏切换） |
| `onTouchEvent` | `void onTouchEvent(boolean handled, MotionEvent event)` | 响应触控事件（用于容器动画） |

---

## XML Attributes

> **重要**：以下 XML 属性**仅对 `HnTransparencyView` 控件生效**（aar 内有 `HnTransparencyView` styleable）。
>
> **`HnVisualEffectLayout` 不解析任何 XML 通透属性**——其构造函数未调用 `obtainStyledAttributes`（字节码验证）。在 `HnVisualEffectLayout` 上配置 `app:hnViewStyleType`/`app:hnWindowBLurType`/`app:hnBlurDistribution`/`app:hnWidgetSizeType`/`app:hnDisplayPattern`/`app:hnCornerRadius` 等属性**全部静默失效**。
>
> 对于 `HnVisualEffectLayout`，所有效果参数必须通过代码 `setVisualEffect(HnGlassEffect)` 配置，圆角通过 `setRadius(int)` 设置。

### `hnViewStyleType` — 视图样式类型

| 值 | 含义 |
|---|---|
| `normal` (0) | 普通样式，不启用通透效果 |
| `transparency` (1) | 通透样式，启用毛玻璃/液态玻璃等效果 |

> 仅 `HnTransparencyView` 生效。`HnVisualEffectLayout` 通过代码 `setVisualEffect()` 启用效果。

### `hnColorMode` — 颜色模式

| 值 | 含义 |
|---|---|
| `dark` (4) | 深色模式，使用暗色背景与高光参数 |
| `light` (8) | 浅色模式，使用亮色背景与高光参数 |

> 不设置时默认跟随系统 UI 模式（深色/浅色切换自动适配）。

### `hnDisplayPattern` — 显示模式/场景类型

| 值 | 含义 | 适用场景 |
|---|---|---|
| `immersive` (16) | 沉浸式 — 背景更通透（alpha 更低）、模糊更重 | 全屏浏览、图库、音乐、阅读 |
| `general` (32) | 常规式 — 背景较实、模糊较轻 | 工具栏、导航栏、资讯列表 |

> 与 `hnWidgetSizeType` 组合使用，决定最终的模糊半径和背景 alpha。详见 SKILL.md §Step 1。

### `hnPopupStyleType` — 弹窗样式类型

| 值 | 含义 |
|---|---|
| `normal` (0) | 普通弹窗样式 |
| `transparency` (1) | 通透弹窗样式 |

> 配合 `HnBottomSheet` / `HwBottomSheet` 等弹窗控件使用。

### `hnPopupDisplayPattern` — 弹窗场景类型

| 值 | 含义 |
|---|---|
| `immersive` (16) | 沉浸式弹窗 — 更通透的背景 |
| `general` (32) | 常规式弹窗 — 较实的背景 |

> 与 `hnDisplayPattern` 类似，专用于弹窗控件的场景配置。

### `hnWindowBLurType` — 窗口模糊层级（重点）

决定**模糊效果渲染在哪个渲染层级**，涉及 Android 跨图层模糊能力。设错会导致通透控件无法获取下层画面（模糊区域变黑或空白）。

| 值 | 含义 | 适用场景 |
|---|---|---|
| `none` (0) | 不启用窗口级模糊 | 不需要通透效果 |
| `view` (1) | 跨窗，窗口后为 **View** 层级 — 悬浮窗覆盖在普通应用页面上方 | **弹窗/对话框/面板**（最常见） |
| `surface` (2) | 跨窗，窗口后为 **Surface** 层级 — 悬浮窗覆盖在视频/游戏等 Surface 渲染内容上 | 覆盖在视频播放器、游戏画面上 |
| `mix` (3) | 跨窗，窗口后同时有 **View + Surface** | 同时覆盖 View 和 Surface 的复杂场景 |

> 底层通过 `WindowBlurManager` 设置 `isLayer` 标志位，使当前 View 能够跨图层正确获取并模糊下层内容。弹窗/面板场景统一使用 `view`。

### `hnBlurDistribution` — 模糊分布类型（重点）

决定**模糊强度在空间上如何分布**。

| 值 | 含义 | 效果 | 适用场景 |
|---|---|---|---|
| `gradientBlur` (64) | **渐变模糊** — 模糊强度沿指定方向递减 | 上强下弱 / 下强上弱 | 顶部导航栏、底部工具栏 |
| `uniformBlur` (128) | **均匀模糊** — 整个区域使用相同模糊强度 | 整体一致的磨砂效果 | **弹窗/面板/卡片**（最常见） |
| `noBlur` (8192) | **不模糊** — 仅用纯色半透明背景 | 无模糊，半透明纯色 | 低端机降级 / 不需要模糊时 |

> 使用 `gradientBlur` 时需配合 `hnBlurGradientDirection` 指定渐变方向。弹窗/面板场景统一使用 `uniformBlur`。

### `hnWidgetSizeType` — 控件尺寸类型

| 值 | 含义 | 典型控件 |
|---|---|---|
| `bigWidget` (256) | 大尺寸 — 模糊半径更大、阴影更重、背景较实 | 弹窗、面板、大卡片 |
| `smallWidget` (512) | 小尺寸 — 模糊半径更小、阴影较轻、背景更透 | 按钮、徽标、小控件 |

> 与 `hnDisplayPattern` 组合使用，决定最终的模糊半径、背景 alpha 和阴影参数。详见 SKILL.md §Step 1。

### `hnCornerRadius` — 容器圆角半径

| 值 | 含义 |
|---|---|
| dimension（如 `16dp`） | 圆角半径大小，单位 dp 或 px |

> 仅 `HnTransparencyView` 生效。`HnVisualEffectLayout` 不解析此属性，圆角通过代码 `setRadius(int)` 设置（单位 px）。容器若背景为 `GradientDrawable`，会自动提取圆角值。

### `hnContainerEffectEnabled` — 容器动效开关

| 值 | 含义 |
|---|---|
| `true` | 开启容器缩放/形变/光效等交互动效 |
| `false` | 关闭动效，静态渲染 |

### `hnContainerEffectLinked` — 容器与子 View 动效联动

| 值 | 含义 |
|---|---|
| `true` | 容器动效会联动子 View（一起缩放/形变） |
| `false` | 容器动效不影响子 View |

### `hnScaleFactor` — 动画缩放因子

| 值 | 含义 |
|---|---|
| `0`（默认） | 使用系统默认缩放因子 |
| `>0` 的 float | 自定义缩放比例（如 `0.95` 表示缩小 5%） |

### `hnTransScaleType` — 动画缩放类型

| 值 | 含义 |
|---|---|
| `micro` (0) | 微动效缩放，幅度极小 |
| `card` (1) | 卡片级缩放，幅度中等 |
| `button` (2) | 按钮级缩放，幅度较大 |

### `hnAllowReceiveColor` — 是否接收颜色回调

| 值 | 含义 |
|---|---|
| `true` | 接收 `IColorChangedReceiver` 颜色变化通知 |
| `false` | 不接收颜色回调 |

> 如果容器不需要根据背景亮度自动调整文字颜色，可设为 `false`。

### `hnTransparencyScenes` — 通透场景配置（flags）

支持多个标志位**位或组合**，用于精细控制通透效果的行为。

| 标志 | 值 | 说明 |
|---|---|---|
| `dark` | 4 | 深色模式 |
| `light` | 8 | 浅色模式 |
| `immersive` | 16 | 沉浸式 |
| `general` | 32 | 通用场景 |
| `gradientBlur` | 64 | 渐变模糊 |
| `uniformBlur` | 128 | 均匀模糊 |
| `noBlur` | 8192 | 不模糊 |
| `bigWidget` | 256 | 大控件 |
| `smallWidget` | 512 | 小控件 |
| `followColorPick` | 1024 | 跟随取色（默认启用） |
| `followUiMode` | 2048 | 跟随系统 UI 模式切换 |
| `forceColorMode` | 4096 | 强制颜色模式，不随 UI 模式和取色变化 |

> `hnTransparencyScenes` 是 `flags` 格式，支持 `|` 组合，如 `immersive|bigWidget|uniformBlur` 表示「沉浸式 + 大控件 + 均匀模糊」。大多数场景使用独立属性（`hnDisplayPattern`、`hnWidgetSizeType` 等）即可，无需直接操作此 flags。
