# ByteKMP DVSync 视觉卡顿修复验证报告

## 结论摘要

基于修改后的实测现象：

- 视觉上的抛滑卡顿感已经消失。
- trace 看 PresentFence/上屏侧仍然没有丢帧。

这说明之前 ByteKMP 的问题不是 DVSync 没有生效，也不是 RS/PresentFence 侧没有按节奏上屏，而是 **Compose app 侧用于驱动滚动动画的 frame time 使用了 draw 执行时刻 `currentNanoTime()`，导致真实滚动 offset 的时间推进和显示帧节奏不一致**。

本次真正解决问题的核心改动是：

```text
RenderingUIView.onFrame(frameTimeNanos)
  -> 保存系统帧时间
RenderingUIView.onDraw()
  -> mediator.onRender(..., frameTimeNanos)
BaseComposeScene.render(...)
  -> frameClock.sendFrame(frameTimeNanos)
Compose fling animation
  -> 使用稳定的 frameTimeNanos 计算 delta
```

而不是：

```text
RenderingUIView.onDraw()
  -> mediator.onRender(..., currentNanoTime())
BaseComposeScene.render(...)
  -> frameClock.sendFrame(currentNanoTime())
Compose fling animation
  -> 用 draw 实际执行时间计算 delta
```

修改后视觉卡顿消失，是因为 Compose 动画时钟重新和系统帧回调时间对齐了，避免了 app 侧偶发长帧把动画时间直接向后拉大，从而避免了“缓存帧平滑 present，但滚动内容停一下再跳”的视觉现象。

## 背景

原始问题中：

- CPF 版本视觉流畅。
- ByteKMP 版本视觉卡顿。
- 两边 DVSync 都能看到生效。
- PresentFence 看起来都没有明显丢帧。

初始 trace 已经说明：

- ByteKMP 的 `RenderView.onDraw` / `BaseComposeScene:render_ohos` / `Recomposer:animation` 有 15-17ms 级长帧。
- CPF 的 app-side draw/render 路径基本没有超过 8.33ms。
- ByteKMP 的 RME render start mode 在分析窗口内基本是 `mode 0 rate 120 skip 0`，不是主因。

关键矛盾是：

```text
显示侧 PresentFence 没丢帧
但人眼看到滚动卡顿
```

这个矛盾可以被“显示帧节奏”和“内容位移节奏”分离解释：

- PresentFence 平稳，只说明屏幕持续 present。
- 视觉滚动流畅，要求每个 present 的内容位置也连续变化。
- DVSync 可以提交缓存/预执行帧，维持 present 节奏。
- 但如果 app/CMP 没有按帧产出新的滚动 offset，缓存帧可能只是旧位置或时间不均匀的位置，肉眼仍会看到停顿/跳动。

## 原 DVSync 接入做了什么

commit `9abeb976513c6338f83985f802565b10be281063`，提交信息为“接入DVsync”。

它主要做了几类事情：

1. 在 scroll/fling 生命周期中打开和关闭 DVSync。
2. 通过 `LocalUiDvsyncSwitch` 把 foundation 层的 fling 状态传到 ui/platform 层。
3. 在 OHOS 侧解析 ArkUI root node handle。
4. 调用 native `androidx_compose_ui_arkui_utils_setUiDvsyncSwitch(handle, true/false)`。
5. 在 surface destroy 时清理 DVSync 状态。

核心代码路径：

```text
ScrollableNode / ScrollingLogic
  -> onFlingStateChanged(true/false)
  -> Scrollable.ohos.kt setUiDvsyncSwitchForFling(enable)
  -> LocalUiDvsyncSwitch
  -> ComposeSceneMediator
  -> DvSyncContext.setUiDvsyncSwitchForFling(enable)
  -> ArkUI native setUiDvsyncSwitch(handle, enable)
```

这次接入的作用边界是 **告诉 ArkUI/RS：当前 fling 期间可以启用 DVSync 能力**。

它没有改变：

- Compose 的动画时钟来源。
- fling 物理模型。
- scroll delta 的计算方式。
- Lazy grid 的 measure/layout/draw 逻辑。

