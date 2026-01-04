# ComfyUI Assistant 后端

FastAPI 代理服务，负责转发 ComfyUI 的 HTTP/WS 接口，并提供前端所需的配置与相册 API。

## 环境要求

- Python 3.9+

## 安装

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -e .
```

## 启动

```bash
uvicorn comfyui_backend.main:app --host 0.0.0.0 --port 8000
```

## 配置

后端读取两类配置：

1) 环境变量：ComfyUI 代理相关设置  
2) `backend/config.json`：前端默认值与相册路径

`backend/config.json` 若不存在会在启动时自动创建。可复制
`backend/config.example.json` 作为起始配置。

示例：

```json
{
  "pcIp": "127.0.0.1",
  "pcPort": "8000",
  "albumPath": "D:\\Path\\To\\ComfyUI\\output"
}
```

环境变量：

- `COMFY_HTTP_BASE`（默认 `http://127.0.0.1:8188`）
- `COMFY_WS_BASE`（默认 `ws://127.0.0.1:8188`）
- `ALLOWED_ORIGINS`（默认 `*`，逗号分隔）
- `HTTP_TIMEOUT_SECONDS`（默认 `300`）

## API

健康检查：

- `GET /health` -> `{ "status": "ok" }`

配置：

- `GET /api/config` -> 返回当前 `pcIp`, `pcPort`, `albumPath`
- `PUT /api/config` -> 持久化到 `backend/config.json`

相册：

- `GET /api/album/list?order=asc|desc` -> 列出 `albumPath` 下的图片
- `GET /api/album/file/{filename}` -> 返回单张图片

代理：

- `GET|POST|PUT|DELETE|OPTIONS /api/{path}` -> 代理到 ComfyUI HTTP API
- `GET|POST|PUT|DELETE|OPTIONS /{path}` -> 代理到 ComfyUI 根路径
- `WS /ws?clientId=...` -> 代理到 ComfyUI WebSocket

## 说明

- 相册仅允许以下后缀：
  `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.bmp`, `.tiff`, `.tif`
- `albumPath` 必须是有效目录，通常指向 ComfyUI 输出目录。
- 若上传的是 ComfyUI 编辑器导出的 workflow.json，后端会自动转换为 API workflow。
- 转换逻辑位于 `backend/src/comfyui_backend/utils/workflow_convert.py`，会优先读取节点的 widget 定义，
  若缺失则使用内置的节点映射（例如 `KSampler`、`CLIPLoader`、`SaveImage` 等）从 `widgets_values` 填充必填输入。
- 转换时会忽略不支持的节点（如 `MarkdownNote`），避免 ComfyUI 执行报错。
- 若仍出现必填输入缺失，请优先使用 ComfyUI 导出的 API workflow，或扩展转换映射表。
