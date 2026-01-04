@echo off
setlocal

set "ROOT=%~dp0"

echo Starting backend...
start "ComfyUI Assistant Backend" cmd /k "cd /d %ROOT%backend && if not exist .venv\Scripts\activate (echo Creating venv... && python -m venv .venv && call .venv\Scripts\activate && pip install -e .) else (call .venv\Scripts\activate) && python -m uvicorn comfyui_backend.main:app --host 0.0.0.0 --port 8000"

echo Starting frontend...
start "ComfyUI Assistant Frontend" cmd /k "cd /d %ROOT%frontend && if not exist node_modules (echo Installing frontend deps... && npm install) else (echo Frontend deps ok.) && npm run dev"

echo Done.
