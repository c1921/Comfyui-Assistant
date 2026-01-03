from dataclasses import dataclass
import json
import os
from pathlib import Path
from typing import List


def _split_origins(value: str) -> List[str]:
    if not value:
        return ["*"]
    parts = [item.strip() for item in value.split(",")]
    return [item for item in parts if item]


@dataclass(frozen=True)
class Settings:
    comfy_http_base: str
    comfy_ws_base: str
    allowed_origins: List[str]
    http_timeout_seconds: int


@dataclass(frozen=True)
class UiConfig:
    pcIp: str
    pcPort: str
    albumPath: str


CONFIG_PATH = Path(__file__).resolve().parents[2] / "config.json"
CONFIG_TEMPLATE_PATH = Path(__file__).resolve().parents[2] / "config.example.json"


def _ensure_ui_config_file() -> None:
    if CONFIG_PATH.exists():
        return
    try:
        if CONFIG_TEMPLATE_PATH.exists():
            CONFIG_PATH.write_text(CONFIG_TEMPLATE_PATH.read_text(encoding="utf-8"), encoding="utf-8")
        else:
            CONFIG_PATH.write_text(
                json.dumps({"pcIp": "", "pcPort": "8000", "albumPath": ""}, indent=2),
                encoding="utf-8",
            )
    except Exception:
        return


def get_settings() -> Settings:
    return Settings(
        comfy_http_base=os.getenv("COMFY_HTTP_BASE", "http://127.0.0.1:8188"),
        comfy_ws_base=os.getenv("COMFY_WS_BASE", "ws://127.0.0.1:8188"),
        allowed_origins=_split_origins(os.getenv("ALLOWED_ORIGINS", "*")),
        http_timeout_seconds=int(os.getenv("HTTP_TIMEOUT_SECONDS", "300")),
    )


def load_ui_config() -> UiConfig:
    _ensure_ui_config_file()
    if not CONFIG_PATH.exists():
        return UiConfig(pcIp="", pcPort="8000")
    try:
        data = CONFIG_PATH.read_text(encoding="utf-8")
        parsed = json.loads(data)
    except Exception:
        return UiConfig(pcIp="", pcPort="8000")
    pc_ip = parsed.get("pcIp")
    pc_port = parsed.get("pcPort")
    album_path = parsed.get("albumPath")
    return UiConfig(
        pcIp=pc_ip if isinstance(pc_ip, str) else "",
        pcPort=pc_port if isinstance(pc_port, str) else "8000",
        albumPath=album_path if isinstance(album_path, str) else "",
    )


def save_ui_config(config: UiConfig) -> UiConfig:
    payload = {"pcIp": config.pcIp, "pcPort": config.pcPort, "albumPath": config.albumPath}
    CONFIG_PATH.write_text(json.dumps(payload, ensure_ascii=True, indent=2), encoding="utf-8")
    return config
