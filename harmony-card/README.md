# 课简原生鸿蒙卡片

这是独立的 Stage / Form Kit 卡片应用工程，面向 HarmonyOS NEXT 及以上。它不能嵌进 Android APK，也不从 Android 私有目录读取数据。

已实现的源码：今日课程与笔记待办两种动态服务卡片、2×2 / 2×4 / 4×4 配置、导入后刷新已添加卡片、置顶与优先级文字、浅深主题和三种界面语言。数据来自用户主动选取的真实文件：Android 课简「课表导出到日历」生成的 `.ics`，以及笔记分享生成的 `.csnotes`。课程解析支持课简的周循环和 EXDATE，错误的规则会拒绝导入；没有导入的内容不会伪装成真实课程。

## 当前验证状态

本机没有 DevEco Studio / HarmonyOS SDK、鸿蒙开发签名和鸿蒙真机，尚未编译 HAP、安装真机或验收系统卡片展示。不能将 Android 模拟器截图当作鸿蒙卡片验收。已运行独立的数据解析测试。Hvigor 插件由匹配的 DevEco 工具链提供；普通 npm 镜像当前无法取得该插件，不能用占位工具或伪造 HAP 代替。

## 本地构建与验证

使用 DevEco Studio 打开本目录，安装对应的 HarmonyOS SDK，按 IDE 提示同步 Hvigor / OHPM。配置自己的调试签名与真机后构建 entry HAP。签名配置留在本地，不上传。

在原生应用中导入课程日历和课简笔记，随后在系统卡片选择器中添加「课简卡片」的今日课程、笔记待办。分别检查三种尺寸、深浅模式、导入后更新和移除卡片；课程开始、结束与跨日显示仍需真机验证。

数据解析测试：`node --experimental-strip-types --test test/`。

## 官方依据

[ArkTS 卡片创建与组件关系](https://raw.githubusercontent.com/openharmony/docs/master/en/application-dev/form/arkts-ui-widget-creation.md)、[FormExtensionAbility 生命周期](https://developer.huawei.com/consumer/en/doc/harmonyos-guides-V5/arkts-ui-widget-lifecycle-V5)、[数据绑定与卡片更新](https://developer.huawei.com/consumer/en/doc/harmonyos-guides/push-form-update)、[系统文件选择器](https://developer.huawei.com/consumer/en/doc/harmonyos-references/js-apis-file-picker)。

本工程只实现桌面卡片。实况窗与锁屏实况窗属于 Live View Kit，需要前台用户行为、场景准入及相应权益；这里不把课程冒充出行或配送，也不使用后台悬浮窗替代系统锁屏能力。