因此，`9abeb976` 没改 `CupertinoFlingBehavior.kt` 是合理的。它是在做 DVSync 接入，而不是调整滚动物理或动画帧时间。

## 根因分析

### 1. Compose 动画要求使用 frameTimeNanos，不应使用 draw 实际执行时刻

Compose runtime 的 `MonotonicFrameClock` 文档明确说明：

```text
frameTimeNanos should be used when calculating animation time deltas from frame to frame
as it may be normalized to the target time for the frame, not necessarily a direct "now" value.
```

也就是说，动画的时间推进应该使用系统/帧调度给出的 `frameTimeNanos`，而不是代码运行到 draw 时再取一个 `now`。

ByteKMP 修改前的关键路径是：

```kotlin
override fun onDraw(canvas: Canvas) {
    frameDelegate.onFrameStart(currentNanoTime(), id.toString())
    mediator.onRender(canvas, width, height, currentNanoTime())
    frameDelegate.onFrameEnd(currentNanoTime(), id.toString(), preComposeProbe?.isActualLaunched())
}
```

`mediator.onRender(..., currentNanoTime())` 会一路传到：

```kotlin
BaseComposeScene.render(canvas, nanoTime) {
    ...
    frameClock.sendFrame(nanoTime)
    ...
}
```

而 `frameClock.sendFrame(nanoTime)` 会驱动：

- `withFrameNanos`
- `Recomposer:animation`
- `AnimationState.animateDecay`
- fling delta 计算
- `scrollBy(delta)`
- Lazy grid layout/draw

所以修改前，ByteKMP 的 fling 动画时间实际来自 `onDraw()` 发生时的当前时间。

### 2. DVSync 场景下，draw 执行时刻和显示帧节奏更容易解耦

DVSync 的目标是 app 主线程空闲时缓存/插入一些 UI 帧；当出现 app 侧长帧时，RS 可以提交缓存帧，减少显示侧丢帧。

这意味着在 DVSync 场景下：

```text
显示侧 present 节奏
不一定等于
app 侧实时完成新内容的节奏
```

如果 Compose 使用 `currentNanoTime()` 作为动画时间，那么当某一帧 app 侧 render 晚了：

1. `onDraw()` 实际执行时刻被推迟。
2. `currentNanoTime()` 比目标帧时间更晚。
3. `frameClock.sendFrame(currentNanoTime())` 把这个晚到的时间发给动画系统。
4. `animateDecay` 认为时间已经过去了更多。
5. 本帧或下一帧的 `delta = value - lastValue` 变大。
6. 视觉上表现为滚动内容先停顿，再跳一段。

同时，RS/PresentFence 仍然可能持续上屏缓存帧，所以 trace 上看“没丢帧”，但用户看到“卡了一下”。

### 3. delta 变大为什么会造成视觉卡顿

这里需要把两个概念拆开：

```text
显示帧率：屏幕是否按 120Hz 持续 present
运动流畅度：相邻 present 帧里的内容位置是否均匀变化
```

PresentFence 不丢，只能证明第一件事成立；视觉不卡顿还要求第二件事成立。

假设当前抛滑速度下，理想情况下内容每帧移动约 10px。在 120Hz 下，用户期待看到：

```text
帧 1：offset = 0px
帧 2：offset = 10px
帧 3：offset = 20px
帧 4：offset = 30px
帧 5：offset = 40px
```

每一帧都上屏，并且每一帧的内容位移也近似均匀，所以视觉是顺滑的。

但 ByteKMP 修改前，在 app 侧偶发长帧 + DVSync 缓存帧补 present 的情况下，屏幕可能仍然按节奏 present，但内容位置变成：

```text
帧 1：offset = 0px
帧 2：offset = 10px
帧 3：offset = 10px   <- 缓存/旧内容继续上屏，显示没丢，但内容没动
帧 4：offset = 30px   <- 动画时间补偿后 delta 变大，内容一下跳过去
帧 5：offset = 40px
```

这时从 PresentFence 看，帧 1-5 都 present 了；但从运动轨迹看，相邻帧位移是：

```text
10px, 0px, 20px, 10px
```

而不是：

```text
10px, 10px, 10px, 10px
```

