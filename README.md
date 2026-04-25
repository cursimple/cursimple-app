# 课表查看（Class Schedule Viewer）

一个基于 Kotlin 的 Android 课表查看应用，采用微内核架构，支持：

- Android 7.0（API 24）到 Android 16（targetSdk 36）
- `armeabi-v7a`、`arm64-v8a`、`universal` 多 ABI 构建
- Compose 主界面
- Glance 桌面小组件
- 使用 QuickJS 的 JS 插件完成登录到课表获取全流程
- GitHub Actions CI/CD

## 模块结构

- `app`：应用壳、依赖组装、入口页面、插件资产
- `core-kernel`：微内核协议（插件目录、执行器、统一课表模型）
- `core-js`：QuickJS 执行器与受控 Host 桥接
- `core-data`：DataStore 仓储
- `feature-schedule`：课表页面与同步逻辑
- `feature-widget`：桌面小组件与定时刷新

## 快速开始

### 1) 环境要求

- JDK 17
- Android SDK（含 `platforms;android-36`）

### 2) 构建 Debug

```bash
./gradlew assembleDebug
```

### 3) 构建 Release（含 v7a/v8a/universal）

```bash
./gradlew assembleRelease
```

构建产物目录：

`app/build/outputs/apk/release/`

## JS 插件协议

每个插件必须导出 3 个函数：

- `login(ctx, input)`：登录并返回会话对象
- `fetchSchedule(ctx, session, term)`：获取原始课表
- `normalize(raw)`：转换为标准课表 JSON

标准课表 JSON 示例：

```json
{
  "termId": "2026-spring",
  "updatedAt": "2026-04-25T08:00:00+08:00",
  "dailySchedules": [
    {
      "dayOfWeek": 1,
      "courses": [
        {
          "id": "c1",
          "title": "高等数学",
          "teacher": "张老师",
          "location": "A101",
          "weeks": [1, 2, 3],
          "time": {
            "dayOfWeek": 1,
            "startNode": 1,
            "endNode": 2
          }
        }
      ]
    }
  ]
}
```

示例插件位于：

- `app/src/main/assets/plugins/index.json`
- `app/src/main/assets/plugins/demo-campus.js`

## GitHub Actions

工作流文件：`.github/workflows/android-ci.yml`

- PR：执行单测 + `assembleDebug`
- Push 到 `main`：执行 `assembleRelease` 并上传 APK artifacts
- Tag（如 `v1.0.0`）：自动创建 GitHub Release 并附加 APK

