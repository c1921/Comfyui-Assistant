import os
import subprocess
import sys
import shutil
from pathlib import Path


ROOT = Path(__file__).resolve().parent
BACKEND_DIR = ROOT / "backend"
FRONTEND_DIR = ROOT / "frontend"


def venv_python() -> Path:
    if os.name == "nt":
        return BACKEND_DIR / ".venv" / "Scripts" / "python.exe"
    return BACKEND_DIR / ".venv" / "bin" / "python"


def ensure_backend_venv() -> Path:
    python_path = venv_python()
    if python_path.exists():
        return python_path
    print("Creating backend venv...")
    subprocess.run([sys.executable, "-m", "venv", str(BACKEND_DIR / ".venv")], check=True, cwd=BACKEND_DIR)
    subprocess.run([str(python_path), "-m", "pip", "install", "-e", "."], check=True, cwd=BACKEND_DIR)
    return python_path


def ensure_frontend_deps() -> None:
    npm = npm_cmd()
    if (FRONTEND_DIR / "node_modules").exists():
        return
    print("Installing frontend deps...")
    subprocess.run(npm_args("install"), check=True, cwd=FRONTEND_DIR)


def npm_cmd() -> str:
    npm = shutil.which("npm")
    if npm:
        return npm
    if os.name == "nt":
        npm = shutil.which("npm.cmd") or shutil.which("npm.exe")
        if npm:
            return npm
    raise FileNotFoundError("npm not found in PATH. Please install Node.js and restart the terminal.")


def npm_args(*args: str) -> list[str]:
    npm = npm_cmd()
    if os.name == "nt" and npm.lower().endswith((".cmd", ".bat")):
        return ["cmd", "/c", npm, *args]
    return [npm, *args]


def popen(cmd, cwd: Path):
    kwargs = {"cwd": cwd}
    if os.name == "nt":
        kwargs["creationflags"] = subprocess.CREATE_NEW_CONSOLE
    return subprocess.Popen(cmd, **kwargs)


def main() -> int:
    print("Starting backend...")
    python_path = ensure_backend_venv()
    backend_proc = popen(
        [
            str(python_path),
            "-m",
            "uvicorn",
            "comfyui_backend.main:app",
            "--host",
            "0.0.0.0",
            "--port",
            "8000",
        ],
        BACKEND_DIR,
    )

    print("Starting frontend...")
    ensure_frontend_deps()
    frontend_proc = popen(npm_args("run", "dev"), FRONTEND_DIR)

    print("Backend and frontend started. Press Ctrl+C to stop.")
    try:
        backend_proc.wait()
        frontend_proc.wait()
    except KeyboardInterrupt:
        print("Stopping...")
        backend_proc.terminate()
        frontend_proc.terminate()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
