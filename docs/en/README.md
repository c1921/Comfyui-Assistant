# ComfyUI Assistant

[中文](../../README.md) | English

ComfyUI Assistant is a lightweight UI plus a FastAPI proxy that lets you run
ComfyUI workflows, edit common parameters, track progress, and view outputs.

## Architecture

- `backend/` FastAPI proxy and config/album API
- `frontend/` Vue 3 + Vite UI
- ComfyUI itself runs separately (default `http://127.0.0.1:8188`)

## Quick start

1) Start ComfyUI (default port 8188).
2) Start the backend:

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -e .
uvicorn comfyui_backend.main:app --host 0.0.0.0 --port 8000
```

3) Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` in your browser.

## Configuration

Backend config is stored in `backend/config.json`. If the file does not exist
it is created from `backend/config.example.json`.

Key fields:

- `pcIp`: backend host used by the UI (empty means current origin)
- `pcPort`: backend port (default `8000`)
- `albumPath`: directory that stores ComfyUI output images

Backend environment variables:

- `COMFY_HTTP_BASE` (default `http://127.0.0.1:8188`)
- `COMFY_WS_BASE` (default `ws://127.0.0.1:8188`)
- `ALLOWED_ORIGINS` (default `*`, comma-separated)
- `HTTP_TIMEOUT_SECONDS` (default `300`)

## Ports

- Backend: `8000`
- ComfyUI: `8188`
- Frontend dev server: `5173`

## Useful endpoints

- `GET /health`
- `GET /api/config`
- `PUT /api/config`
- `GET /api/album/list`
- `GET /api/album/file/{filename}`
- `WS /ws`

## Notes

- The backend proxies all other `/api/*` and `/*` routes to ComfyUI.
- Ensure `albumPath` points to a real folder with image outputs.