人眼对这种“不均匀速度”非常敏感。`0px` 那一下会被感知为停顿，后面的 `20px` 会被感知为跳动，所以整体感觉就是卡顿。

这个问题和传统 FPS 丢帧不同。传统丢帧是显示侧少 present 了；这里是显示侧 present 了，但某些 present 帧里的内容位置没有按预期更新。也可以理解为 **空间上的运动采样不均匀**，而不是单纯的 **时间上的 present 缺失**。

需要特别说明：上面的 `帧 3 offset = 10px` 只是 DVSync 缓存帧的一种可能，不是唯一可能。DVSync 可能提交的是旧内容，也可能提交的是 app 空闲期预执行出来的更接近目标时间的内容。

因此，Vsync3 的缓存帧至少有两种形态。二者都可能让 PresentFence 看起来“不丢帧”，但视觉效果不同：

```text
情况 A：DVSync 提交的是旧内容
结果：Vsync3 仍然停一下，但 Vsync4 不再跳太远，卡顿感明显减轻。

情况 B：DVSync 提交的是预执行出来的新内容
结果：Vsync3 也有正确中间 offset，视觉上接近真正连续滚动。
```

#### 情况 A：缓存帧还是旧位置

```text
Vsync1：present offset = 0px
Vsync2：present offset = 10px
Vsync3：present offset = 10px   <- 缓存/旧内容
Vsync4：present offset = 20px   <- 修改 frameTime 后，下一帧不再额外跳远
Vsync5：present offset = 30px

视觉位移：10px, 0px, 10px, 10px
```

这种情况下，Vsync3 仍然有一次“内容没动”。修复 frameTime 后并不能凭空让 app 在 Vsync3 产出真实新内容，但它避免了下一帧因为 `currentNanoTime()` 晚到而跳到 30px。也就是说，它把修改前可能出现的：

```text
10px, 0px, 20px, 10px
```

改善成：

```text
10px, 0px, 10px, 10px
```

人的视觉对“重复一帧，然后继续正常速度”通常比“重复一帧，然后下一帧跳两倍距离”不敏感得多。因此即使 Vsync3 还是旧内容，卡顿感也会明显减轻，甚至在高速滚动中不容易察觉。

#### 情况 B：缓存帧是预执行出来的较新位置，也就是更理想的情况

```text
Vsync1：present offset = 0px
Vsync2：present offset = 10px
Vsync3：present offset = 20px   <- DVSync 预执行/缓存的新内容
Vsync4：present offset = 30px
Vsync5：present offset = 40px

视觉位移：10px, 10px, 10px, 10px
```

这种情况下，DVSync 不只是补住 present，还补住了内容位置连续性，视觉上会更接近真正的 120Hz 连续滚动。

仅从 PresentFence 看，无法准确判断 Vsync3 的缓存帧到底是 `10px` 还是 `20px`。PresentFence 只告诉我们某个 buffer/帧被 present 了，不直接告诉我们这个 buffer 内部的滚动 offset。要精确判断，需要额外证据，例如：

- 在 Compose scroll/fling 层打 offset/delta 日志或 trace。
- 给每次提交到 RS 的 buffer/content frame 打业务序号、scroll offset 或 frameTime。
- 在 trace 中关联 app 侧 `frameClock.sendFrame`、draw 完成、RS receive/submit/present 的 frame id。
- 做视频逐帧或截图像素比对，估算相邻 present 帧的内容位移。

所以对当前问题能做的准确判断是：修改前视觉卡顿来自“内容位移采样不均匀”，修改后卡顿消失说明 frameTime 修复让后续真实内容的 delta 恢复稳定；但 Vsync3 具体是旧 offset 还是预执行 offset，不能只靠 PresentFence 精确判定。

为什么修改 frameTime 后能解决这个问题？

修改前，Compose fling 的动画时间来自 `currentNanoTime()`。如果 app render 晚了，动画系统会认为时间真的过去了更多，于是 `animateDecay` 计算出的动画 value 跨度变大，`delta = value - lastValue` 也变大。这个大 delta 会在下一次真实渲染时把列表推进更远。

