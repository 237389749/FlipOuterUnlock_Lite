# FlipOuterUnlock_Lite

基于 FlipOuterUnlock_262 精简重建的 LSPosed 模块（无 dexkit 依赖）。
去掉全部 hook 后按需加入，仅保留核心功能。

## Hooks

| Hook | 作用域 | 功能 |
|---|---|---|
| `FlashlightHook` | android + com.android.systemui | 控制中心手电筒：外屏点击直接开关，跳过"翻转手机"对话框/传感器等待 |
| `RotationFixHook` | system_server | 旋转解除：折叠态 `setUserRotation` LOCKED→FREE |
| `DeviceIdentityHook` | *（wildcard） | isFlipDevice→false + hook 属性读取（multi_display_type→1，§38.9 免 root 虚拟改属性）；SystemUI 默认排除（§38.4） |
| `SystemUiKeyguardFix` | com.android.systemui | systemui 崩溃环兜底：`providesTinyKeyguardViewPager` 强制 inflate（§38.1/38.2） |
| `CutoutAlwaysHook` | *（wildcard） | **备用，默认关**：app 端 cutout 全屏四件套，`persist.flipunlock.display.cutout` 开启 |

## 开关

```bash
getprop | grep persist.flipunlock

setprop persist.flipunlock.enable false                     # master kill switch
setprop persist.flipunlock.display.cutout true              # CutoutAlwaysHook（备用，默认关）
setprop persist.flipunlock.identity.exclude.systemui false  # SystemUI 内身份伪造（默认排除，勿开）
```

## 构建

GitHub Actions CI：推送 `main` 分支自动编译，artifact 为 debug APK（未配置签名密钥时）。

## 部署

1. CI artifact 下载 `app/build/outputs/apk/debug/*.apk`
2. `adb install -r <apk>`
3. LSPosed 管理器启用模块，勾选 scope（见 `app/src/main/resources/META-INF/xposed/scope.list`）
4. 重启
