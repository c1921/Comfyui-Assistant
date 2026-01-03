# ComfyUI Backend

FastAPI proxy service for ComfyUI HTTP/WS endpoints.

## Run

```bash
uvicorn comfyui_backend.main:app --host 0.0.0.0 --port 8000
```

## Environment

- `COMFY_HTTP_BASE` (default: `http://127.0.0.1:8188`)
- `COMFY_WS_BASE` (default: `ws://127.0.0.1:8188`)
- `ALLOWED_ORIGINS` (default: `*`) comma-separated
- `HTTP_TIMEOUT_SECONDS` (default: `300`)
