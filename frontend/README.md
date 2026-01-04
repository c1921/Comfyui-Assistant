# ComfyUI Assistant 前端

Vue 3 + Vite 前端界面，用于通过后端代理运行 ComfyUI 工作流。

## 环境要求

- Node.js 18+（Vite 7）

## 安装

```bash
cd frontend
npm install
```

## 启动

```bash
npm run dev
```

Vite 默认地址为 `http://localhost:5173`。

## 功能概览

- 粘贴或上传 workflow JSON 并解析可编辑字段
- 编辑节点输入（string/number/boolean）并可选随机 seed
- 连接 ComfyUI WebSocket 获取进度
- 运行提示词并展示输出图片
- 浏览后端配置的本地相册目录

## 后端连接

前端通过后端提供的接口进行访问：

- HTTP 基址：`http://<backend-host>:<backend-port>/api`
- WebSocket：`ws://<backend-host>:<backend-port>/ws`

页面加载时会调用 `GET /api/config` 预填 `pcIp` 与 `pcPort`。你可以通过
`PUT /api/config` 或直接编辑 `backend/config.json` 更新配置。

若 `pcIp` 为空，前端会使用当前页面的 origin 作为后端地址。

## 构建

```bash
npm run build
```

## 预览

```bash
npm run preview
```
