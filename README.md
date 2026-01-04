# ComfyUI Assistant

中文 | [English](docs/en/README.md)

ComfyUI Assistant 是一个轻量的 UI + FastAPI 代理，支持运行 ComfyUI 工作流、编辑常用参数、查看进度与输出图片。

## 架构

- `backend/` FastAPI 代理与配置/相册 API
- `frontend/` Vue 3 + Vite 前端界面
- ComfyUI 独立运行（默认 `http://127.0.0.1:8188`）

## 快速开始

1) 启动 ComfyUI（默认端口 8188）。
2) 启动后端：

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -e .
uvicorn comfyui_backend.main:app --host 0.0.0.0 --port 8000
```

3) 启动前端：

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。

## 配置

后端配置存储在 `backend/config.json`。如果文件不存在，会从
`backend/config.example.json` 自动生成。

关键字段：

- `pcIp`：前端使用的后端主机（为空则使用当前页面的 origin）
- `pcPort`：后端端口（默认 `8000`）
- `albumPath`：ComfyUI 输出图片所在目录

后端环境变量：

- `COMFY_HTTP_BASE`（默认 `http://127.0.0.1:8188`）
- `COMFY_WS_BASE`（默认 `ws://127.0.0.1:8188`）
- `ALLOWED_ORIGINS`（默认 `*`，逗号分隔）
- `HTTP_TIMEOUT_SECONDS`（默认 `300`）

## 端口

- 后端：`8000`
- ComfyUI：`8188`
- 前端开发服务：`5173`

## 常用接口

- `GET /health`
- `GET /api/config`
- `PUT /api/config`
- `GET /api/album/list`
- `GET /api/album/file/{filename}`
- `WS /ws`

## 说明

- 后端会把其他 `/api/*` 与 `/*` 路由代理到 ComfyUI。
- 后端会在提交 `/api/prompt` 时自动将编辑器 workflow 转换为 API workflow，并忽略不支持的节点（如 `MarkdownNote`）。
- 请确保 `albumPath` 指向真实存在的输出目录。
