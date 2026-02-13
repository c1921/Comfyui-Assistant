# RunningHub Workflow Assistant 使用说明

一个原生 Android 应用，用于通过 RunningHub Workflow API 发起图像生成任务、轮询结果并下载到相册。

## 1. 功能简介

- 使用 `workflowId + nodeInfoList` 调用 RunningHub 工作流任务
- 支持文本参数：
  - Prompt（必填）
  - Negative（可选）
- 自动轮询任务状态（排队/运行/成功/失败）
- 结果图片预览
- 一键下载到系统相册 `Pictures/RunningHubAssistant`
- API Key 本地加密保存（`EncryptedSharedPreferences`）

## 2. 使用前准备

在 RunningHub 平台完成以下准备：

1. 获取 API Key
2. 获取 `workflowId`
3. 确保目标 workflow 在网页端至少手动成功运行过一次
4. 从工作流 API JSON 中确认可编辑节点的 `nodeId` 与 `fieldName`

说明：
- `workflowId` 通常来自工作流页面地址尾部数字。
- 如果你的 workflow 没有对应文本节点映射，App 无法正确覆盖参数。

## 3. 安装 APK（真机）

调试包路径：

- `app/build/outputs/apk/debug/app-debug.apk`

可用 ADB 安装（设备已开启 USB 调试）：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. 首次配置（Settings 页）

打开 App 后切到 `Settings`，填写并保存：

1. `API key`
2. `workflowId`
3. `Prompt nodeId`（必填）
4. `Prompt fieldName`（必填，常见为 `text`）
5. `Negative nodeId`（可选）
6. `Negative fieldName`（可选）

点击 `Save config` 完成保存。

注意：
- Negative 映射必须“nodeId + fieldName”同时填写，不能只填一个。
- 你也可以点 `Clear API key` 清空本地 Key。

## 5. 生成流程（Generate 页）

1. 输入 `Prompt`
2. 可选输入 `Negative`
3. 点击 `Generate`
4. 观察状态卡片：
   - `queued`：排队中（对应 `code=813`）
   - `running`：运行中（对应 `code=804`）
   - `success`：成功返回图片（对应 `code=0`）
   - `failed`：任务失败（常见 `code=805`）
   - `timeout`：超时（默认最多轮询约 10 分钟）
5. 成功后点击 `Download to gallery` 保存图片

## 6. 常见错误与排查

- `API key is invalid or unauthorized`（802）
  - 检查 API Key 是否正确、是否过期
- `nodeInfoList does not match the workflow mapping`（803）
  - 检查 `nodeId`/`fieldName` 是否与 workflow API JSON 一致
- `Workflow is not saved or has never run successfully on web`（810）
  - 先到 RunningHub 网页端保存并手动成功运行一次
- `Insufficient balance`（416/812）
  - 账户余额不足
- `Rate limit exceeded`（1003）
  - 降低请求频率后再试
- `System is busy`（1011/1005）
  - 稍后重试

## 7. 安全与数据说明

- API Key 仅保存在本机加密存储中
- 应用不会上传你的 Key 到第三方服务
- 生成结果通过 RunningHub 返回的 URL 获取

## 8. 开发与构建

项目根目录执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

若你要编译 AndroidTest，网络环境需能访问 Google Maven（`dl.google.com`）。

