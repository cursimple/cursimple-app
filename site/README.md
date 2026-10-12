# 官网维护

[English](README_en.md)

官网是静态站点，`.github/workflows/pages.yml` 验证后将 `site/` 发布到 GitHub Pages，无需构建 Android。

## 插件与组件入口

官网的「插件与组件」区和页尾分别跳转到插件收录页与组件收录页。「扩展组件」区介绍雨课堂通知和多平台通知，提供各自仓库与 Release 地址。组件注册表为 `cursimple/cursimple-components`，其收录页部署地址是 `https://cursimple.github.io/cursimple-components/`；该仓库的 Pages 首次启用与部署由其自身工作流负责。

平台能力与兼容要求以组件说明为准，网站不固定某个本地 ZIP 的版本号。新增文案需同步三种语言。

## 语言

首次访问默认简体中文，不随浏览器自动改为英文。用户可选择繁體中文或 English，选择会保存；分享链接中的 `?lang=` 优先于保存的选择，可同时保留 `#download` 等锚点。

`i18n.js` 三种语言使用相同的语义键。纯文本使用 `data-i18n`；包含图标或链接时只标记文字子元素。图片说明、读屏标签和元信息分别使用 `data-i18n-alt`、`data-i18n-aria-label`、`data-i18n-content`。动态文案用 `SiteI18n.t()`，日期用 `formatDate()`。

## 公告

在 `release-notes/` 中维护原文 `v<version>.md`、繁体 `v<version>.zh-TW.md` 和英文 `v<version>.en.md`。图片相对各自 Markdown 文件解析。

本地缺失时读取发布标签下的同名文档；缺少翻译时回退原文并提示。切换语言或版本会丢弃旧请求结果。每次点翻页按钮立即更新页面和指示，手动滑动也同步跟随。

## 验证

在仓库根目录执行：

```sh
node --check site/i18n.js
node --check site/releases.js
node --check site/main.js
node --test site/tests/*.test.cjs
node site/check-links.cjs
python3 -m http.server 8892 --bind 127.0.0.1 --directory site
```

检查默认中文、手动选择及刷新保留、手机菜单、下载架构和公告翻页。测试覆盖语言优先级、字典完整性、断网回退和竞争请求。
