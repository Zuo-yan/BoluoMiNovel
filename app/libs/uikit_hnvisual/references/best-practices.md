# 最佳实践与常见陷阱

## 最佳实践

### 子 View 背景应为透明

通透效果渲染在子 View **之下**，因此子 View 的背景必须设为透明，否则效果会被遮挡。

```java
// 正确：子 View 不设背景或设为透明
textView.setBackgroundColor(Color.TRANSPARENT);
```

### 圆角自动提取

容器会自动从自身 `GradientDrawable` 类型的背景中提取圆角值。也可以手动设置：

```java
container.setRadius(32); // 单位：px
```

### 动态切换效果

切换效果时，先清除旧效果，再设置新效果：

```java
container.setVisualEffect(null);       // 先清除
container.setVisualEffect(newEffect);  // 再设置新的
```

### 父容器配色 + 子容器毛玻璃组合

实现统一配色的父容器与独立毛玻璃的子容器：

```java
// 父容器：ColorPickEffect 用于统一感知背景
HnVisualEffectLayout parent = new HnVisualEffectLayout(context);
HnColorPickEffect parentEffect = new HnColorPickEffect();
parent.setVisualEffect(parentEffect);

// 子容器：GlassEffect，关闭自适应背景以避免与父容器冲突
HnVisualEffectLayout child = new HnVisualEffectLayout(context);
HnGlassEffect childEffect = new HnGlassEffect();
childEffect.setAdaptToBackground(false);
child.setVisualEffect(childEffect);

parent.addView(child);
```

---

## 常见陷阱

### setColors() 会关闭自适应背景

`HnGradientBlurEffect.setColors(int[] colors)` 设置非空颜色数组后，会自动关闭 `adaptToBackground` 模式，效果不再随背景亮度自动切换明暗。

如需既自定义颜色又保持明暗切换，需在 `setColors()` 之后手动管理 `adaptToBackground`。

### setAdaptToBackground(false) 会抑制回调

如果关闭了自适应背景，`IColorChangedReceiver` 的 `colorChanged()` 回调将不会被触发。如果需要回调，请保持 `adaptToBackground` 为 `true`。

### setTransparencyLevel 传 0.0-1.0

参数范围是 `0.0` 到 `1.0`，内部会自动乘以 100 转换为实际等级值。不要传 0-100 的整数。

```java
effect.setTransparencyLevel(0.5f);  // 正确：50% 等级
// effect.setTransparencyLevel(50); // 错误：会超出范围
```

### setSkipRenderLayer(true) 会清除外阴影

关闭离屏渲染后，效果的实时模糊更新会被禁用，**外阴影效果也会同时被清除**。仅建议在静态背景（无滚动、无动画）时使用。

### 交互配置应在样式应用之前

如果需要在设置效果后开启交互动效（缩放、形变、光效等），应在容器布局或样式确定后再调用 `setInteractive(true)`，以确保交互参数正确生效。

---

## 性能优化建议

### 静态背景使用 setSkipRenderLayer(true)

对于背景固定的场景（如静态弹窗、固定面板），关闭离屏渲染可以显著降低内存和功耗：

```java
effect.setSkipRenderLayer(true);
```

### 滚动列表中使用透明效果的注意事项

在 `RecyclerView` 或 `ScrollView` 等滚动列表中使用通透效果时：
- 每个列表项都渲染模糊效果会导致较高功耗，建议仅在关键容器（如顶部吸顶栏、底部导航栏）中使用
- 如需在列表中大量使用，考虑用 `setSkipRenderLayer(true)` 降低开销
- 频繁滑动时模糊效果可能出现渲染延迟，需做好性能测试

---

## 兼容性说明

### 系统要求

通透效果需要荣耀 MagicOS 特定版本支持。在不支持通透效果的设备上，效果可能无法渲染或退化为普通透明背景。

### 降级方案

在不支持通透效果的设备上，可以用纯色半透明背景作为降级方案：

```java
// 检测是否支持通透效果（根据实际 API 调整）
if (isTransparencySupported()) {
    container.setVisualEffect(new HnGlassEffect());
} else {
    // 降级：使用半透明背景
    container.setBackgroundColor(0xCC000000);
}
```