修改后，Compose fling 的动画时间来自 ArkUI `FrameCallback.frameTimeNanos`。这个时间代表系统帧节奏，而不是 draw 代码实际跑到那一行的时刻。即使 app 某一帧执行偏晚，也不会把这部分执行延迟直接转化为更大的滚动 delta。于是内容 offset 的变化更接近：

```text
10px, 10px, 10px, 10px
```

而不是：

```text
10px, 0px, 20px, 10px
```

这就是视觉卡顿消失的直接原因。

### 4. 修改后为什么卡顿消失

修改后：

```kotlin
override fun onFrame(frameTime: Long) {
    lastOnFrameTimeNanos = frameTime
    ...
}

override fun onDraw(canvas: Canvas) {
    val drawStartTimeNanos = currentNanoTime()
    val renderFrameTimeNanos = if (lastOnFrameTimeNanos > 0L) {
        lastOnFrameTimeNanos
    } else {
        drawStartTimeNanos
    }
    mediator.onRender(canvas, width, height, renderFrameTimeNanos)
}
```

上层 `RenderFrameCallback.onFrame(frameTimeNanos)` 已经明确把 ArkUI `FrameCallback` 的 `frameTimeNanos` 传给 native view：

```typescript
onFrame(frameTimeNanos: number) {
    this.nativeView?.onFrame(frameTimeNanos)
}
```

因此修改后，Compose 的动画时钟来源变成：

```text
ArkUI FrameCallback.frameTimeNanos
  -> RenderingUIView.onFrame(frameTime)
  -> BaseComposeScene.render(..., frameTime)
  -> frameClock.sendFrame(frameTime)
  -> animateDecay / scrollBy(delta)
```

这带来两个效果：

1. 动画 delta 按系统帧时间推进，而不是按 draw 执行时间推进。
2. 即使 app 某帧 render 晚了，也不会把“执行晚了多少”直接转换成更大的滚动 delta。

所以视觉上不再出现“缓存帧看似平稳 present，但内容位置突然跳”的问题。

## 为什么报告/验证里涉及 CupertinoFlingBehavior.kt

需要区分两件事：

### 1. 修复根因不依赖修改 CupertinoFlingBehavior 行为

真正改变行为并解决视觉卡顿的是 `RenderingUIView.kt` 中 frame time 来源的修正。

`CupertinoFlingBehavior.kt` 当前的修改只是补充验证打点：

```kotlin
trace("CupertinoFling:scrollBy") {
    scrollBy(delta)
}

trace(
    "CupertinoFling:frame " +
        "frameTimeNs=$lastFrameTimeNanos " +
        "delta=$delta " +
        "consumed=$consumed " +
        "velocity=$velocityLeft " +
        "scrollByNs=$scrollByDurationNanos " +
        "cancelled=$scrollByCancelled"
) {}
```

它没有改变：

- `AnimationState`
- `animateDecay`
- `delta = value - lastValue`
- `scrollBy(delta)`
- `lastValue = value`
- `velocityLeft = this.velocity`
- cancel 条件

所以它不是修复逻辑的一部分，而是诊断/观测的一部分。

### 2. 为什么需要观察 CupertinoFlingBehavior

ByteKMP OHOS 默认 fling 行为来自：

```kotlin
internal actual fun platformDefaultFlingBehavior(): ScrollableDefaultFlingBehavior =
    CupertinoFlingBehavior(CupertinoScrollDecayAnimationSpec().generateDecayAnimationSpec())
```

商品瀑布流抛滑时，真实滚动 offset 的每帧变化最终在这里计算：

```kotlin
AnimationState(...).animateDecay(flingDecay) {
    val delta = value - lastValue
    val consumed = scrollBy(delta)
    lastValue = value
    velocityLeft = this.velocity
}
```

视觉卡顿关心的不是“是否 present”，而是“每帧滚动 offset 是否连续”。因此需要在 fling 层打点观察：

- `frameTimeNs` 是否稳定。
- `delta` 是否突然变大。
- `consumed` 是否异常。
- `scrollByNs` 是否明显变长。
- 是否有 `cancelled=true`。

也就是说，`CupertinoFlingBehavior.kt` 是为了证明/排除“滚动 offset 侧”的问题，不是因为 DVSync 接入必须修改它。

## 当前三个文件改动的性质和风险

