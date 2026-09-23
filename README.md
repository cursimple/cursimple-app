# update-feed

这个分支由发版流程（.github/workflows/android-release.yml 的 Publish update feed）自动维护，请勿手改。

- `beta.json`：含测试版在内的最新版的 update.json
- `stable.json`：最新正式版的 update.json

App 检查更新时先经 jsDelivr 等 CDN 读这里，读不到才退回 GitHub API。
