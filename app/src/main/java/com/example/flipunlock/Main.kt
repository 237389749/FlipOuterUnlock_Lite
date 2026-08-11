package com.example.flipunlock

import com.example.flipunlock.hook.identity.DeviceIdentityHook
import com.example.flipunlock.hook.identity.CutoutAlwaysHook
import com.example.flipunlock.hook.system_server.RotationFixHook
import com.example.flipunlock.hook.systemui.FlashlightHook
import com.example.flipunlock.hook.systemui.SystemUiKeyguardFix
import com.example.flipunlock.hook.util.Config
import com.example.flipunlock.hook.util.DeviceGuard
import com.example.flipunlock.hook.util.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

internal var module: Main? = null

/**
 * FlipOuterUnlock_Lite — 精简重建版（2026-08-11）。
 *
 * 以 262 为基底，去掉全部 hook 后按需重建。仅保留核心 hook：
 *   1. FlashlightHook      — 控制中心手电筒：外屏点手电筒跳过"翻转手机"对话框/传感器等待，直接开关
 *   2. RotationFixHook     — 旋转解除：折叠态 setUserRotation LOCKED→FREE（isFlipDevice→false 副作用）
 *   3. DeviceIdentityHook  — isFlipDevice→false + hook 属性读取（multi_display_type→1，§38.9 免 root 虚拟改属性）
 *   4. SystemUiKeyguardFix — systemui 崩溃环兜底：providesTinyKeyguardViewPager 强制 inflate（§38.1/38.2）
 *   5. CutoutAlwaysHook    — 备用：app 端 cutout 全屏四件套（默认关闭，persist.flipunlock.display.cutout 开启）
 *
 * 无 dexkit 依赖。工具类只保留被上述 hook 用到的部分。
 */
class Main : XposedModule() {

    private val hooks = listOf(
        DeviceIdentityHook,  // isFlipDevice→false + 属性读取 hook（wildcard，豁免 firstPackage）
        SystemUiKeyguardFix, // systemui 崩溃环兜底（属性层配套，§38.1/38.2）
        FlashlightHook,      // 控制中心手电筒：跳过翻转对话框/传感器等待
        CutoutAlwaysHook,    // [备用，Config.displayCutout] app 端 cutout 全屏四件套
    )

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        Config.logConfig()
        DeviceGuard.logInfo()
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log("Main: onSystemServerStarting — gen=${DeviceGuard.gen}")
        // 服务端身份伪造（§34.7）：flip2 zygisk 注入正常 → 生效；flip1 corepatch 断路 → 装不上（无害）
        DeviceIdentityHook.hookSystemServer(param)
        // 旋转解除：DisplayRotationStubImpl 折叠态 LOCKED→FREE
        RotationFixHook.hook(param)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        log("Main: onPackageReady pkg=${param.packageName} first=${param.isFirstPackage}")
        hooks.forEach { hook ->
            val isWildcard = hook.targetPackages.contains("*")
            val isTargeted = hook.targetPackages.contains(param.packageName)

            if (!isWildcard && !isTargeted) return@forEach

            // 262 基线行为：DeviceIdentityHook 豁免 firstPackage 限制
            if (isWildcard && !param.isFirstPackage && hook !is DeviceIdentityHook) return@forEach

            log("Main: loading ${hook.javaClass.simpleName} for ${param.packageName}")
            hook.hook(param)
        }
    }
}
