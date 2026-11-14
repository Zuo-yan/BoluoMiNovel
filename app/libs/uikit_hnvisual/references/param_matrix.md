# 通透材质参数详解

本文档列出高端机型（HIGH_LEVEL_TRANSPARENT_MATERIAL）在**通透开启**状态下的完整参数矩阵。

## 1. 磨砂玻璃（FROSTED_GLASS）

### 1.1 大尺寸 + 常规式（FrostedBigGeneral）

**亮色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 24dp |
| saturation | 1.2 |
| lightBgColors | #F2F2F2 → #FCFCFC |
| lightBgAlphas | [191, 102] |
| shadowRadius | 60dp |
| shadowDy | 8dp |
| highlightThickness | 6 |
| highlightAngle | 235° |

**暗色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 24dp |
| saturation | 1.0 |
| darkBgColors | #000000 → #737373 |
| darkBgAlphas | [191, 77] |
| shadowRadius | 60dp |
| shadowDy | 8dp |
| highlightThickness | 6 |
| highlightAngle | 235° |

### 1.2 大尺寸 + 沉浸式（FrostedBigImmersive）

**亮色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 60dp |
| saturation | 1.2 |
| lightBgColors | #F7F7F7 → #FFFFFF |
| lightBgAlphas | [115, 26] |
| shadowRadius | 60dp |
| shadowDy | 8dp |
| shadowAlpha | 20 |
| highlightThickness | 6 |
| highlightAlpha | 230 |

**暗色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 60dp |
| saturation | 1.0 |
| darkBgColors | #000000 → #737373 |
| darkBgAlphas | [38, 102] |
| shadowRadius | 60dp |
| shadowDy | 8dp |
| shadowAlpha | 20 |
| highlightThickness | 6 |
| highlightAlpha | 102 |

### 1.3 小尺寸 + 常规式（FrostedSmallGeneral）

**亮色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 12dp |
| saturation | 1.5 |
| lightBgColors | #FFFFFF → #FCFCFC |
| lightBgAlphas | [153, 102] |
| shadowRadius | 30dp |
| shadowDy | 4dp |
| highlightThickness | 6 |
| highlightAngle | 235° |

**暗色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 12dp |
| saturation | 1.1 |
| darkBgColors | #000000 → #4D4D4D |
| darkBgAlphas | [153, 102] |
| shadowRadius | 30dp |
| shadowDy | 4dp |
| highlightThickness | 6 |
| highlightAngle | 235° |

### 1.4 小尺寸 + 沉浸式（FrostedSmallImmersive）

**亮色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 8dp |
| saturation | 1.5 |
| lightBgColors | #F7F7F7 → #FFFFFF |
| lightBgAlphas | [102, 26] |
| shadowRadius | 30dp |
| shadowDy | 4dp |
| shadowAlpha | 20 |
| highlightThickness | 6 |
| highlightAlpha | 230 |

**暗色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 8dp |
| saturation | 1.1 |
| darkBgColors | #000000 → #787878 |
| darkBgAlphas | [38, 77] |
| shadowRadius | 30dp |
| shadowDy | 4dp |
| shadowAlpha | 20 |
| highlightThickness | 6 |
| highlightAlpha | 102 |

## 2. 液态玻璃（LIQUID_GLASS）

### 2.1 大尺寸 + 常规式（LiquidBigGeneral）

**亮色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 8dp |
| saturation | 1.5 |
| glassRefraction | 0.3 |
| glassThickness | 0.62 |
| glassDepth | 0.54 |
| glassDispersion | 0.2 |
| glassMaskColor | #40000000 |
| shadowRadius | 60dp |
| shadowDy | 8dp |
| highlightThickness | 5 |

**暗色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 8dp |
| saturation | 1.0 |
| glassRefraction | 0.3 |
| glassThickness | 0.62 |
| glassDepth | 0.54 |
| glassDispersion | 0.2 |
| glassMaskColor | #80000000 |
| shadowRadius | 60dp |
| shadowDy | 8dp |
| highlightThickness | 5 |

### 2.2 大尺寸 + 沉浸式（LiquidBigImmersive）

**亮色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 32dp |
| saturation | 1.2 |
| glassRefraction | 0.3 |
| glassThickness | 0.62 |
| glassDepth | 0.54 |
| glassDispersion | 0.2 |
| lightBgColors | #F7F7F7 → #FFFFFF |
| lightBgAlphas | [115, 26] |
| shadowDy | 10dp |
| highlightThickness | 5 |

