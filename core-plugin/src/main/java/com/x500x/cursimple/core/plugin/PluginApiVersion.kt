package com.x500x.cursimple.core.plugin

object PluginApiVersion {
    /**
     * 3 起支持扩展组件（manifest.kind = "extension"）。导课插件照旧声明 2 也能装；
     * 扩展组件声明 3，老版本 App 会按「接口版本太新」拒装，不会把它当导课插件跑。
     */
    /** 4 起支持组件自带的设置/登录页面与通用 UI 调用接口。 */
    const val CURRENT = 4

    /** 扩展组件最低要声明的接口版本 */
    const val EXTENSION_MIN = 3
}
