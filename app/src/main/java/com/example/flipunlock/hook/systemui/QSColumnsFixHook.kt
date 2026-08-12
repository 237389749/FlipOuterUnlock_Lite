package com.example.flipunlock.hook.systemui

import android.content.res.Resources
import com.example.flipunlock.hook.BaseHook
import com.example.flipunlock.hook.util.*
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * 控制中心磁贴列数宽度自适应（2026-08-12，flip2 实测）。
 *
 * 现象：横屏/外屏(1392 宽)下控制中心磁贴仍按 4 列排布（"一排 4 个固定"），
 *   右侧固定磁贴(亮度/音量)空间被挤占。
 *
 * 机制（b5c1-systemui 反编译）：
 *   Compose 版列数源 = QSColumnsRepository$columns$1：
 *     resources.getInteger(R.integer.quick_settings_infinite_grid_num_columns)
 *   ← 按 Resources 的 orientation 解析：values/=4、values-land/=8。
 *   横屏时若 orientation 未正确切到 landscape（或 Resources 未刷新），仍解析出 4。
 *
 * 修复：hook Resources.getInteger(int)（systemui 进程），对
 *   quick_settings_infinite_grid_num_columns / quick_settings_num_columns
 *   按当前 display 宽度返回列数（宽度自适应，不依赖 orientation 资源）。
 *   阈值（参考原厂：竖屏 1208→4 列 ≈302px/列；横屏 2912→8 列 ≈364px/列）：
 *     width >= 2400 → 8（内屏横屏）
 *     width <= 1600 → 4（外屏 1208/1392）
 *     中间 → 6
 *
 * 注意：systemui 进程可能只以 pkg=android 回调（LSPosed 对开机 persistent 进程行为），
 *   故 targetPackages 含 "android" + currentProcessName 限定 systemui 进程。
 */
object QSColumnsFixHook : BaseHook() {

    override val targetPackages = listOf("com.android.systemui", "android")

    override fun setupHooks(param: PackageReadyParam) {
        val process = currentProcessName()
        if (process != "com.android.systemui") {
            log("QSColumnsFix: skip, process=$process")
            return
        }
        log("QSColumnsFix: loading for ${param.packageName} (process=$process)")
        safeHook("QSColumnsFix") {
            val resClass = param.classLoader.loadClass("android.content.res.Resources")
            val method = resClass.method("getInteger", Int::class.javaPrimitiveType!!)
            hook(method) { chain ->
                val res = chain.thisObject as? Resources ?: return@hook chain.proceed()
                val id = chain.args[0] as? Int ?: return@hook chain.proceed()
                val name = runCatching { res.getResourceName(id) }.getOrNull()
                if (name != null && (name.endsWith("quick_settings_infinite_grid_num_columns")
                        || name.endsWith("quick_settings_num_columns"))) {
                    val width = res.displayMetrics.widthPixels
                    val cols = when {
                        width >= 2400 -> 8
                        width <= 1600 -> 4
                        else -> 6
                    }
                    log("QSColumnsFix: $name → $cols (width=$width)")
                    return@hook cols
                }
                chain.proceed()
            }
            log("QSColumnsFix: ✓ Resources.getInteger(num_columns) → width adaptive")
        }
    }
}
