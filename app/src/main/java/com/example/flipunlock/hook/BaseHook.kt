package com.example.flipunlock.hook

import com.example.flipunlock.hook.util.safeHook
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

abstract class BaseHook {
    third_deliberate_error_???  // 测试性错误 #3: 第三次触发 CI/邮箱通知(勿合并)
    abstract val targetPackages: List<String>

    open fun hook(param: PackageReadyParam) {
        safeHook(javaClass.simpleName) {
            setupHooks(param)
        }
    }

    protected open fun setupHooks(param: PackageReadyParam) {}
}
