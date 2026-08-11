package com.example.flipunlock.hook.util

/**
 * Feature toggles via SystemProperties（精简版，仅保留 Lite 用到的开关）。
 *
 *   getprop | grep persist.flipunlock
 *
 *   setprop persist.flipunlock.enable false                    # master kill switch
 *   setprop persist.flipunlock.display.cutout true             # CutoutAlwaysHook 备用开关（默认关）
 *   setprop persist.flipunlock.identity.exclude.systemui false # SystemUI 内身份伪造（默认排除，勿开）
 */
object Config {
    private val keys = listOf(
        "persist.flipunlock.enable",
        "persist.flipunlock.display.cutout",
        "persist.flipunlock.gesture.sf",
        "persist.flipunlock.identity.exclude.systemui",
    )

    // Master switch
    val enabled: Boolean get() = raw("persist.flipunlock.enable", true)

    // CutoutAlwaysHook 备用开关（默认关闭；开启后所有 scope 包窗口不避让挖孔）
    val displayCutout: Boolean get() = enabled && raw("persist.flipunlock.display.cutout", false)

    // SFDeviceGestureHook 开关（外屏上滑手势执行器，默认开）
    val gestureSf: Boolean get() = enabled && raw("persist.flipunlock.gesture.sf", true)

    // Identity exclusion — SystemUI MUST stay excluded:
    // isFlipDevice→false inside SystemUI makes TinyKeyguardPanelViewController
    // construction NPE on HyperOS3 firmware (b5c1e89), crashing KeyguardService
    // in a loop (§38.1/38.2)。崩溃兜底由 SystemUiKeyguardFix 独立承担，
    // 但 SystemUI 内身份伪造副作用过大（§38.4）——默认排除，勿开。
    val identityExcludeSystemUi: Boolean get() = raw("persist.flipunlock.identity.exclude.systemui", true)

    /** Print all toggle keys and current values. */
    fun logConfig() {
        val sb = StringBuilder("═══ FlipOuterUnlock Config ═══\n")
        for (key in keys) {
            sb.append("  $key = ${readProp(key)}\n")
        }
        sb.append("  (getprop | grep persist.flipunlock)")
        log(sb.toString())
    }

    private fun raw(key: String, default: Boolean): Boolean {
        return try {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType!!)
                .invoke(null, key, default) as? Boolean ?: default
        } catch (_: Exception) {
            default
        }
    }

    private fun readProp(key: String): String {
        return try {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java, String::class.java)
                .invoke(null, key, "") as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