### 1. RenderingUIView.kt

性质：正式修复候选。

改动内容：

- 保存 `onFrame(frameTime)`。
- `onDraw()` 渲染时优先使用 `lastOnFrameTimeNanos`。
- 没有 frameTime 时 fallback 到 `currentNanoTime()`。
- 添加 `RenderingUIView:onRender frameTimeSource=...` trace。

收益：

- 让 Compose 动画时钟回归 frame callback 时间。
- 与 Compose `MonotonicFrameClock` 的语义一致。
- 实测已经消除视觉卡顿。

潜在风险：

1. 如果存在没有 `onFrame()` 但直接触发 `onDraw()` 的路径，可能会使用上一次 frameTime。
   - 当前代码有 fallback，但 fallback 只覆盖“从未收到 onFrame”的情况。
   - 如果收到过 onFrame，后续某次 draw 不是由 frame callback 驱动，就可能使用旧 frameTime。
2. 如果同一个 `onFrame(frameTime)` 之后触发多次 `onDraw()`，这些 draw 会使用相同 frameTime。
   - 对动画来说，这通常比使用多个 `currentNanoTime()` 更合理，因为同一显示帧内不应推进多次动画时间。
   - 但如果某些非动画绘制依赖每次 draw 都推进时间，需要单独验证。
3. 如果 ArkUI 传入的 `frameTimeNanos` 不严格单调，Compose 动画可能受影响。
   - 目前从 `RenderFrameCallback.onFrame(frameTimeNanos)` 命名和实测看，它是正确的帧时间纳秒。

建议：

- 保留这个改动作为正式修复方向。
- 可以增加一个保护：仅当 `lastOnFrameTimeNanos > 0L` 且未明显落后/异常时使用它，否则 fallback 到 `drawStartTimeNanos`。
- 也可以在 `onDraw()` 消费一次 frameTime 后记录 `lastRenderedFrameTimeNanos`，避免未来重复发送完全相同 frameTime 导致隐性问题。不过当前实测已经正常，是否需要取决于是否存在一帧多次 draw 的真实路径。

### 2. BaseComposeScene.ohos.kt

性质：诊断打点。

改动内容：

在 `BaseComposeScene:render_ohos` 内部拆分 trace：

- `BaseComposeScene:performScheduledEffects`
- `BaseComposeScene:performScheduledRecomposerTasks`
- `BaseComposeScene:frameClock.sendFrame`
- `BaseComposeScene:doLayout`
- `BaseComposeScene:onDrawSnapshot`
- `BaseComposeScene:draw`

收益：

- 可以判断 app-side 长帧到底发生在动画帧钟、重组、layout，还是 draw。
- 对后续分析 LazyStaggeredGrid、图片、文本布局、分页状态更新非常有用。

潜在风险：

- 每帧增加多个 HiTrace section，有一定开销。
- trace 名称固定，开销比动态字符串小，但仍不建议无条件长期留在生产版本。

建议：

- 性能验证阶段保留。
- 正式合入时建议加开关，或只保留最关键的 `BaseComposeScene:frameClock.sendFrame` / `BaseComposeScene:draw`。

### 3. CupertinoFlingBehavior.kt

性质：诊断打点，不是正式修复必需项。

改动内容：

- 给 `scrollBy(delta)` 加 `CupertinoFling:scrollBy` trace。
- 每帧生成 `CupertinoFling:frame frameTimeNs=... delta=... consumed=... velocity=... scrollByNs=... cancelled=...` trace。
- 使用 `TimeSource.Monotonic` 统计 `scrollBy` 耗时。

收益：

- 可以直接看到 fling 每帧 offset 推进是否均匀。
- 可以证明修复后 `frameTimeNs` / `delta` 是否更平稳。
- 可以定位 Lazy grid 的 `scrollBy` 是否耗时异常。

潜在风险：

1. 每个 fling frame 都会做字符串拼接，开销比固定 trace 名称更大。
2. 每帧调用 `TimeSource.Monotonic.markNow()` 和 `elapsedNow()`，有额外成本。
3. trace 数据量会明显增加，影响 trace 可读性。
4. 虽然不改变滚动物理逻辑，但在极限性能场景下，观测代码本身可能轻微扰动性能。

