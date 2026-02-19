# RunningHub Workflow Assistant 使用说明

一个原�?Android 应用，用于通过 RunningHub Workflow API 发起图像生成任务、轮询结果并下载到相册�?

## 0. 项目结构（重构后�?

- `:app`：应用入口、Tab 导航、依赖装�?
- `:core:model`：共享模型与校验逻辑
- `:core:network`：RunningHub API、解析器、生成仓储实�?
- `:core:storage`：配置加密存储与 `ConfigRepository`
- `:core:media`：图片下�?解码/保存�?`MediaSaver`
- `:feature:generate`：生成页 UI + `GenerateViewModel`
- `:feature:settings`：设置页 UI + `SettingsViewModel`

## 1. 功能简�?

- 使用 `workflowId + nodeInfoList` 调用 RunningHub 工作流任�?
- 支持文本参数�?
  - Prompt（必填）
  - Negative（可选）
- 自动轮询任务状态（排队/运行/成功/失败�?
- 结果图片预览
- 一键下载到系统相册 `files/internal_album` (app-private)
- API Key 本地加密保存（`EncryptedSharedPreferences`�?

## 2. 使用前准�?

�?RunningHub 平台完成以下准备�?

1. 获取 API Key
2. 获取 `workflowId`
3. 确保目标 workflow 在网页端至少手动成功运行过一�?
4. 从工作流 API JSON 中确认可编辑节点�?`nodeId` �?`fieldName`

说明�?
- `workflowId` 通常来自工作流页面地址尾部数字�?
- 如果你的 workflow 没有对应文本节点映射，App 无法正确覆盖参数�?

## 3. 安装 APK（真机）

调试包路径：

- `app/build/outputs/apk/debug/app-debug.apk`

可用 ADB 安装（设备已开�?USB 调试）：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 4. 首次配置（Settings 页）

打开 App 后切�?`Settings`，填写并保存�?

1. `API key`
2. `workflowId`
3. `Prompt nodeId`（必填）
4. `Prompt fieldName`（必填，常见�?`text`�?
5. `Negative nodeId`（可选）
6. `Negative fieldName`（可选）

点击 `Save config` 完成保存�?

注意�?
- Negative 映射必须“nodeId + fieldName”同时填写，不能只填一个�?
- 你也可以�?`Clear API key` 清空本地 Key�?

## 5. 生成流程（Generate 页）

1. 输入 `Prompt`
2. 可选输�?`Negative`
3. 点击 `Generate`
4. 观察状态卡片：
   - `queued`：排队中（对�?`code=813`�?
   - `running`：运行中（对�?`code=804`�?
   - `success`：成功返回图片（对应 `code=0`�?
   - `failed`：任务失败（常见 `code=805`�?
   - `timeout`：超时（默认最多轮询约 10 分钟�?
5. 成功后点�?`View in album (auto archived)` 保存图片

## 6. 常见错误与排�?

- `API key is invalid or unauthorized`�?02�?
  - 检�?API Key 是否正确、是否过�?
- `nodeInfoList does not match the workflow mapping`�?03�?
  - 检�?`nodeId`/`fieldName` 是否�?workflow API JSON 一�?
- `Workflow is not saved or has never run successfully on web`�?10�?
  - 先到 RunningHub 网页端保存并手动成功运行一�?
- `Insufficient balance`�?16/812�?
  - 账户余额不足
- `Rate limit exceeded`�?003�?
  - 降低请求频率后再�?
- `System is busy`�?011/1005�?
  - 稍后重试

## 7. 安全与数据说�?

- API Key 仅保存在本机加密存储�?
- 应用不会上传你的 Key 到第三方服务
- 生成结果通过 RunningHub 返回�?URL 获取

## 8. 开发与构建

项目根目录执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

若你要编�?AndroidTest，网络环境需能访�?Google Maven（`dl.google.com`）�?

## 9. Settings update (2026-02-19)

- Settings now includes a **Video settings** section.
- Image and video configurations share the same `API key`.
- Newly added optional video fields:
  - `videoWorkflowId`
  - `videoPromptNodeId`
  - `videoPromptFieldName`
- Save validation rule:
  - Video mapping must be **all filled or all empty**.
  - Incomplete video mapping blocks saving config.
- Existing image generation behavior is unchanged.

## 10. Generate mode update (2026-02-19)

- Generate page now supports switching between **Image** and **Video** modes.
- Video mode uses ComfyUI workflow API with `videoWorkflowId`, `videoPromptNodeId`, and `videoPromptFieldName`.
- Video mode only requires positive prompt input.
- Results can contain both images and videos and are rendered by media type.
- Generation success now auto archives outputs into app-private internal album.
- Result action is **View in album** (no auto save to public gallery in generate flow).

## 11. SS_tools duck video decode update (2026-02-19)

- Added SS_tools-style payload decode for duck carrier images with `*.binpng` payload extension.
- Video payload preview now auto-resolves from image output to local playable `.mp4`.
- Internal archive now also supports decoded duck video payload (`.mp4`).
- Output extension for decoded duck video payload is fixed to `.mp4`.

## 12. Internal album auto archive update (2026-02-19)

- Generation success now auto archives images/videos into app-private storage:
  - `files/internal_album/tasks/<taskId>/out_<index>.<ext>`
  - `files/internal_album/tasks/<taskId>/task.json`
  - `files/internal_album/index.json`
- Outputs are no longer auto written to system gallery (`MediaStore`) in generate flow.
- Generate result action changed from **View in album (auto archived)** to **View in album**.
- Album tab provides task list and detail view with full metadata snapshot:
  - prompt/negative/mode/workflowId
  - nodeInfoList request mapping
  - taskId, output stats, decode outcome
  - local media preview (image/video)

