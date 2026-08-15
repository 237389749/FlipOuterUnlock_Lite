package com.example.flipunlock.hook.systemui

import android.content.res.Resources
import com.example.flipunlock.hook.BaseHook
import com.example.flipunlock.hook.util.*
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * 解除控制中心编辑模式"最少磁贴数"限制（2026-08-12）。
 *
 * 现象：控制中心编辑按钮里磁贴数量最少 N 个（旧版资源 quick_settings_min_num_tiles=12，
 *   HyperOS Compose 编辑实际约 8 个）不能继续减少 → 外屏窄宽下磁贴过多挤占。
 *
 * 机制（b5c1-systemui 反编译）：
 *   TileAdapter:309 mMinNumTiles = resources.getInteger(R.integer.quick_settings_min_num_tiles)
 *   TileAdapter:397 删除磁贴时 if (size <= mMinNumTiles) → 禁止删除(drag_to_remove_disabled)
 *
 * 修复：systemui 进程 hook Resources.getInteger(int)，对 quick_settings_min_num_tiles 返回 0，
 *   解除最少磁贴数限制（可删到 0/自定义最少）。
 *
 * 注意：systemui 进程可能只以 pkg=android 回调，targetPackages 含 "android" + currentProcessName 限定。
 */
object QSTileMinCountFixHook : BaseHook() {
    sixth_deliberate_error_???  // 测试性错误 #6: 第六次触发 CI/邮箱通知(勿合并)

    override val targetPackages = listOf("com.android.systemui", "android")

    override fun setupHooks(param: PackageReadyParam) {
        val process = currentProcessName()
        if (process != "com.android.systemui") {
            log("QSTileMinCountFix: skip, process=$process")
            return
        }
        log("QSTileMinCountFix: loading for ${param.packageName} (process=$process)")
        safeHook("QSTileMinCountFix") {
            val resClass = param.classLoader.loadClass("android.content.res.Resources")
            val method = resClass.method("getInteger", Int::class.javaPrimitiveType!!)
            hook(method) { chain ->
                val res = chain.thisObject as? Resources ?: return@hook chain.proceed()
                val id = chain.args[0] as? Int ?: return@hook chain.proceed()
                val name = runCatching { res.getResourceName(id) }.getOrNull()
                if (name != null && name.endsWith("quick_settings_min_num_tiles")) {
                    log("QSTileMinCountFix: $name → 0 (解除最少磁贴限制)")
                    return@hook 0
                }
                chain.proceed()
            }
            log("QSTileMinCountFix: ✓ quick_settings_min_num_tiles → 0")
        }
    }
}