建议：

- 不建议把这部分无条件带入生产。
- 如果要保留，建议加性能开关，例如只在 `KPerfComposeConfig.enableTraceDelegate` 或专门的 fling debug flag 打开时启用。
- 正式修复可以只保留 `RenderingUIView.kt` 的 frameTime 修复，把 `CupertinoFlingBehavior.kt` 的打点撤掉或开关化。

## 是否会影响其他东西

### 正向影响

1. 所有 OHOS Compose 动画都更符合 Compose frame clock 语义。
2. fling、scroll、AnimatedVisibility、Transition 等基于 `withFrameNanos` 的动画会使用系统帧时间，而不是 draw 执行时刻。
3. 在 DVSync、预执行、主线程偶发长帧等场景下，动画时间更稳定。

### 需要关注的影响面

1. 非 fling 动画
   - 例如普通 alpha/scale/position animation。
   - 这些动画也会使用新的 frameTime。
   - 理论上这是更正确的行为，但需要跑几个基础动画页面确认没有速度异常。

2. 一帧多次 draw
   - 如果同一个 `frameTimeNanos` 被用于多次 draw，动画不会在同一帧内重复推进。
   - 这通常是正确的，但如果某些路径过去依赖 `currentNanoTime()` 每次 draw 都变化，可能出现行为差异。

3. 非 frame callback 驱动的 draw
   - 如果存在手动 draw 或特殊 precompose draw，可能使用旧 frameTime。
   - 当前有初始 fallback，但不是完整的 stale frameTime 保护。

4. 监控数据口径
   - `frameDelegate.onFrameStart/onFrameEnd` 仍使用实际 `currentNanoTime()`，所以帧耗时监控仍表示真实执行耗时。
   - `mediator.onRender` 使用 frameTime，表示动画时间。
   - 这两个时间口径分离是合理的：一个测执行耗时，一个驱动动画。

## 建议的正式方案

建议把当前改动拆成两类：

### 必须保留

`RenderingUIView.kt` 的 frameTime 修复：

```text
onFrame(frameTimeNanos) 保存系统帧时间
onDraw() 用该 frameTimeNanos 调 mediator.onRender
fallback 到 currentNanoTime()
```

这是解决视觉卡顿的核心。

### 建议开关化或回收

`BaseComposeScene.ohos.kt` 的细粒度 trace：

- 可保留到性能验证版本。
- 正式版本建议开关化。

`CupertinoFlingBehavior.kt` 的每帧 trace：

- 验证完成后建议撤掉或开关化。
- 它不是 DVSync 修复必要逻辑。

## 后续验证建议

1. 使用修改后的包，对比以下页面：
   - 淘宝首页 Demo 瀑布流抛滑。
   - 普通 LazyColumn 抛滑。
   - 横向 scrollable。
   - 普通 Compose 动画页面。

2. trace 中重点看：
   - `RenderingUIView:onRender frameTimeSource=onFrame`
   - `BaseComposeScene:frameClock.sendFrame`
   - `CupertinoFling:frame`
   - `CupertinoFling:scrollBy`
   - PresentFence gap
   - App ReceiveVsync gap

3. 如果保留 `CupertinoFling:frame` 打点，重点检查：
   - 修复后 `frameTimeNs` 是否按约 8.33ms 递增。
   - `delta` 是否没有异常尖峰。
   - `scrollByNs` 是否仍有明显长尾。

## 最终判断

这次修复有效的本质是：

```text
修复前：
Compose 动画时间 = draw 实际执行时刻
app 长帧会污染动画时间
DVSync 能补 present，但补不了内容位移连续性
视觉上出现停顿/跳动

修复后：
Compose 动画时间 = 系统帧回调 frameTimeNanos
动画 delta 与显示帧节奏对齐
DVSync 补 present 的同时，内容位移也更连续
视觉卡顿消失
```

因此，ByteKMP 的视觉卡顿不是“DVSync 无效”，而是 **DVSync 生效后暴露出 Compose 动画时钟来源不正确的问题**。修正 frameTime 来源后，显示侧和内容侧的节奏重新对齐，问题消失。
