package com.example.flipunlock.hook.system_server

import com.example.flipunlock.hook.util.*
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

/**
 * DISPLAY_CUTOUT letterbox 服务端解除（refMD §34.3/§34.6 候选 1+2，双保险）。
 *
 * flip2 应用不全屏的真凶是 AOSP DISPLAY_CUTOUT letterbox（非 MIUI size-compat）：
 *   WindowState.isLetterboxedForDisplayCutout()（flip2-services L2860）：
 *     ① parentFrame 被挖孔裁剪 && ② layoutInDisplayCutoutMode!=3
 *     && ③ mWindowStateStub.isMiuiLayoutInCutoutAlways(attrs)
 *     && ④ 全屏窗口 → letterbox
 *
 * 双保险：
 *   候选 1：WindowStateStubImpl.isMiuiLayoutInCutoutAlways(LayoutParams) → true
 *           （③ 号豁免开关，flip2-miui-services L175 硬编码 false）——单点干净
 *   候选 2：WindowState.isLetterboxedForDisplayCutout() → false
 *           （整个 AOSP 判定直接关）——粗暴但必达
 *
 * 机型守护（2026-08-12 重新启用）：仅 FLIP2 生效——flip1 的 size-compat 链是主攻点
 * （门闸恒 false，冗余保险），且 flip1 system_server 模块不加载，直接跳过。
 *
 * 进程：system_server（依赖注入正常——flip2 zygisk 正常，flip1 corepatch 断路则装不上）
 */
object Flip2CutoutLetterboxHook {

    fun hook(param: SystemServerStartingParam) {
        if (DeviceGuard.gen != DeviceGuard.DeviceGen.FLIP2) {
            log("CutoutLetterboxFix: skip, gen=${DeviceGuard.gen} (FLIP2 only)")
            return
        }
        log("CutoutLetterboxFix: setting up (gen=${DeviceGuard.gen})")
        safeHook("CutoutLetterboxFix") {
            // ── 候选 1：isMiuiLayoutInCutoutAlways → true（③ 号豁免开关）──
            runCatching {
                val cls = param.classLoader.loadClass("com.android.server.wm.WindowStateStubImpl")
                val lp = param.classLoader.loadClass("android.view.WindowManager\$LayoutParams")
                val m = cls.method("isMiuiLayoutInCutoutAlways", lp)
                hook(m, replaceResult(true))
                log("CutoutLetterboxFix: ✓ candidate1 isMiuiLayoutInCutoutAlways → true")
            }.onFailure { log("CutoutLetterboxFix: candidate1 failed: ${it.message}") }

            // ── 候选 2：isLetterboxedForDisplayCutout → false（AOSP 判定直接关）──
            runCatching {
                val ws = param.classLoader.loadClass("com.android.server.wm.WindowState")
                val m = ws.method("isLetterboxedForDisplayCutout")
                hook(m, replaceResult(false))
                log("CutoutLetterboxFix: ✓ candidate2 isLetterboxedForDisplayCutout → false")
            }.onFailure { log("CutoutLetterboxFix: candidate2 failed: ${it.message}") }
        }
    }
}
