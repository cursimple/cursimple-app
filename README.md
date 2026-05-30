[English](README_en.md) | [中文](README.md)

# 课简 (CurSimple)

一个基于 Kotlin 的 Android 课表应用「课简」，帮助你轻松管理课程表。

## 功能特性

- **广泛兼容性**：支持 Android 7.0（API 24）到 Android 16（targetSdk 36）
- **多架构支持**：提供 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 分包和通用包
- **现代界面**：使用 Jetpack Compose 构建，提供流畅的现代界面
- **桌面小组件**：基于 Glance 的小组件，快速查看课程表
- **插件系统**：使用 manifest + WebView JS 插件完成学校课表采集
- **插件市场**：从 GitHub 注册表 ([cursimple-plugins](https://github.com/cursimple/cursimple-plugins)) 浏览和安装插件
- **课程提醒**：双后端提醒（系统闹钟 / 应用内闹钟）
- **开源免费**：完全开源，使用 GitHub Actions CI/CD

## 快速开始

### 下载安装

1. 访问 [Releases](https://github.com/cursimple/cursimple-app/releases) 页面
2. 下载适合你设备的 APK：
   - `app-armeabi-v7a-release.apk`：适用于较旧的 32 位 ARM 设备
   - `app-arm64-v8a-release.apk`：适用于现代 64 位 ARM 设备（大多数手机）
   - `app-x86_64-release.apk`：适用于 Intel 架构设备
   - `app-universal-release.apk`：如果不确定选哪个（兼容所有架构但体积较大）
3. 安装 APK（可能需要启用"允许安装未知来源应用"）

### 首次启动

1. 打开应用
2. 系统会引导你设置第一个课程表
3. 使用插件市场查找并安装适合你学校的插件
4. 按照插件说明导入你的课程表

## 使用插件

### 插件市场

插件市场由 GitHub 仓库 [cursimple/cursimple-plugins](https://github.com/cursimple/cursimple-plugins) 驱动，提供针对各种学校和机构的插件列表。

1. 进入应用中的 **插件** 标签页
2. 浏览"插件市场"区域（以 2 列网格显示）
3. 每个插件显示：名称、作者、星标数、描述和最新 release 标签
4. 点击插件查看详情，然后选择"安装"或"在 GitHub 查看"

### 安装插件

每个 GitHub 上的插件仓库必须至少有一个 Release，并包含 `*.zip` 资产。应用会：
1. 通过镜像池访问 GitHub release
2. 从 release 中下载第一个 `*.zip` 资产
3. 自动安装插件

注意：GitHub 自动生成的"Source code"压缩包不会被用作插件包。

### 管理插件

- 已安装的插件会出现在插件列表中
- 可以启用/禁用插件
- 插件设置可以单独配置

## 功能详解

### 课程提醒

- **系统闹钟**：使用 Android 内置闹钟系统，确保可靠通知
- **应用内闹钟**：适用于闹钟权限受限设备的替代后端
- 在 设置 → 提醒 中配置提醒时间

### 桌面小组件

- 将课简小组件添加到主屏幕
- 小组件显示即将到来的课程，并可自定义
- 小组件按可配置的时间间隔自动刷新

### 数据管理

- 课程表数据使用 Android DataStore 本地存储
- 设置中提供备份和恢复功能
- 插件数据单独存储，便于管理

## 常见问题

### 插件安装失败

- 确保网络连接稳定
- 检查插件仓库是否有有效的 Release 和 `.zip` 资产
- 尝试切换网络（Wi-Fi/移动数据）

### 课程表不显示

- 确认插件已安装并启用
- 检查插件是否已成功导入课程表
- 尝试手动刷新课程表视图

### 小组件不更新

- 确保应用有后台刷新权限
- 检查小组件刷新间隔设置
- 尝试移除并重新添加小组件

## 支持与反馈

- **问题反馈**：通过 [GitHub Issues](https://github.com/cursimple/cursimple-app/issues) 报告错误或请求功能
- **讨论交流**：在 [GitHub Discussions](https://github.com/cursimple/cursimple-app/discussions) 参与讨论
- **插件开发**：参见 [插件开发指南](docs/plugin-system.md) 创建自己的插件

## 技术细节

详细的构建说明、模块结构和 CI/CD 配置，请参阅 [开发者文档](README_dev.md)。

<details>
<summary>快速概览</summary>

### 从源码构建

#### 环境要求
- JDK 17
- Android SDK（含 `platforms;android-36`）

#### 配置说明
本地签名配置通过 `keystore.properties`（参见 `keystore.example.properties`）：

```properties
CLASS_VIEWER_KEYSTORE_FILE=.signing/class-viewer.jks
CLASS_VIEWER_KEYSTORE_PASSWORD=替换为仓库密码
CLASS_VIEWER_KEY_ALIAS=替换为密钥别名
CLASS_VIEWER_KEY_PASSWORD=替换为密钥密码
```

#### 构建命令
```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（所有架构）
./gradlew assembleRelease
```

### 项目结构
- `app`：应用壳、依赖组装、入口页面、更新检查
- `core-kernel`：统一课表模型与核心协议
- `core-plugin`：插件 manifest、安装、组件、Web 会话模型
- `core-data`：DataStore 仓储
- `core-reminder`：提醒规则、计划与派发后端
- `feature-schedule`：课表页面与同步逻辑
- `feature-plugin`：插件市场 UI 和 WebView 会话
- `feature-widget`：桌面小组件与定时刷新

### CI/CD
- GitHub Actions 工作流位于 `.github/workflows/`
- CI 在 PR 和推送到 `main` 时运行
- Release 在版本标签（如 `v1.0.0`）时运行

</details>

## 开源许可

本项目为开源项目。详见 [LICENSE](LICENSE) 文件。