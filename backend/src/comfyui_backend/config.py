from dataclasses import dataclass
import os
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


def get_settings() -> Settings:
    return Settings(
        comfy_http_base=os.getenv("COMFY_HTTP_BASE", "http://127.0.0.1:8188"),
        comfy_ws_base=os.getenv("COMFY_WS_BASE", "ws://127.0.0.1:8188"),
        allowed_origins=_split_origins(os.getenv("ALLOWED_ORIGINS", "*")),
        http_timeout_seconds=int(os.getenv("HTTP_TIMEOUT_SECONDS", "300")),
    )