**暗色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 32dp |
| saturation | 1.1 |
| glassRefraction | 0.3 |
| glassThickness | 0.62 |
| glassDepth | 0.54 |
| glassDispersion | 0.2 |
| darkBgColors | #000000 → #737373 |
| darkBgAlphas | [38, 102] |
| shadowDy | 10dp |
| highlightThickness | 5 |

### 2.3 小尺寸 + 常规式（LiquidSmallGeneral）

**亮色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 2dp |
| saturation | 1.5 |
| glassRefraction | 0.3 |
| glassThickness | 0.54 |
| glassDepth | 0.52 |
| glassDispersion | 0.1 |
| glassMaskColor | #40000000 |
| shadowRadius | 30dp |
| shadowDy | 4dp |
| highlightThickness | 5 |

**暗色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 2dp |
| saturation | 1.1 |
| glassRefraction | 0.3 |
| glassThickness | 0.54 |
| glassDepth | 0.52 |
| glassDispersion | 0.1 |
| glassMaskColor | #80000000 |
| shadowRadius | 30dp |
| shadowDy | 4dp |
| highlightThickness | 5 |

### 2.4 小尺寸 + 沉浸式（LiquidSmallImmersive）

**亮色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 1.5dp |
| saturation | 1.5 |
| glassRefraction | 0.3 |
| glassThickness | 0.54 |
| glassDepth | 0.52 |
| glassDispersion | 0.1 |
| lightBgColors | #F7F7F7 → #FFFFFF |
| lightBgAlphas | [102, 26] |
| shadowDy | 4dp |
| highlightThickness | 5 |

**暗色模式：**
| 参数 | 值 |
|------|---|
| blurRadius | 1.5dp |
| saturation | 1.1 |
| glassRefraction | 0.3 |
| glassThickness | 0.54 |
| glassDepth | 0.52 |
| glassDispersion | 0.1 |
| darkBgColors | #000000 → #787878 |
| darkBgAlphas | [38, 77] |
| shadowDy | 4dp |
| highlightThickness | 5 |

## 3. 动态透明度算法公式

以下公式中的 `t` 为系统通透等级，取值 **0-100**（无需归一化，直接使用原始值代入计算）。

### 3.1 磨砂玻璃

| 组合 | baseAlpha 公式 | radius 公式 |
|------|---------------|------------|
| 大+常规 | -0.08t² - 0.11t + 0.85 | 6.67t² - 30.67t + 40 |
| 大+沉浸 | 亮: 0.04t² - 0.19t + 0.55 / 暗: 0.15 | 12.5t² - 57.5t + 90 |
| 小+常规 | -0.5t + 0.9 | 5t² - 23t + 24 |
| 小+沉浸 | 亮: 0.02t² - 0.07t + 0.45 / 暗: 0.09t² - 0.22t + 0.25 | 4.17t² - 9.17t + 8 |

### 3.2 液态玻璃

| 组合 | baseAlpha 公式 | radius 公式 |
|------|---------------|------------|
| 大+常规 | -0.08t² - 0.11t + 0.85 | 亮: 0.83t² - 3.83t + 8 / 暗: 3.33t² - 15.33t + 24 |
| 大+沉浸 | 亮: -0.08t² - 0.12t + 0.85 / 暗: 0.15 | 亮: 0.83t² - 3.83t + 8 / 暗: 30t² - 68t + 70 |
| 小+常规 | -0.5t + 0.9 | 2.1t² - 4.28t + 4 |
| 小+沉浸 | 亮: -0.02t² - 0.07t + 0.45 / 暗: 0.09t² - 0.22t + 0.25 | 2.1t² - 4.28t + 4 |

## 4. 中端/低端降级方案

### 4.1 中端机（MIDDLE_LEVEL）

渐变模糊降级为**纯色渐变填充**，保留外阴影和高光效果。

### 4.2 低端机（LOW_LEVEL / Compat）

无模糊效果，仅使用：

| 组合 | 亮色填充 | 暗色填充 | 描边宽度 | 描边颜色 |
|------|---------|---------|---------|---------|
| 小+常规 | 0xF2FFFFFF | 0xF2262626 | 1.5dp | 0x4DC1C1CC |
| 小+沉浸 | 0xE6FFFFFF | 0xE6262626 | 1.5dp | - |
| 大+常规 | 0xFFF7F7F7 | 0xFF262626 | - | - |
| 大+沉浸 | 0xFFF7F7F7 | 0xFF262626 | - | - |
