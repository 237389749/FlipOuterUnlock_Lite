package com.example.flipunlock

import com.example.flipunlock.hook.identity.DeviceIdentityHook
import com.example.flipunlock.hook.identity.CameraCutoutFixHook
import com.example.flipunlock.hook.identity.CutoutAlwaysHook
import com.example.flipunlock.hook.miuihome.SFDeviceGestureHook
import com.example.flipunlock.hook.system_server.RotationFixHook
import com.example.flipunlock.hook.systemui.FlashlightHook
import com.example.flipunlock.hook.systemui.QSColumnsFixHook
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
 *   6. SFDeviceGestureHook — 外屏上滑手势：miuihome 折叠态短路 NavStubView → isInSFDeviceFoldedMode→false + force_fsg_nav_bar→true
 *
 * 无 dexkit 依赖。工具类只保留被上述 hook 用到的部分。
 */
class Main : XposedModule() {

    private val hooks = listOf(
        // DeviceIdentityHook, // [DISABLED 2026-08-11 实验2] isFlipDevice/属性值 hook 全注释，只靠属性层模块
        CameraCutoutFixHook, // 相机 NPE 修复：Display.getCutout() → 有效非 null DisplayCutout（属性1 副作用）
        SystemUiKeyguardFix, // systemui 崩溃环兜底（属性层配套，§38.1/38.2）
        FlashlightHook,      // 控制中心手电筒：跳过翻转对话框/传感器等待
        QSColumnsFixHook,    // 控制中心磁贴列数宽度自适应（横屏/外屏 4 列挤占修复）
        SFDeviceGestureHook, // 外屏上滑手势：isInSFDeviceFoldedMode→false + force_fsg_nav_bar→true
        CutoutAlwaysHook,    // [备用，Config.displayCutout] app 端 cutout 全屏四件套
    )

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        Config.logConfig()
        DeviceGuard.logInfo()
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        log("Main: onSystemServerStarting — gen=${DeviceGuard.gen}")
        // [DISABLED 2026-08-11 实验2] 服务端身份伪造注释（DeviceIdentityHook 全注释）
        // DeviceIdentityHook.hookSystemServer(param)
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
